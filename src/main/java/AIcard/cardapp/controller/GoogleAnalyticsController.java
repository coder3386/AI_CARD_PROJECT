package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.GoogleAnalyticsDTO;
import AIcard.cardapp.entity.User;
import AIcard.cardapp.repository.UserRepository;
import AIcard.cardapp.service.GoogleAnalyticsService;
import AIcard.cardapp.service.GoogleAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class GoogleAnalyticsController {

    private final GoogleAnalyticsService googleAnalyticsService;
    private final UserRepository userRepository;
    private final GoogleAuthService googleAuthService;

    @GetMapping("/cards/{cardId}/analytics")
    public String cardAnalyticsDashboardPage(@PathVariable Long cardId, Model model) {
        model.addAttribute("cardId", cardId);
        return "card_analytics";
    }

    @GetMapping("/api/google-analytics/stats")
    @ResponseBody
    public ResponseEntity<GoogleAnalyticsDTO.Response> getChartStats(@RequestParam Long cardId,
                                                                     @RequestParam(defaultValue = "30days") String rangeType,
                                                                     Authentication authentication) {

        org.slf4j.Logger userLogger = org.slf4j.LoggerFactory.getLogger("USER_LOGGER");
        String currentLoginId = (authentication != null) ? authentication.getName() : "ANONYMOUS";

        GoogleAnalyticsDTO.Response stats = googleAnalyticsService.getCardViewStatistics(cardId, rangeType);

        userLogger.info("[USER-ACTION] 유저 '{}'이 명함 ID '{}'의 통계 차트 데이터를 조회함.", currentLoginId, cardId);

        return ResponseEntity.ok(stats);
    }

    //구글 OAuth 연동 시작 (주소록 권한 포함)
    @GetMapping("/oauth/start")
    public String startOAuth() {
        String clientId = "893320613116-b1d36l01tvd7gqqt3dkaj22qapb45aua.apps.googleusercontent.com";
        String redirectUri = "http://wooserver76.iptime.org/card/oauth/callback";
        String scopes = "https://www.googleapis.com/auth/analytics.readonly%20https://www.googleapis.com/auth/contacts";

        return "redirect:https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + scopes
                + "&access_type=offline"
                + "&prompt=consent";
    }
    // 구글 OAuth 콜백 처리
    @GetMapping("/oauth/callback")
    public String oauthCallback(@RequestParam Map<String, String> params,
                                Authentication authentication) {
        String code = params.get("code");

        Long userId = null;

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof Long) {
                userId = (Long) principal;
            }
            else if (principal instanceof UserDetails) {
                try {
                    String loginId = ((UserDetails) principal).getUsername();
                    Optional<User> userOptional = userRepository.findByLoginId(loginId);

                    if (userOptional.isPresent()) {
                        userId = userOptional.get().getUserId();
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ UserDetails에서 ID 조회 중 오류: " + e.getMessage());
                }
            }
        }

        if (userId == null) {
            System.out.println("❌ 경고: userId를 찾지 못해 임시값 1L 대입");
            userId = 1L;
        }

        if (code != null) {
            try {
                googleAuthService.processAuthorizationCode(userId, code);

                System.out.println("✅ 구글 인증 성공!");
                return "redirect:/card/edit?linked=success";
            } catch (Exception e) {
                e.printStackTrace();
                return "redirect:/card/edit?linked=error";
            }
        }
        return "redirect:/card/edit";
    }
}