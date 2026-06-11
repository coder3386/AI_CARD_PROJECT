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

    private static final org.slf4j.Logger userLog = org.slf4j.LoggerFactory.getLogger("USER_LOGGER");

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

    //일반 텍스트 명함 생성 프롬프트:

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
                - The three template concepts must have clearly different geometry. Do not create the same layout with only different colors.

                Concept-specific layout rules:
                - If layoutJson.templateCode is modern_dark:
                  - Use an asymmetric tech layout with a strong dark navy or black background and cyan, mint, or blue accents.
                  - Put nameText, jobText, companyText, departmentText, and introText mainly in a left hero column.
                  - Put profileImage near the upper-right, contact details in a compact right-side panel or row, and portfolioArea in a separate lower-right or bottom-right panel.
                  - Use glassy panels, thin neon lines, or dashboard-like sections sparingly. Keep the name large and dominant.
                  - Keep the upper-left hero text zone clean. Do not place decorative circles, badges, blobs, gradients, avatars, or empty shapes over or near nameText, jobText, companyText, departmentText, or introText.
                  - If decorative shapes are used, they must sit behind the content, avoid the text bounding boxes completely, and never reduce text readability.
                  - Make portfolioArea fully visible inside cardRoot with at least 36px from the card edge. Its text must be light and readable on the dark background.
                - If layoutJson.templateCode is simple_white:
                  - Use a minimal resume-like layout with a mostly white or off-white background, dark text, subtle warm-gray or brown accents, and generous whitespace.
                  - Avoid dark full-card backgrounds, heavy gradients, large decorative blocks, and many filled panels.
                  - Use a clean two-column or editorial layout: nameText/jobText/introText on the left, companyText/departmentText/emailText/phoneText as neat rows on the right.
                  - Put portfolioArea as a quiet bottom band or subtle lower section with thin separators, not as a heavy colorful card.
                - If layoutJson.templateCode is portfolio_grid:
                  - Use a clear creative grid layout, such as 2x2 blocks or a strong left column plus stacked right blocks.
                  - Give portfolioArea the largest or most visually important block because this template is for projects, skills, and links.
                  - Put nameText/jobText in one bold identity block, introText or project summary in another block, and contact details in a separate compact block.
                  - Use stronger creative contrast, visible grid sections, and lively accent colors, but keep every block aligned and non-overlapping.

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
                - Return exactly one visible root card element: <div id='cardRoot'>...</div>.
                - Every required ID and optional companyText/departmentText must be inside cardRoot.
                - Do not close cardRoot until after all required elements, portfolioArea, and linkArea. Do not return stray closing tags or visible elements outside cardRoot.
                - Every non-void HTML element must have a valid closing tag such as </div>. Never output malformed text like /div>.
                - profileImage itself must have visible position, width, height, border-radius, and overflow styling. Do not rely only on a child wrapper for the profile frame.
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
                Layout safety rules:
                - Keep every visual element inside cardRoot.
                - STRONGLY PREFER using CSS Flexbox (display: flex) or CSS Grid to group elements naturally without overlapping. Avoid overusing position: absolute for every single element.
                - NEVER use fixed height (e.g., height: 160px) for text containers like introText, nameText, jobText, companyText, departmentText, emailText, phoneText, or contact boxes. Always use height: auto or min-height so the box can expand vertically if the text is long.
                - Use a clean grid-like composition with clear zones. Align left edges, top edges, or center lines where possible.
                - Section boxes must never overlap, touch, or visually crowd each other. If using position: absolute, calculate coordinates carefully to allow elements above them to expand without colliding.
                - Keep at least 24px of visible gap between introText box, contact box, profileImage, name/job group, portfolioArea, and decorative panels.
                - Keep every section at least 28px away from the cardRoot outer edge. Never set top, left, right, or bottom close enough that text touches or clips at the edge.
                - Do not place emailText or phoneText at the extreme top-left, top-right, bottom-left, or bottom-right corner unless the requested layout explicitly shows a full contact box there with enough padding.
                - Prefer grouping emailText and phoneText together in one contact zone. If separated, they must still align cleanly and have matching widths, padding, and style.
                - If you create a contact group wrapper, phoneText and emailText must be actual children inside that wrapper. Otherwise, give phoneText and emailText their own absolute/flex-grid positioning.
                - Contact boxes should be at least 210px wide for email and at least 170px wide for phone, unless the text is shorter and still fully visible.
                - If portfolioArea would collide with introText or contact details, move or resize the sections instead of stacking them.
                - Treat portfolioArea as a real occupied panel even if your HTML leaves it empty. The server will inject content later, so reserve EXACTLY the 360px x 178px rectangle. Do not make it arbitrarily larger (e.g., 800px wide) than the reserved size.
                - No other section may be behind, under, inside, or touching the future portfolioArea rectangle. Force portfolioArea to sit clear of long text blocks.
                - If the generated layout puts too many regions in one column, reorganize into a clean two-column or top/bottom layout while preserving the selected concept's rough intent.
                - Do not create too many visible boxes if that creates a cluttered layout. Convert them into polished aligned sections with consistent padding, radius, and spacing.
                - Keep introText in a bounded readable block. If the intro is long, allow the block to expand downward (height: auto), reduce font size, or wrap text naturally.
                - Contact details must remain readable, fully inside their box, and must not sit under portfolioArea.
                - Text inside any bounded region must use display: flex or equivalent centering when appropriate, with consistent padding and line-height. No text should sit awkwardly at a bottom-left corner.
                - Do a final mental bounding-box check before answering: every visible text line must be fully inside cardRoot, every panel must fit in 860x480, and no section may cover another section.
                - Avoid large empty decorative boxes when content is crowded.
                - MANDATORY CSS: Use box-sizing: border-box, word-break: keep-all, and overflow-wrap: break-word (or overflow-wrap: anywhere) on all text containers to prevent text from breaking out of boxes.
                - Use safe margins of about 36px to 56px and consistent gaps between sections.
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

    //그림,화면그림 기반 명함 생성 프롬프트:
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
                - Interpreted drawing placement constraints:
                %s

                Extra portfolio, skill, certificate, and link items:
                %s

                Core drawing rules:
                - Do not select an existing template.
                - Set layoutJson.templateCode exactly to "drawing_custom".
                - The drawing canvas is exactly 860px x 480px and uses the same coordinate system as cardRoot.
                - Every drawingLayoutJson element with a non-empty role must be mapped to the matching required ID in HTML, CSS, and layoutJson.elements.
                - Keep each mapped element in the same rough zone and relative order as the drawing. Do not move a left-side drawing element to the right side, or a top element to the bottom, unless it would otherwise overlap or clip.
                - Treat drawn boxes as placement anchors, not final visual boxes. Preserve the intended zone, then polish spacing, alignment, sizing, and visual grouping.
                - When cleaning the sketch, keep each element center close to the drawn center, but you may nudge, resize, align, or merge nearby regions to make the card look professionally designed.
                - Use absolute or clearly bounded positioning for the main drawn regions so the final result follows the sketch instead of falling back to a generic template layout.
                - Treat drawingLayoutJson as the user's intended placement and grouping.
                - Preserve the relative intent of the sketch, but do not copy messy or cramped coordinates blindly.
                - If the sketch is too close to an edge, overlapping, too small, or visually rough, snap it into clean alignment.
                - Use the user's sketch to decide where profileImage, nameText, jobText, introText, emailText, phoneText, portfolioArea, and linkArea should roughly go.
                - If the user did not draw a required element, place it naturally in the remaining space.
                - The final card must look polished, not like raw wireframe boxes.
                - Do not render a separate visible rounded panel for every drawn rectangle. Use visible panels only for meaningful grouped sections; simple text can sit directly in open space or in one refined shared group.
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
                - Return exactly one visible root card element: <div id='cardRoot'>...</div>.
                - Every required ID and optional companyText/departmentText must be inside cardRoot.
                - Do not close cardRoot until after all required elements, portfolioArea, and linkArea. Do not return stray closing tags or visible elements outside cardRoot.
                - Every non-void HTML element must have a valid closing tag such as </div>. Never output malformed text like /div>.
                - profileImage itself must have visible position, width, height, border-radius, and overflow styling. Do not rely only on a child wrapper for the profile frame.
                - If possible, include companyText and departmentText IDs too.
                - Do not create headlineText.
                - Use the exact Name value for nameText. Do not add words such as "card", "business card", or "명함" after the name.
                - Use only the user's provided job title, company, department, intro, email, and phone values. Do not invent missing personal details.
                - If drawingLayoutJson includes role=nameText, role=jobText, role=companyText, role=departmentText, role=introText, role=emailText, role=phoneText, role=profileImage, or role=portfolioArea, those IDs must be positioned according to the drawn box for that role.
                - jobText, companyText, and departmentText must be three separate text elements. Never place them on the same x/y coordinates or inside the same tiny box.
                - If companyText and departmentText are not explicitly drawn, stack them below jobText with clear vertical spacing instead of overlaying them.
                - nameText must be optically centered and balanced inside its intended region. Do not anchor the name to the lower-left corner of the drawn box.
                - profileImage must show the full uploaded photo without cropping the face. If the drawn profile box is too short or too wide, refine it into a neat square or portrait frame within the same rough zone.

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
                - STRONGLY PREFER using CSS Flexbox (display: flex) or CSS Grid to group elements naturally without overlapping. Avoid overusing position: absolute for every single element.
                - NEVER use fixed height (e.g., height: 160px) for text containers like introText, nameText, jobText, companyText, departmentText, emailText, phoneText, or contact boxes. Always use height: auto or min-height so the box can expand vertically if the text is long.
                - Use a clean grid-like composition with clear zones. Align left edges, top edges, or center lines where possible.
                - Section boxes must never overlap, touch, or visually crowd each other. If using position: absolute, calculate coordinates carefully to allow elements above them to expand without colliding.
                - Keep at least 24px of visible gap between introText box, contact box, profileImage, name/job group, portfolioArea, and decorative panels.
                - Keep every section at least 28px away from the cardRoot outer edge. Never set top, left, right, or bottom close enough that text touches or clips at the edge.
                - Do not place emailText or phoneText at the extreme top-left, top-right, bottom-left, or bottom-right corner unless the drawing explicitly shows a full contact box there with enough padding.
                - Prefer grouping emailText and phoneText together in one contact zone. If separated, they must still align cleanly and have matching widths, padding, and style.
                - If you create a contact group wrapper, phoneText and emailText must be actual children inside that wrapper. Otherwise, give phoneText and emailText their own absolute/flex-grid positioning.
                - Contact boxes should be at least 210px wide for email and at least 170px wide for phone, unless the text is shorter and still fully visible.
                - If portfolioArea would collide with introText or contact details, move or resize the sections instead of stacking them.
                - Treat portfolioArea as a real occupied panel even if your HTML leaves it empty. The server will inject content later, so reserve EXACTLY the 360px x 178px rectangle. Do not make it arbitrarily larger (e.g., 800px wide) than the reserved size.
                - No other section may be behind, under, inside, or touching the future portfolioArea rectangle. Force portfolioArea to sit clear of long text blocks.
                - If the sketch puts too many regions in one column, reorganize into a clean two-column or top/bottom layout while preserving the user's rough intent.
                - Do not copy rough drawing boxes as literal visible boxes if that creates a cluttered layout. Convert them into polished aligned sections with consistent padding, radius, and spacing.
                - Keep introText in a bounded readable block. If the intro is long, allow the block to expand downward (height: auto), reduce font size, or wrap text naturally.
                - Contact details must remain readable, fully inside their box, and must not sit under portfolioArea.
                - Text inside any bounded region must use display:flex or equivalent centering when appropriate, with consistent padding and line-height. No text should sit awkwardly at a bottom-left corner.
                - Do a final mental bounding-box check before answering: every visible text line must be fully inside cardRoot, every panel must fit in 860x480, and no section may cover another section.
                - Avoid large empty decorative boxes when content is crowded.
                - MANDATORY CSS: Use box-sizing: border-box, word-break: keep-all, and overflow-wrap: break-word (or overflow-wrap: anywhere) on all text containers to prevent text from breaking out of boxes.
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
                buildDrawingLayoutGuide(request.getDrawingLayoutJson()),
                buildExtraInfo(request.getExtraItems())
        );
    }

    //겹침,깨짐 수정용 프롬프트:
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
                - Return exactly one visible root card element: <div id='cardRoot'>...</div>.
                - Every required ID and optional companyText/departmentText must be inside cardRoot.
                - Do not close cardRoot until after all required elements, portfolioArea, and linkArea. Do not return stray closing tags or visible elements outside cardRoot.
                - Every non-void HTML element must have a valid closing tag such as </div>. Never output malformed text like /div>.
                - profileImage itself must have visible position, width, height, border-radius, and overflow styling. Do not rely only on a child wrapper for the profile frame.

                Layout safety rules:
                - Keep every visual element inside cardRoot.
                - STRONGLY PREFER using CSS Flexbox (display: flex) or CSS Grid to group elements naturally without overlapping. Avoid overusing position: absolute for every single element.
                - NEVER use fixed height (e.g., height: 160px) for text containers like introText, nameText, jobText, companyText, departmentText, emailText, phoneText, or contact boxes. Always use height: auto or min-height so the box can expand vertically if the text is long.
                - Use a clean grid-like composition with clear zones. Align left edges, top edges, or center lines where possible.
                - Section boxes must never overlap, touch, or visually crowd each other. If using position: absolute, calculate coordinates carefully to allow elements above them to expand without colliding.
                - Keep at least 24px of visible gap between introText box, contact box, profileImage, name/job group, portfolioArea, and decorative panels.
                - Keep every section at least 28px away from the cardRoot outer edge. Never set top, left, right, or bottom close enough that text touches or clips at the edge.
                - Do not place emailText or phoneText at the extreme top-left, top-right, bottom-left, or bottom-right corner unless the current layout already has a full contact box there with enough padding.
                - Prefer grouping emailText and phoneText together in one contact zone. If separated, they must still align cleanly and have matching widths, padding, and style.
                - If you create a contact group wrapper, phoneText and emailText must be actual children inside that wrapper. Otherwise, give phoneText and emailText their own absolute/flex-grid positioning.
                - Contact boxes should be at least 210px wide for email and at least 170px wide for phone, unless the text is shorter and still fully visible.
                - If portfolioArea would collide with introText or contact details, move or resize the sections instead of stacking them.
                - Treat portfolioArea as a real occupied panel even if your HTML leaves it empty. The server will inject content later, so reserve EXACTLY the 360px x 178px rectangle. Do not make it arbitrarily larger (e.g., 800px wide) than the reserved size.
                - No other section may be behind, under, inside, or touching the future portfolioArea rectangle. Force portfolioArea to sit clear of long text blocks.
                - If the current layout puts too many regions in one column, reorganize into a clean two-column or top/bottom layout while preserving the current visual mood.
                - Do not preserve rough or cluttered visible boxes if they cause overlap. Convert them into polished aligned sections with consistent padding, radius, and spacing.
                - Keep introText in a bounded readable block. If the intro is long, allow the block to expand downward (height: auto), reduce font size, or wrap text naturally.
                - Contact details must remain readable, fully inside their box, and must not sit under portfolioArea.
                - Text inside any bounded region must use display: flex or equivalent centering when appropriate, with consistent padding and line-height. No text should sit awkwardly at a bottom-left corner.
                - Do a final mental bounding-box check before answering: every visible text line must be fully inside cardRoot, every panel must fit in 860x480, and no section may cover another section.
                - Avoid large empty decorative boxes when content is crowded.
                - MANDATORY CSS: Use box-sizing: border-box, word-break: keep-all, and overflow-wrap: break-word (or overflow-wrap: anywhere) on all text containers to prevent text from breaking out of boxes.
                - Use safe margins of about 36px to 56px and consistent gaps between sections.

                Portfolio/extra area rules:
                - portfolioArea must remain a single empty placeholder container. Do not include the Portfolio / Skills / Links title, chips, URLs, skill names, certificate names, or portfolio titles in the generated HTML.
                - Do not use CSS ::before or ::after on portfolioArea or linkArea to render titles, item names, URLs, or labels.
                - Do not create duplicate portfolio, skills, links, certificate, tag, chip, or badge panels outside portfolioArea.
                - Keep the current colors and mood, but change coordinates, widths, heights, font sizes, and spacing as much as needed to remove overlap.
                - If introText and portfolioArea collide, shrink or move introText first, or move contact details, while preserving the card's colors and style.
                - If the original template coordinates are the cause of overlap, ignore those coordinates and reflow the card into cleaner zones while preserving the same palette and visual mood.
                - Recommended repair layouts:
                  1. Left identity/profile column, right intro/contact column, portfolioArea as a lower-right block with no overlap.
                  2. Top identity/contact row, middle intro block, bottom portfolioArea band.
                  3. Left intro/contact column, right identity/profile column, portfolioArea below the right column.
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

    private String buildDrawingLayoutGuide(String drawingLayoutJson) {
        if (drawingLayoutJson == null || drawingLayoutJson.isBlank()) {
            return "- No drawn elements. Use the text description and create a balanced default layout.";
        }

        try {
            JsonNode root = objectMapper.readTree(drawingLayoutJson);
            JsonNode elements = root.path("elements");
            if (!elements.isArray() || elements.isEmpty()) {
                return "- No drawn elements. Use the text description and create a balanced default layout.";
            }

            StringBuilder guide = new StringBuilder();
            for (JsonNode element : elements) {
                String role = text(element, "role");
                if (role.isBlank()) {
                    role = "unassigned";
                }
                String label = text(element, "text");
                int x = element.path("x").asInt(0);
                int y = element.path("y").asInt(0);
                int width = element.path("width").asInt(0);
                int height = element.path("height").asInt(0);
                int centerX = x + width / 2;
                int centerY = y + height / 2;

                guide.append("- role=")
                        .append(role)
                        .append(", label=")
                        .append(label.isBlank() ? "-" : label)
                        .append(", box={x:")
                        .append(x)
                        .append(", y:")
                        .append(y)
                        .append(", width:")
                        .append(width)
                        .append(", height:")
                        .append(height)
                        .append("}, zone=")
                        .append(horizontalZone(centerX))
                        .append("-")
                        .append(verticalZone(centerY))
                        .append(". Keep #")
                        .append(role)
                        .append(" in this same rough zone after cleanup.\n");
            }
            return guide.toString();
        } catch (Exception ex) {
            return "- Drawing layout JSON could not be parsed. Use it only as raw reference and prioritize a clean, non-overlapping layout.";
        }
    }

    private String horizontalZone(int centerX) {
        if (centerX < 286) {
            return "left";
        }
        if (centerX > 574) {
            return "right";
        }
        return "center";
    }

    private String verticalZone(int centerY) {
        if (centerY < 160) {
            return "top";
        }
        if (centerY > 320) {
            return "bottom";
        }
        return "middle";
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
        userLog.warn("[USER-ACTION]|AI 생성 실패로 기본 템플릿 사용. cardId={}, reason={}", card.getCardId(), reason);

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
