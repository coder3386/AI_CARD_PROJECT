package AIcard.cardapp.service;

import AIcard.cardapp.DTO.AiCardResponse;
import AIcard.cardapp.DTO.CardCreateRequest;
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
    private String apiKey;

    @Value("${openai.model:gpt-5.4-mini}")
    private String model;

    public OpenAiCardService() {
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com")
                .build();
    }

    public AiCardResponse generateCardDraft(BusinessCard card, List<Template> templates, CardCreateRequest request) {
        String prompt = buildPrompt(card, templates, request);
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("${OPENAI_API_KEY}")) {
            return fallback(card, prompt, "OPENAI_API_KEY가 없어 기본 명함을 생성했습니다.");
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", "너는 안전한 HTML/CSS 기반 디지털 명함 디자이너다. 응답은 반드시 JSON 객체 하나만 반환한다."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String apiResult = restClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseResponse(apiResult, prompt, card);
        } catch (Exception ex) {
            return fallback(card, prompt, "GPT API 호출 실패로 기본 명함을 생성했습니다. " + ex.getMessage());
        }
    }

    private AiCardResponse parseResponse(String apiResult, String prompt, BusinessCard card) throws Exception {
        JsonNode root = objectMapper.readTree(apiResult);
        String content = root.at("/choices/0/message/content").asText();
        if (content == null || content.isBlank()) {
            return fallback(card, prompt, "GPT 응답에서 content를 찾지 못해 기본 명함을 생성했습니다.");
        }

        String cleaned = stripJsonFence(content);
        JsonNode cardJson = objectMapper.readTree(cleaned);
        String html = text(cardJson, "html");
        String css = text(cardJson, "css");
        String reason = text(cardJson, "reason");
        JsonNode layoutNode = cardJson.get("layoutJson");
        String layoutJson = layoutNode == null || layoutNode.isNull()
                ? defaultLayoutJson()
                : objectMapper.writeValueAsString(layoutNode);

        if (!isSafeAndComplete(html, css)) {
            return fallback(card, prompt, "GPT 응답의 필수 ID가 누락되었거나 사용할 수 없는 태그가 있어 기본 명함을 생성했습니다.");
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
                사용자의 명함 정보를 바탕으로 860px x 480px 디지털 명함 HTML/CSS 초안을 만들어라.

                사용자 정보:
                - 이름: %s
                - 직무/직책: %s
                - 회사/소속: %s
                - 부서/전공: %s
                - 자기소개: %s
                - 이메일: %s
                - 전화번호: %s
                - 원하는 분위기: %s
                - 선호 색상: %s

                참고 가능한 템플릿:
                %s

                반드시 아래 JSON 형식만 반환한다.
                {
                  "reason": "추천 이유",
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

                HTML 규칙:
                - html, head, body 태그를 만들지 않는다.
                - script 태그, iframe 태그, 외부 CDN을 사용하지 않는다.
                - class 이름은 card- 로 시작한다.
                - 수정 가능한 주요 요소에는 editable 클래스를 붙인다.
                - 전체 카드 크기는 860px x 480px 기준이다.
                - headlineText는 만들지 않는다.
                - 대표 문구가 필요하면 introText 안에 자연스럽게 포함한다.
                - 아래 ID는 반드시 포함한다: cardRoot, nameText, jobText, introText, emailText, phoneText, profileImage, portfolioArea, linkArea
                - 가능하면 companyText, departmentText ID도 포함한다.
                """.formatted(
                safe(card.getDisplayName()),
                safe(card.getJobTitle()),
                safe(card.getCompany()),
                safe(card.getDepartment()),
                safe(card.getIntro()),
                safe(card.getEmail()),
                safe(card.getPhone()),
                safe(request.getMood()),
                safe(request.getPreferredColor()),
                templateInfo
        );
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
