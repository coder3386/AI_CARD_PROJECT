package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.CardDownloadDTO;
import AIcard.cardapp.entity.BusinessCard;              // 1. 팀원의 진짜 엔티티 임포트
import AIcard.cardapp.repository.BusinessCardRepository; // 2. 팀원의 진짜 리포지토리 임포트
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

    // 3. 기존 CardRepository 대신 팀원의 BusinessCardRepository 사용
    @Autowired
    private BusinessCardRepository businessCardRepository;

    @Autowired
    private TemplateEngine templateEngine;

    @GetMapping("/{id}")
    public String getCardView(@PathVariable Long id, Model model) {
        System.out.println(">>> 요청된 명함 ID: " + id);

        // 4. 진짜 엔티티인 BusinessCard로 데이터 조회
        BusinessCard card = businessCardRepository.findById(id).orElse(null);

        if (card == null) {
            System.out.println(">>> 에러: DB에서 ID=" + id + " 데이터를 찾지 못했습니다.");
            model.addAttribute("error", "해당 ID의 명함이 없습니다.");
            return "error";
        }

        // 5. 팀원이 구현해둔 엔티티 내 편의 메서드를 통해 DTO 채우기
        CardDownloadDTO dto = CardDownloadDTO.builder()
                .name(card.getDisplayName())  // business_cards 테이블의 display_name
                .position(card.getJobTitle())  // business_card_details 테이블의 job_title
                .email(card.getEmail())        // business_card_details 테이블의 email
                .phone(card.getPhone())        // business_card_details 테이블의 phone
                .imagePath("default.png")      // 임시 이미지 경로 유지 (필요시 수정)
                .build();

        // 6. 기존 타임리프 코드가 깨지지 않게 "card"라는 이름으로 DTO 전달
        model.addAttribute("card", dto);

        return "card/testid";
    }

    @GetMapping("/download/{id}")
    @ResponseBody
    public ResponseEntity<String> downloadHtmlFile(@PathVariable Long id) {
        System.out.println(">>> 들어온 ID 확인: " + id);

        // 7. 다운로드 기능에서도 동일하게 진짜 엔티티로 조회
        BusinessCard card = businessCardRepository.findById(id).orElse(null);
        if (card == null) return ResponseEntity.notFound().build();

        // 8. 뷰와 마찬가지로 안전하게 DTO로 변환하여 Thymeleaf Context에 전달합니다.
        CardDownloadDTO dto = CardDownloadDTO.builder()
                .name(card.getDisplayName())
                .position(card.getJobTitle())
                .email(card.getEmail())
                .phone(card.getPhone())
                .imagePath("default.png")
                .build();

        Context context = new Context();
        context.setVariable("card", dto); // HTML 파일 내부에서 사용할 변수

        // card/testid.html 템플릿을 읽어서 HTML 문자열로 변환
        String htmlContent = templateEngine.process("card/testid", context);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-card.html\"")
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent);
    }
}