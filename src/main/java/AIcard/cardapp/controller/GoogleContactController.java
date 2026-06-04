package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.GoogleContactDTO;
import AIcard.cardapp.service.GoogleContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*") // 프론트엔드가 리액트/웹 환경일 때 포트 불일치로 인한 차단(CORS) 방지
@RestController // REST API 요청을 받는 클래스임을 선언
@RequestMapping("/api/contacts") // 이 주소로 들어오는 요청을 처리
@RequiredArgsConstructor // 생성자를 통해 서비스를 자동으로 주입받음
public class GoogleContactController {

    private final GoogleContactService googleContactService;

    /**
     * 사용자의 명함 정보를 구글 주소록에 저장하는 엔드포인트
     * @param bearerToken 프론트엔드 요청 헤더(Authorization)에 실려오는 구글 Access Token
     * @param contactDto 프론트엔드에서 보낸 명함 데이터 (이름, 번호 등)
     * @return 성공 시 성공 메시지 반환
     */
    @PostMapping("/sync")
    public ResponseEntity<String> saveToGoogleContacts(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @RequestBody GoogleContactDTO contactDto) {
        try {
            // 1. 헤더에 실려온 토큰 검증 및 정제
            if (bearerToken == null || bearerToken.isEmpty()) {
                return ResponseEntity.badRequest().body("구글 인증 토큰(Authorization Header)이 존재하지 않습니다.");
            }

            String accessTokenStr = bearerToken;
            if (bearerToken.startsWith("Bearer ")) {
                accessTokenStr = bearerToken.substring(7).trim();
            }

            // 2. 서비스에게 토큰과 DTO 데이터를 함께 맡김
            googleContactService.saveContact(accessTokenStr, contactDto);

            // 3. 처리가 잘 끝났다면 성공 메시지를 사용자에게 전달
            return ResponseEntity.ok("구글 주소록에 명함이 성공적으로 저장되었습니다!");
        } catch (Exception e) {
            e.printStackTrace();
            // 4. 문제가 생겼다면 에러 메시지 전달
            return ResponseEntity.internalServerError().body("저장 중 오류 발생: " + e.getMessage());
        }
    }
}