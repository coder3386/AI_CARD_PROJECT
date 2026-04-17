package AIcard.cardapp.service.sfr004;

import AIcard.cardapp.dto.sfr004.CardGenerateRequestDto;
import AIcard.cardapp.dto.sfr004.CardGenerateResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CardGenerateService {

    private final RestTemplate restTemplate;

    public CardGenerateRequestDto loadMyInfoOrDefault() {
        CardGenerateRequestDto dto = new CardGenerateRequestDto();
        dto.setName("지민승");
        dto.setEmail("alstmd5310@example.com");
        dto.setPhone("010-1234-5678");
        dto.setTitle("백엔드 개발자");
        dto.setIntro("AI 기반 디지털 명함 서비스를 개발하고 있습니다.");
        dto.setStylePrompt("심플하고 블루톤, 모바일 느낌");
        return dto;
    }

    public CardGenerateResultDto generateCard(CardGenerateRequestDto request) {
        try {
            String prompt = buildFastPrompt(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "qwen2.5:3b");
            body.put("prompt", prompt);
            body.put("stream", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://localhost:11434/api/generate",
                    entity,
                    Map.class
            );

            Object raw = response.getBody().get("response");
            String llmText = raw == null ? "" : raw.toString().trim();

            return parseSimpleResponse(llmText, request);

        } catch (Exception e) {
            return fallbackResult(request);
        }
    }

    private String buildFastPrompt(CardGenerateRequestDto request) {
        String name = safe(request.getName(), "지민승");
        String title = safe(request.getTitle(), "백엔드 개발자");
        String intro = safe(request.getIntro(), "AI 기반 디지털 명함 서비스를 개발하고 있습니다.");
        String style = safe(request.getStylePrompt(), "심플하고 블루톤, 모바일 느낌");

        return """
                사용자 정보를 바탕으로 디지털 명함 초안을 만들어줘.
                반드시 아래 형식 그대로만 출력해.
                설명 절대 금지.
                코드블록 절대 금지.
                JSON 절대 금지.

                형식:
                name=...
                title=...
                intro=...
                themeColor=...
                layoutType=...

                조건:
                - intro는 1문장만
                - themeColor는 hex 색상코드 1개
                - layoutType은 mobile-card 또는 simple-card 중 하나

                사용자 정보:
                name: %s
                title: %s
                intro: %s
                style: %s
                """.formatted(name, title, intro, style);
    }

    private CardGenerateResultDto parseSimpleResponse(String text, CardGenerateRequestDto request) {
        CardGenerateResultDto result = fallbackResult(request);

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("name=")) {
                result.setName(trimmed.substring("name=".length()).trim());
            } else if (trimmed.startsWith("title=")) {
                result.setTitle(trimmed.substring("title=".length()).trim());
            } else if (trimmed.startsWith("intro=")) {
                result.setIntro(trimmed.substring("intro=".length()).trim());
            } else if (trimmed.startsWith("themeColor=")) {
                result.setThemeColor(trimmed.substring("themeColor=".length()).trim());
            } else if (trimmed.startsWith("layoutType=")) {
                result.setLayoutType(trimmed.substring("layoutType=".length()).trim());
            }
        }

        return result;
    }

    private CardGenerateResultDto fallbackResult(CardGenerateRequestDto request) {
        CardGenerateResultDto result = new CardGenerateResultDto();
        result.setName(safe(request.getName(), "지민승"));
        result.setTitle(safe(request.getTitle(), "백엔드 개발자"));
        result.setIntro(safe(request.getIntro(), "AI 기반 디지털 명함 서비스를 개발하고 있습니다."));
        result.setThemeColor("#4BA3C7");
        result.setLayoutType("mobile-card");
        result.setEmail(safe(request.getEmail(), "alstmd5310@example.com"));
        result.setPhone(safe(request.getPhone(), "010-1234-5678"));
        return result;
    }

    private String safe(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}