package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.GoogleContactDTO;
import AIcard.cardapp.service.GoogleContactService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/oauth")
public class GoogleOAuthController {

    private final GoogleContactService googleContactService;

    // application.properties에 등록해둔 구글 클라이언트 정보들
    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    // 구글 콘솔에 '승인된 리디렉션 URI'로 등록할 주소와 일치해야 합니다.
    private final String redirectUri = "http://wooserver76.iptime.org/api/oauth/google/callback";

    public GoogleOAuthController(GoogleContactService googleContactService) {
        this.googleContactService = googleContactService;
    }

    /**
     * 1. 구글 로그인 완료 후, 구글이 인가 코드(code)를 던져주는 Callback 주소
     */
    @GetMapping("/google/callback")
    public ResponseEntity<String> googleCallback(@RequestParam("code") String code) {
        try {
            // 구글 인가 코드(code)를 가지고 실시간으로 Access Token 교환 요청
            RestTemplate restTemplate = new RestTemplate();
            String tokenUrl = "https://oauth2.googleapis.com/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            // 구글이 발급해준 Access Token 추출
            String accessTokenStr = (String) response.getBody().get("access_token");

            // 임시 테스트용 데이터 세팅 (나중에는 프론트에서 넘어온 값을 받아야 함)
            GoogleContactDTO testContact = new GoogleContactDTO();
            testContact.setName("테스트 유저");
            testContact.setPhoneNumber("010-1234-5678");
            testContact.setEmail("test@example.com");
            testContact.setOrganization("AI 명함 회사");
            testContact.setJobTitle("개발팀장");
            testContact.setNotes("IPTOME 서버 연동 테스트 성공 결과물");

            // 작성하신 Service 호출하여 구글 주소록에 등록 실행!!
            googleContactService.saveContact(accessTokenStr, testContact);

            return ResponseEntity.ok("구글 로그인 및 주소록 저장 테스트 성공!");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("테스트 실패 에러 발생: " + e.getMessage());
        }
    }
}