package AIcard.cardapp.controller;

import AIcard.cardapp.entity.User;
import AIcard.cardapp.repository.UserRepository;
import AIcard.cardapp.service.GoogleContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class GoogleContactController {

    private final GoogleContactService googleContactService;
    private final UserRepository userRepository;

    @PostMapping("/save")
    public ResponseEntity<String> saveToGoogleContacts(
            @RequestParam("cardId") Long cardId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("로그인이 필요합니다.");
        }

        Object principal = authentication.getPrincipal();
        Long userId = null;

        if (principal instanceof UserDetails) {
            String loginId = ((UserDetails) principal).getUsername();
            userId = userRepository.findByLoginId(loginId)
                    .map(User::getUserId)
                    .orElse(null);
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("유효한 사용자 정보를 찾을 수 없습니다.");
        }

        try {
            googleContactService.saveCardToGoogleContacts(cardId, userId);
            return ResponseEntity.ok("구글 주소록에 명함이 성공적으로 저장되었습니다!");
        } catch (Exception e) {
            log.error("저장 중 오류 발생", e);
            return ResponseEntity.internalServerError().body("저장 중 오류 발생: " + e.getMessage());
        }
    }
}