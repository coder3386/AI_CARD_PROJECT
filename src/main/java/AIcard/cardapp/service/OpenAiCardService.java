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
    private final RestClient restClient;

    @Value("${openai.api.key:}")
    private String configuredApiKey;

    @Value("${openai.model:gpt-5.4-mini}")
    private String model;

    public OpenAiCardService() {
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .build();
    }

    public AiCardResponse generateCardDraft(BusinessCard card, List<Template> templates, CardCreateRequest request) {
        return generateCardDraft(card, templates, request, request.getApiKey());
    }

    public AiCardResponse generateCardDraft(
            BusinessCard card,
            List<Template> templates,
            CardCreateRequest request,
            String userProvidedApiKey
    ) {
        String prompt = buildPrompt(card, templates, request);
        String apiKeyToUse = resolveApiKey(userProvidedApiKey);

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a safe HTML/CSS digital business card designer. Return only one JSON object."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String apiResult = restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKeyToUse)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseResponse(apiResult, prompt, card);
        } catch (Exception ex) {
            return fallback(card, prompt, "GPT API 호출 실패로 기본 명함을 생성했습니다.");
        }
    }

    private String resolveApiKey(String userProvidedApiKey) {
        if (userProvidedApiKey != null && !userProvidedApiKey.isBlank()) {
            return userProvidedApiKey.trim();
        }
        if (configuredApiKey != null && !configuredApiKey.isBlank() && !configuredApiKey.contains("${OPENAI_API_KEY}")) {
            return configuredApiKey.trim();
        }
        throw new IllegalStateException("API 키가 설정되지 않았습니다.");
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

                Return only this JSON format:
                {
                  "reason": "recommendation reason",
                  "html": "<div id='cardRoot'>...</div>",
                  "css": "#cardRoot { ... }",
                  "layoutJson": {
                    "templateCode": "ai_generated",
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
                - Do not create headlineText.
                - If possible, include companyText and departmentText IDs too.
                - Use the exact Name value for nameText. Do not add words such as "card", "business card", or "명함" after the name.
                - Use only the user's provided job title, company, department, intro, email, and phone values. Do not invent missing personal details.
                - Drawing description is only design guidance. Do not copy it into introText unless the same content was also provided as Intro.
                - Always reserve a future extra-info area for portfolioArea near the lower-right or lower-middle area, even when Extra items is "- none".
                - Do not place nameText, jobText, introText, emailText, phoneText, companyText, departmentText, or decorations over the reserved portfolioArea space.
                - portfolioArea must only include items listed in Extra portfolio, skill, certificate, and link items. If Extra items is "- none", keep the area visually available as an empty extra section.
                - linkArea must stay empty unless drawingLayout explicitly places it. The server will render LINK items together inside portfolioArea.
                - Do not place portfolioArea or linkArea at the top-left corner unless drawingLayout explicitly places them there.
                - Prefer portfolioArea near the lower-right or lower-middle area, with about 360px x 112px of usable space.
                - Do not invent placeholder links such as GitHub, Blog, Portfolio, or SNS.
                - Keep every visual element inside cardRoot. Do not let text overflow outside the 860px x 480px card.
                - If drawingLayoutJson is provided, keep the user's specified elements as close to their x, y, width, and height as possible.
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
                templateInfo
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
