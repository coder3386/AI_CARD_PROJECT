package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.GoogleContactDTO;
import AIcard.cardapp.service.GoogleContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController // REST API 요청을 받는 클래스임을 선언
@RequestMapping("/api/contacts") // 이 주소로 들어오는 요청을 처리
@RequiredArgsConstructor // 생성자를 통해 서비스를 자동으로 주입받음
public class GoogleContactController {

    private final GoogleContactService googleContactService;

    /**
     * 사용자의 명함 정보를 구글 주소록에 저장하는 엔드포인트
     * @param contactDto 프론트엔드에서 보낸 명함 데이터 (이름, 번호 등)
     * @return 성공 시 성공 메시지 반환
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveToGoogleContacts(@RequestBody GoogleContactDTO contactDto) {
        try {
            // 1. 서비스에게 주소록 저장 업무를 맡김
            googleContactService.saveContact(contactDto);

            // 2. 처리가 잘 끝났다면 성공 메시지를 사용자에게 전달
            return ResponseEntity.ok("구글 주소록에 명함이 성공적으로 저장되었습니다!");
        } catch (Exception e) {
            // 3. 문제가 생겼다면 에러 메시지 전달
            return ResponseEntity.internalServerError().body("저장 중 오류 발생: " + e.getMessage());
        }
    }
}