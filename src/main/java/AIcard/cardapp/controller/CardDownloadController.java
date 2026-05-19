package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.CardDownloadDTO;
import AIcard.cardapp.model.Card;
import AIcard.cardapp.repository.CardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/card")
public class CardDownloadController {

    @Autowired
    private CardRepository cardRepository;

    @GetMapping("/{id}")
    public String getCardView(@PathVariable Long id, Model model) {
        // 1. DB에서 데이터 조회
        System.out.println(">>> 요청된 명함 ID: " + id); // 1. 어떤 ID를 찾는지 확인

        Card card = cardRepository.findById(id).orElse(null); // .orElseThrow 대신 null 반환

        if (card == null) {
            System.out.println(">>> 에러: DB에서 ID=" + id + " 데이터를 찾지 못했습니다.");
            model.addAttribute("error", "해당 ID의 명함이 없습니다.");
            return "error"; // 혹은 본인의 에러 페이지
        }
        // 2. DTO 변환
        CardDownloadDTO dto = CardDownloadDTO.builder()
                .name(card.getName())
                .position(card.getPosition())
                .email(card.getEmail())
                .phone(card.getPhone())
                .imagePath(card.getImagePath())
                .build();

        // 3. 모델에 담기
        model.addAttribute("card", dto);

        return "card/testid";
    }
    @Autowired
    private TemplateEngine templateEngine;

    // 새로운 다운로드 메서드 추가
    @GetMapping("/download/{id}")
    @ResponseBody
    public ResponseEntity<String> downloadHtmlFile(@PathVariable Long id) {
        System.out.println(">>> 들어온 ID 확인: " + id);
        Card card = cardRepository.findById(id).orElse(null);
        if (card == null) return ResponseEntity.notFound().build();

        // 1. Thymeleaf Context에 데이터 설정 (HTML 내의 ${card}에 들어갈 내용)
        Context context = new Context();
        context.setVariable("card", card);

        // 2. card/testid.html 템플릿을 읽어서 HTML 문자열로 변환
        String htmlContent = templateEngine.process("card/testid", context);

        // 3. 파일로 다운로드하도록 응답 헤더 설정
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-card.html\"")
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent);
    }
}
