package AIcard.cardapp.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
/* HTML 버튼 th:href="@{/api/cards/download}" 경로와 일치하도록 s를 붙여 /api/cards 로 변경 */
@RequestMapping("/api/cards")
public class CardDownloadController {

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadIntegratedCard(@RequestParam("cardId") Long cardId) {
        try {
            // 1. 파일 읽어오기
            ClassPathResource htmlResource = new ClassPathResource("templates/card/testid.html");
            ClassPathResource cssResource = new ClassPathResource("static/cards/testid.css");

            String htmlContent = new String(htmlResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String cssContent = new String(cssResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // 2. 오프라인 파일 가공 (타임리프 문법 정제 및 기존 외부 스타일시트 링크 제거)
            // 다운로드된 완전한 독립 파일 내부에서는 기존에 존재하던 외부 <link> 태그가 불필요하므로 제거합니다.
            htmlContent = htmlContent.replaceAll("<link rel=\"stylesheet\"[^>]*>", "");

            // 오프라인 파일이므로 불필요한 타임리프 링크 속성들(th:href, th:onclick 등) 제거해 주면 더욱 완벽합니다.
            htmlContent = htmlContent.replaceAll("th:href=\"[^\"]*\"", "");

            // 3. HTML 문서 내에 CSS 스타일 주입 (Inlining)
            String styleTag = "\n<style>\n" + cssContent + "\n</style>\n";
            String integratedHtml;

            if (htmlContent.contains("</head>")) {
                integratedHtml = htmlContent.replace("</head>", styleTag + "</head>");
            } else {
                // 헤더가 없는 조각용 방어 코드
                integratedHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
                        + cssContent + "</style></head><body>"
                        + htmlContent + "</body></html>";
            }

            // 4. 파일 다운로드를 위한 HTTP 헤더 및 바이트 배열 준비
            byte[] fileBytes = integratedHtml.getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_HTML);
            headers.setContentDispositionFormData("attachment", "my_digital_card_" + cardId + ".html");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);

        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}