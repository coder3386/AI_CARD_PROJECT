package AIcard.cardapp.service;

import AIcard.cardapp.DTO.AiCardResponse;
import AIcard.cardapp.DTO.CardCreateRequest;
import AIcard.cardapp.DTO.CardExtraItemRequest;
import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.entity.Template;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiCardService {

    private static final List<String> REQUIRED_IDS = List.of(
            "cardRoot",
            "nameText",
            "jobText",
            "introText",
            "emailText",
            "phoneText",
            "profileImage",
            "portfolioArea",
            "linkArea"
    );

    private final ObjectMapper objectMapper;
    private final RestClient openAiRestClient;
    private final RestClient geminiRestClient;

    @Value("${openai.api.key:}")
    private String configuredApiKey;

    @Value("${openai.model:gpt-5.4-mini}")
    private String model;

    @Value("${gemini.api.key:}")
    private String configuredGeminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    public OpenAiCardService() {
        this.objectMapper = new ObjectMapper();
        this.openAiRestClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .build();
        this.geminiRestClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public AiCardResponse generateCardDraft(BusinessCard card, List<Template> templates, CardCreateRequest request) {
        return generateCardDraft(card, templates, request, request.getApiKey(), request.getGeminiApiKey());
    }

    public AiCardResponse generateCardDraft(
            BusinessCard card,
            List<Template> templates,
            CardCreateRequest request,
            String userProvidedApiKey
    ) {
        return generateCardDraft(card, templates, request, userProvidedApiKey, null);
    }

    public AiCardResponse generateCardDraft(
            BusinessCard card,
            List<Template> templates,
            CardCreateRequest request,
            String userProvidedApiKey,
            String userProvidedGeminiApiKey
    ) {
        String prompt = buildPrompt(card, templates, request);
        return callCardModel(card, prompt, userProvidedApiKey, userProvidedGeminiApiKey);
    }

    public AiCardResponse generateDrawingCardDraft(BusinessCard card, CardCreateRequest request) {
        String prompt = buildDrawingPrompt(card, request);
        return callCardModel(card, prompt, request.getApiKey(), request.getGeminiApiKey());
    }

    public AiCardResponse fixLayoutOnly(
            BusinessCard card,
            String currentHtml,
            String currentCss,
            String userProvidedApiKey,
            String userProvidedGeminiApiKey
    ) {
        String prompt = buildLayoutFixPrompt(card, currentHtml, currentCss);
        return callCardModel(card, prompt, userProvidedApiKey, userProvidedGeminiApiKey);
    }

    private AiCardResponse callCardModel(
            BusinessCard card,
            String prompt,
            String userProvidedApiKey,
            String userProvidedGeminiApiKey
    ) {
        String userOpenAiApiKey = trimToNull(userProvidedApiKey);
        String userGeminiApiKey = trimToNull(userProvidedGeminiApiKey);
        String openAiApiKey = userOpenAiApiKey != null
                ? userOpenAiApiKey
                : userGeminiApiKey == null ? resolveConfiguredApiKeyOrNull(configuredApiKey, "${OPENAI_API_KEY}") : null;
        String geminiApiKey = userGeminiApiKey != null
                ? userGeminiApiKey
                : resolveConfiguredApiKeyOrNull(configuredGeminiApiKey, "${GEMINI_API_KEY}");

        if (openAiApiKey == null && geminiApiKey != null) {
            try {
                return callGemini(card, prompt, geminiApiKey);
            } catch (Exception ex) {
                return fallback(card, prompt, "Gemini API 호출 실패로 기본 명함을 생성했습니다.");
            }
        }

        if (openAiApiKey == null) {
            throw new IllegalStateException("GPT API Key 또는 Gemini API Key 중 하나는 입력해야 합니다.");
        }

        String apiKeyToUse = openAiApiKey;

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a safe HTML/CSS digital business card designer. Return only one JSON object."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String apiResult = openAiRestClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKeyToUse)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseResponse(apiResult, prompt, card);
        } catch (Exception ex) {
            if (geminiApiKey != null) {
                try {
                    return callGemini(card, prompt, geminiApiKey);
                } catch (Exception ignored) {
                    return fallback(card, prompt, "GPT와 Gemini API 호출 실패로 기본 명함을 생성했습니다.");
                }
            }
            return fallback(card, prompt, "GPT API 호출 실패로 기본 명함을 생성했습니다.");
        }
    }

    private String resolveConfiguredApiKeyOrNull(String configuredKey, String unresolvedPlaceholder) {
        if (configuredKey != null && !configuredKey.isBlank() && !configuredKey.contains(unresolvedPlaceholder)) {
            return configuredKey.trim();
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AiCardResponse callGemini(BusinessCard card, String prompt, String geminiApiKey) throws Exception {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", "You are a safe HTML/CSS digital business card designer. Return only one JSON object."))
                ),
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        String apiResult = geminiRestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", geminiApiKey)
                        .build(geminiModel))
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(apiResult);
        String content = root.at("/candidates/0/content/parts/0/text").asText();
        if (content == null || content.isBlank()) {
            return fallback(card, prompt, "Gemini 응답 파싱 실패로 기본 명함을 생성했습니다.");
        }

        return parseGeminiResponse(content, apiResult, prompt, card);
    }

    private AiCardResponse parseResponse(String apiResult, String prompt, BusinessCard card) throws Exception {
        JsonNode root = objectMapper.readTree(apiResult);
        String content = root.at("/choices/0/message/content").asText();
        if (content == null || content.isBlank()) {
            return fallback(card, prompt, "GPT 응답 파싱 실패로 기본 명함을 생성했습니다.");
        }

        JsonNode cardJson = objectMapper.readTree(stripJsonFence(content));
        String html = text(cardJson, "html");
        String css = text(cardJson, "css");
        String reason = text(cardJson, "reason");
        JsonNode layoutNode = cardJson.get("layoutJson");
        String layoutJson = layoutNode == null || layoutNode.isNull()
                ? defaultLayoutJson()
                : objectMapper.writeValueAsString(layoutNode);

        if (!isSafeAndComplete(html, css)) {
            return fallback(card, prompt, "GPT 응답에 필수 ID가 누락되어 기본 명함을 생성했습니다.");
        }

        AiCardResponse response = new AiCardResponse();
        response.setReason(reason);
        response.setHtml(html);
        response.setCss(css);
        response.setLayoutJson(layoutJson);
        response.setRawJson(apiResult);
        response.setPrompt(prompt);
        response.setModelName(model);
        response.setFallback(false);
        return response;
    }

    private AiCardResponse parseGeminiResponse(String content, String apiResult, String prompt, BusinessCard card) throws Exception {
        JsonNode cardJson = objectMapper.readTree(stripJsonFence(content));
        String html = text(cardJson, "html");
        String css = text(cardJson, "css");
        String reason = text(cardJson, "reason");
        JsonNode layoutNode = cardJson.get("layoutJson");
        String layoutJson = layoutNode == null || layoutNode.isNull()
                ? defaultLayoutJson()
                : objectMapper.writeValueAsString(layoutNode);

        if (!isSafeAndComplete(html, css)) {
            return fallback(card, prompt, "Gemini 응답에 필수 ID가 누락되어 기본 명함을 생성했습니다.");
        }

        AiCardResponse response = new AiCardResponse();
        response.setReason(reason);
        response.setHtml(html);
        response.setCss(css);
        response.setLayoutJson(layoutJson);
        response.setRawJson(apiResult);
        response.setPrompt(prompt);
        response.setModelName(geminiModel);
        response.setFallback(false);
        return response;
    }

    private String buildPrompt(BusinessCard card, List<Template> templates, CardCreateRequest request) {
        StringBuilder templateInfo = new StringBuilder();
        for (Template template : templates) {
            templateInfo.append("- ")
                    .append(template.getTemplateCode())
                    .append(" / ")
                    .append(template.getTemplateName())
                    .append(" / mood=")
                    .append(template.getMoodTags())
                    .append(" / color=")
                    .append(template.getColorTags())
                    .append('\n');
        }
        String templateCodes = String.join(", ", templates.stream()
                .map(Template::getTemplateCode)
                .toList());

        return """
                Create a 860px x 480px digital business card draft using HTML and CSS.

                User information:
                - Name: %s
                - Job title: %s
                - Company: %s
                - Department or major: %s
                - Intro: %s
                - Email: %s
                - Phone: %s
                - Drawing description: %s
                - Desired mood: %s
                - Preferred color: %s
                - Drawing layout JSON:
                %s

                Extra portfolio, skill, certificate, and link items:
                %s

                Available templates:
                %s

                Template selection rules:
                - Choose exactly one template_code from Available templates.
                - Allowed template_code values: %s
                - Put the chosen template_code into layoutJson.templateCode exactly.
                - Choose modern_dark for dark, tech, professional, developer, backend, cyber, navy, neon moods.
                - Choose simple_white for clean, student, academic, minimal, bright, formal, resume-like moods.
                - Choose portfolio_grid for creative, portfolio, designer, project, skill-heavy, visual-grid moods.
                - Templates are style references, not rigid layout locks. Keep the selected template's mood, palette, and visual language, but freely adjust placement when the user's text or extra items need more room.
                - For all templates (modern_dark, simple_white, portfolio_grid), choose the final coordinates by content fit. Never reuse a template's original box coordinates if they make introText, contact, or portfolioArea collide.

                Return only this JSON format:
                {
                  "reason": "recommendation reason",
                  "html": "<div id='cardRoot'>...</div>",
                  "css": "#cardRoot { ... }",
                  "layoutJson": {
                    "templateCode": "modern_dark",
                    "backgroundColor": "#0F172A",
                    "pointColor": "#38BDF8",
                    "elements": [
                      {
                        "id": "nameText",
                        "type": "text",
                        "x": 250,
                        "y": 70,
                        "fontSize": 36,
                        "color": "#FFFFFF"
                      }
                    ]
                  }
                }

                HTML rules:
                - Do not create html, head, or body tags.
                - Do not use script tags, iframe tags, or external CDN.
                - Class names must start with card-.
                - Add editable class to major editable text elements.
                - Required IDs: cardRoot, nameText, jobText, introText, emailText, phoneText, profileImage, portfolioArea, linkArea.
                - layoutJson.templateCode must be one of the allowed template_code values, never ai_generated.
                - Do not create headlineText.
                - If possible, include companyText and departmentText IDs too.
                - Use the exact Name value for nameText. Do not add words such as "card", "business card", or "명함" after the name.
                - Use only the user's provided job title, company, department, intro, email, and phone values. Do not invent missing personal details.
                - nameText, jobText, introText, emailText, phoneText, companyText, and departmentText must be simple leaf elements containing only text. Do not put child div, span, strong, p, or label elements inside these IDs.
                - Do not render the same email, phone, name, company, department, job title, or intro anywhere outside its required ID element.
                - Do not place raw email or phone text at card corners or edges as decorative text.
                - If you want labels like EMAIL or PHONE, use a parent contact group or CSS styling, but keep emailText and phoneText themselves as leaf text-only elements.
                - Drawing description is only design guidance. Do not copy it into introText unless the same content was also provided as Intro.
                - Always reserve a future extra-info area for portfolioArea in a clean open zone, even when Extra items is "- none".
                - The server will render portfolioArea inside the position you choose, with about 360px x 178px of usable space when items exist. Choose a position that has a full empty rectangle around it.
                - Do not place nameText, jobText, introText, emailText, phoneText, companyText, departmentText, profileImage, or decorations over the reserved portfolioArea space.
                - portfolioArea must be a single empty placeholder container. The server will inject the Portfolio / Skills / Links title and all chips/buttons later.
                - Do not put "PORTFOLIO", "Portfolio / Skills / Links", skill names, certificate names, portfolio titles, URLs, or chips inside the generated HTML.
                - Do not use CSS ::before or ::after on portfolioArea or linkArea to render titles, item names, URLs, or labels.
                - Do not create a second portfolio, skills, links, certificate, tag, chip, or badge panel outside portfolioArea.
                - If Extra items is "- none", keep only an empty portfolioArea placeholder with no inner title or items.
                - Do not render skill, certificate, portfolio, or link item titles anywhere outside the element with id portfolioArea.
                - Do not create standalone skill/tag/chip/badge elements outside portfolioArea.
                - linkArea must stay empty unless drawingLayout explicitly places it. The server will render LINK items together inside portfolioArea.
                - Do not place portfolioArea or linkArea at the top-left corner unless drawingLayout explicitly places them there.
                - portfolioArea can be lower-right, lower-middle, right column, or bottom band depending on which location avoids overlap best.
                - Give portfolioArea about 360px x 178px of usable space for up to 8 compact items. If the template is too narrow, use a bottom band or a clear side column.
                - Do not invent placeholder links such as GitHub, Blog, Portfolio, or SNS.
                - Keep every visual element inside cardRoot. Do not let text overflow outside the 860px x 480px card.
                - Before returning, do a layout safety check: no visible text may overlap another element, no text may be clipped, and no important element may leave cardRoot.
                - Section boxes must never overlap or touch each other. introText box, contact box, profileImage, name/job group, portfolioArea, and decorative panels need at least 18px of clear spacing between their visible borders.
                - Treat portfolioArea as a real occupied panel even though it is empty in your HTML. The server will inject a title and up to 8 buttons later, so reserve the full 360px x 178px rectangle plus clear spacing.
                - Never place introText, emailText, phoneText, companyText, departmentText, nameText, jobText, profileImage, or decorative panels behind, under, or partly inside the future portfolioArea rectangle.
                - If there is not enough room for a side portfolioArea, move portfolioArea to a bottom band and move contact/intro above it, or use a clear two-column layout.
                - Do not return a layout where two absolute-positioned boxes have intersecting x/y/width/height ranges. If two boxes intersect, change x, y, width, height, or font-size until they do not.
                - If portfolioArea is near the lower-right, introText and contact details must end before portfolioArea begins, or move to the left/upper area. Do not let introText run underneath portfolioArea.
                - Do not solve crowding by simply drawing semi-transparent boxes on top of each other. Reflow the layout into separate zones instead.
                - Treat every rounded rectangle or visible panel as a real box for collision checks. The visible borders of introText panels, contact chips, profileImage, and portfolioArea must have clear gaps.
                - If intro, email, phone, or extra items are long, reduce font size, widen the text box, wrap text, or move the section. A clean readable layout is more important than copying the template's original coordinates.
                - Keep introText in a bounded readable block. If the intro is long, use a smaller font and line-height, and place it away from portfolioArea and contact details.
                - Contact details must remain readable and must not sit under portfolioArea. Move them to the left, upper-right, or a separate compact row if the lower-right is occupied.
                - Avoid large empty decorative boxes when content is crowded. Use decoration only if it does not reduce readable space.
                - Use CSS box-sizing: border-box and overflow-wrap: anywhere on long text containers where needed.
                - Treat drawingLayoutJson as a rough wireframe and user intention, not as exact final coordinates.
                - Preserve the user's intended regions and relative grouping, but refine x, y, width, and height for a clean professional layout.
                - If the user's drawing is scattered, cramped, overlapping, or visually messy, snap elements to clean alignment, consistent spacing, and a balanced grid.
                - Do not draw visible outline boxes for every wireframe shape. Convert rough rectangles into polished sections only when they improve the design.
                - Group related information naturally: name/job/company/department together, contact details together, intro as one readable area, and extra items inside portfolioArea.
                - Use safe margins of about 36px to 56px and keep consistent gaps between sections.
                - If the user drew only some required elements, automatically place missing required elements in the remaining space.
                - Even if drawingLayoutJson is empty, generate a complete card using the text description and default layout.
                """.formatted(
                safe(card.getDisplayName()),
                safe(card.getJobTitle()),
                safe(card.getCompany()),
                safe(card.getDepartment()),
                safe(card.getIntro()),
                safe(card.getEmail()),
                safe(card.getPhone()),
                safe(request.getDrawingDescription()),
                safe(request.getMood()),
                safe(request.getPreferredColor()),
                safeLayout(request.getDrawingLayoutJson()),
                buildExtraInfo(request.getExtraItems()),
                templateInfo,
                templateCodes
        );
    }

    private String buildDrawingPrompt(BusinessCard card, CardCreateRequest request) {
        return """
                Create a custom 860px x 480px digital business card draft using HTML and CSS.

                This is drawing-based card creation.
                Do not use or imitate the existing saved templates such as modern_dark, simple_white, or portfolio_grid.
                The user's drawingLayoutJson is the main source of layout intent.
                Use the drawing as a rough wireframe, then refine it into a clean professional business card.

                User information:
                - Name: %s
                - Job title: %s
                - Company: %s
                - Department or major: %s
                - Intro: %s
                - Email: %s
                - Phone: %s
                - Drawing description: %s
                - Drawing layout JSON:
                %s

                Extra portfolio, skill, certificate, and link items:
                %s

                Core drawing rules:
                - Do not select an existing template.
                - Set layoutJson.templateCode exactly to "drawing_custom".
                - Treat drawingLayoutJson as the user's intended placement and grouping.
                - Preserve the relative intent of the sketch, but do not copy messy or cramped coordinates blindly.
                - If the sketch is too close to an edge, overlapping, too small, or visually rough, snap it into clean alignment.
                - Use the user's sketch to decide where profileImage, nameText, jobText, introText, emailText, phoneText, portfolioArea, and linkArea should roughly go.
                - If the user did not draw a required element, place it naturally in the remaining space.
                - The final card must look polished, not like raw wireframe boxes.
                - Use the drawing description only as design guidance. Do not copy it into introText unless the same content was provided as Intro.

                Return only this JSON format:
                {
                  "reason": "custom drawing layout reason",
                  "html": "<div id='cardRoot'>...</div>",
                  "css": "#cardRoot { ... }",
                  "layoutJson": {
                    "templateCode": "drawing_custom",
                    "backgroundColor": "#0F172A",
                    "pointColor": "#38BDF8",
                    "elements": [
                      {
                        "id": "nameText",
                        "type": "text",
                        "x": 250,
                        "y": 70,
                        "fontSize": 36,
                        "color": "#FFFFFF"
                      }
                    ]
                  }
                }

                HTML rules:
                - Do not create html, head, or body tags.
                - Do not use script tags, iframe tags, or external CDN.
                - Class names must start with card-.
                - Add editable class to major editable text elements.
                - Required IDs: cardRoot, nameText, jobText, introText, emailText, phoneText, profileImage, portfolioArea, linkArea.
                - If possible, include companyText and departmentText IDs too.
                - Do not create headlineText.
                - Use the exact Name value for nameText. Do not add words such as "card", "business card", or "명함" after the name.
                - Use only the user's provided job title, company, department, intro, email, and phone values. Do not invent missing personal details.

                Portfolio/extra area rules:
                - portfolioArea must be a single empty placeholder container. The server will inject the Portfolio / Skills / Links title and all chips/buttons later.
                - Do not put "PORTFOLIO", "Portfolio / Skills / Links", skill names, certificate names, portfolio titles, URLs, or chips inside the generated HTML.
                - Do not use CSS ::before or ::after on portfolioArea or linkArea to render titles, item names, URLs, or labels.
                - Do not create a second portfolio, skills, links, certificate, tag, chip, or badge panel outside portfolioArea.
                - If Extra items is "- none", keep only an empty portfolioArea placeholder with no inner title or items.
                - linkArea must stay empty. The server will render LINK items together inside portfolioArea.
                - Reserve about 360px x 178px of usable space for portfolioArea when extra items exist, and keep it inside cardRoot.

                Layout safety rules:
                - Keep every visual element inside cardRoot.
                - Use a clean grid-like composition with clear zones. Align left edges, top edges, or center lines where possible.
                - Section boxes must never overlap, touch, or visually crowd each other.
                - Keep at least 24px of visible gap between introText box, contact box, profileImage, name/job group, portfolioArea, and decorative panels.
                - Keep every section at least 28px away from the cardRoot outer edge. Never set top, left, right, or bottom close enough that text touches or clips at the edge.
                - Do not place emailText or phoneText at the extreme top-left, top-right, bottom-left, or bottom-right corner unless the drawing explicitly shows a full contact box there with enough padding.
                - Prefer grouping emailText and phoneText together in one contact zone. If separated, they must still align cleanly and have matching widths, padding, and style.
                - Contact boxes should be at least 210px wide for email and at least 170px wide for phone, unless the text is shorter and still fully visible.
                - If portfolioArea would collide with introText or contact details, move or resize the sections instead of stacking them.
                - Treat portfolioArea as a real occupied panel even if your HTML leaves it empty. The server will inject the Portfolio / Skills / Links title and up to 8 buttons later, so reserve the full 360px x 178px rectangle.
                - No other section may be behind, under, inside, or touching the future portfolioArea rectangle.
                - If the sketch puts too many regions in one column, reorganize into a clean two-column or top/bottom layout while preserving the user's rough intent.
                - Do not copy rough drawing boxes as literal visible boxes if that creates a cluttered layout. Convert them into polished aligned sections.
                - Keep introText in a bounded readable block. If the intro is long, reduce font size, wrap text, or expand the block.
                - Contact details must remain readable, fully inside their box, and must not sit under portfolioArea.
                - Do a final mental bounding-box check before answering: every visible text line must be fully inside cardRoot, every panel must fit in 860x480, and no section may cover another section.
                - Avoid large empty decorative boxes when content is crowded.
                - Use CSS box-sizing: border-box and overflow-wrap: anywhere on long text containers where needed.
                - Use safe margins of about 36px to 56px and consistent gaps between sections.
                """.formatted(
                safe(card.getDisplayName()),
                safe(card.getJobTitle()),
                safe(card.getCompany()),
                safe(card.getDepartment()),
                safe(card.getIntro()),
                safe(card.getEmail()),
                safe(card.getPhone()),
                safe(request.getDrawingDescription()),
                safeLayout(request.getDrawingLayoutJson()),
                buildExtraInfo(request.getExtraItems())
        );
    }

    private String buildLayoutFixPrompt(BusinessCard card, String currentHtml, String currentCss) {
        return """
                Fix only layout problems in this existing 860px x 480px digital business card.

                Goal:
                - Preserve the current visual mood, colors, typography feeling, background, and overall design direction.
                - Preserve all user text exactly.
                - Do not add new personal details.
                - Do not redesign the card from scratch.
                - Only solve overlap, clipping, overflow, unreadable spacing, or elements escaping cardRoot.
                - You must actively change layout coordinates and sizes when the current layout has overlap. Do not return the same top/left/width/height values if any section is touching or covering another section.

                Required content values:
                - Name: %s
                - Job title: %s
                - Company: %s
                - Department: %s
                - Intro: %s
                - Email: %s
                - Phone: %s

                Current HTML fragment:
                %s

                Current CSS:
                %s

                Return only this JSON format:
                {
                  "reason": "what layout issue was fixed",
                  "html": "<div id='cardRoot'>...</div>",
                  "css": "#cardRoot { ... }",
                  "layoutJson": {
                    "templateCode": "layout_fixed",
                    "elements": []
                  }
                }

                HTML/CSS rules:
                - Do not create html, head, or body tags.
                - Do not use script tags, iframe tags, or external CDN.
                - Required IDs: cardRoot, nameText, jobText, introText, emailText, phoneText, profileImage, portfolioArea, linkArea.
                - Keep every visual element inside cardRoot.
                - Avoid overlapping text, contact, profileImage, and portfolioArea.
                - Keep portfolioArea in whichever open zone works best, such as lower-right, lower-middle, right column, or bottom band, with about 360px x 178px of usable space for up to 8 compact items without clipping.
                - Treat portfolioArea as a full occupied 360px x 178px panel even if the Current HTML shows it as empty. The server injects the title and item buttons after your answer.
                - The future injected portfolioArea panel must have at least 24px clear gap from introText, contact details, profileImage, name/job group, and any decorative panels.
                - If portfolioArea intersects introText or contact details in the current layout, move portfolioArea, introText, or contact details to a different zone. Do not leave them on the same x/y area.
                - portfolioArea must remain a single empty placeholder container. Do not include the Portfolio / Skills / Links title, chips, URLs, skill names, certificate names, or portfolio titles in the generated HTML.
                - Do not use CSS ::before or ::after on portfolioArea or linkArea to render titles, item names, URLs, or labels.
                - Do not create duplicate portfolio, skills, links, certificate, tag, chip, or badge panels outside portfolioArea.
                - Keep the current colors and mood, but change coordinates, widths, heights, font sizes, and spacing as much as needed to remove overlap.
                - Use flexible wrapping, smaller font sizes, or adjusted spacing when text is long.
                - Keep introText, contact details, and portfolioArea in separate non-overlapping zones.
                - Section boxes must not overlap or touch. Keep at least 18px of visible gap between introText box, contact box, profileImage, name/job group, portfolioArea, and decorative panels.
                - If introText and portfolioArea collide, shrink or move introText first, or move contact details, while preserving the card's colors and style.
                - If the original template coordinates are the cause of overlap, ignore those coordinates and reflow the card into cleaner zones while preserving the same palette and visual mood.
                - Recommended repair layouts:
                  1. Left identity/profile column, right intro/contact column, portfolioArea as a lower-right block with no overlap.
                  2. Top identity/contact row, middle intro block, bottom portfolioArea band.
                  3. Left intro/contact column, right identity/profile column, portfolioArea below the right column.
                - Avoid putting large introText and portfolioArea on the same horizontal row unless their rectangles have at least 24px gap.
                - Put long introText in a smaller bounded block with max-width and line-height, not behind portfolioArea.
                - Use CSS box-sizing: border-box, overflow-wrap: anywhere, and reasonable line-height on text boxes.
                - Final self-check before returning: imagine rectangles around profileImage, name/job group, introText, emailText, phoneText, contact group, and portfolioArea. None of those rectangles may intersect or touch.
                - Avoid large decorative boxes if they cause crowding.
                - Do not remove editable classes from major editable text elements.
                """.formatted(
                safe(card.getDisplayName()),
                safe(card.getJobTitle()),
                safe(card.getCompany()),
                safe(card.getDepartment()),
                safe(card.getIntro()),
                safe(card.getEmail()),
                safe(card.getPhone()),
                safe(currentHtml),
                safe(currentCss)
        );
    }

    private String safeLayout(String drawingLayoutJson) {
        if (drawingLayoutJson == null || drawingLayoutJson.isBlank()) {
            return "- none";
        }
        return drawingLayoutJson;
    }

    private String buildExtraInfo(List<CardExtraItemRequest> extraItems) {
        if (extraItems == null || extraItems.isEmpty()) {
            return "- none";
        }

        StringBuilder builder = new StringBuilder();
        for (CardExtraItemRequest item : extraItems) {
            if (item == null) {
                continue;
            }
            String title = safe(item.getTitle());
            String url = safe(item.getUrl());
            if (title.isBlank() && url.isBlank()) {
                continue;
            }
            builder.append("- type=")
                    .append(safe(item.getItemType()))
                    .append(", title=")
                    .append(title)
                    .append(", url=")
                    .append(url)
                    .append('\n');
        }
        return builder.isEmpty() ? "- none" : builder.toString();
    }

    private AiCardResponse fallback(BusinessCard card, String prompt, String reason) {
        String html = """
                <div id="cardRoot" class="card-root">
                  <div id="profileImage" class="card-profile" aria-label="profile image"></div>
                  <div class="card-content">
                    <p id="companyText" class="card-company editable">%s</p>
                    <h1 id="nameText" class="card-name editable">%s</h1>
                    <p id="jobText" class="card-job editable">%s</p>
                    <p id="departmentText" class="card-department editable">%s</p>
                    <p id="introText" class="card-intro editable">%s</p>
                    <div id="portfolioArea" class="card-portfolio"></div>
                    <div id="linkArea" class="card-links"></div>
                    <div class="card-contact">
                      <span id="emailText" class="card-email editable">%s</span>
                      <span id="phoneText" class="card-phone editable">%s</span>
                    </div>
                  </div>
                </div>
                """.formatted(
                escape(card.getCompany()),
                escape(card.getDisplayName()),
                escape(card.getJobTitle()),
                escape(card.getDepartment()),
                escape(card.getIntro()),
                escape(card.getEmail()),
                escape(card.getPhone())
        );

        String css = """
                #cardRoot {
                  width: 860px;
                  height: 480px;
                  box-sizing: border-box;
                  position: relative;
                  display: flex;
                  gap: 34px;
                  padding: 54px;
                  overflow: hidden;
                  color: #f8fafc;
                  background: linear-gradient(135deg, #101827 0%, #1e293b 54%, #0f766e 100%);
                  border-radius: 22px;
                  font-family: Arial, sans-serif;
                }
                .card-profile {
                  flex: 0 0 148px;
                  width: 148px;
                  height: 148px;
                  border-radius: 50%;
                  background: radial-gradient(circle at 35% 30%, #ffffff, #67e8f9 36%, #0f172a 76%);
                  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
                }
                .card-content {
                  min-width: 0;
                  display: flex;
                  flex-direction: column;
                  justify-content: center;
                }
                .card-company, .card-department {
                  margin: 0 0 8px;
                  color: #a7f3d0;
                  font-size: 18px;
                }
                .card-name {
                  margin: 0;
                  font-size: 54px;
                  line-height: 1.08;
                  color: #ffffff;
                }
                .card-job {
                  margin: 12px 0 0;
                  font-size: 26px;
                  color: #bae6fd;
                }
                .card-intro {
                  max-width: 560px;
                  margin: 24px 0 26px;
                  font-size: 20px;
                  line-height: 1.55;
                  color: #e2e8f0;
                }
                .card-contact {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 14px 22px;
                  font-size: 18px;
                  color: #f8fafc;
                }
                .card-portfolio, .card-links {
                  min-height: 0;
                }
                """;

        AiCardResponse response = new AiCardResponse();
        response.setReason(reason);
        response.setHtml(html);
        response.setCss(css);
        response.setLayoutJson(defaultLayoutJson());
        response.setRawJson("""
                {"reason": "%s", "html": "fallback", "css": "fallback", "layoutJson": %s}
                """.formatted(reason.replace("\"", "'"), defaultLayoutJson()));
        response.setPrompt(prompt);
        response.setModelName("fallback");
        response.setFallback(true);
        return response;
    }

    private boolean isSafeAndComplete(String html, String css) {
        if (html == null || css == null || html.isBlank() || css.isBlank()) {
            return false;
        }
        String lower = html.toLowerCase();
        if (lower.contains("<script") || lower.contains("<iframe") || lower.contains("<html")
                || lower.contains("<head") || lower.contains("<body")) {
            return false;
        }
        for (String id : REQUIRED_IDS) {
            if (!html.contains("id=\"" + id + "\"") && !html.contains("id='" + id + "'")) {
                return false;
            }
        }
        return true;
    }

    private String stripJsonFence(String content) {
        String cleaned = content.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String defaultLayoutJson() {
        return """
                {
                  "templateCode": "ai_generated",
                  "backgroundColor": "#101827",
                  "pointColor": "#67E8F9",
                  "elements": [
                    {"id": "nameText", "type": "text", "x": 240, "y": 90, "fontSize": 54, "color": "#FFFFFF"},
                    {"id": "jobText", "type": "text", "x": 240, "y": 162, "fontSize": 26, "color": "#BAE6FD"},
                    {"id": "introText", "type": "text", "x": 240, "y": 220, "fontSize": 20, "color": "#E2E8F0"}
                  ]
                }
                """;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(safe(value));
    }
}