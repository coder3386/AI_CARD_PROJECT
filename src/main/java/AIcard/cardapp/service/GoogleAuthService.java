package AIcard.cardapp.service;

import AIcard.cardapp.entity.GoogleOauthToken;
import AIcard.cardapp.repository.GoogleOauthTokenRepository;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private static final Logger mgrLogger = LoggerFactory.getLogger("MANAGER_LOGGER");

    private final GoogleOauthTokenRepository repository;

    private final String clientId = "893320613116-b1d36l01tvd7gqqt3dkaj22qapb45aua.apps.googleusercontent.com";
    private final String clientSecret = "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";

    public String getValidAccessToken(Long userId) {
        try {
            GoogleOauthToken token = repository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자 연동 정보가 없습니다."));

            if (token.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
                return refreshAccessToken(token);
            }

            return token.getAccessToken();

        } catch (Exception e) {
            throw new RuntimeException("토큰 조회 실패", e);
        }
    }

    private String refreshAccessToken(GoogleOauthToken token) throws Exception {
        TokenResponse response = new GoogleRefreshTokenRequest(
                new NetHttpTransport(),
                new GsonFactory(),
                token.getRefreshToken(),
                clientId,
                clientSecret
        ).execute();

        LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(response.getExpiresInSeconds());
        String newRefreshToken = response.getRefreshToken() != null ? response.getRefreshToken() : token.getRefreshToken();

        token.updateOAuthTokens(response.getAccessToken(), newRefreshToken, newExpiresAt);
        repository.save(token);

        mgrLogger.info("[MGR-ACTION] 시스템이 유저 ID '{}'의 구글 OAuth Access Token을 성공적으로 갱신함.", token.getUserId());

        return response.getAccessToken();
    }
    public void processAuthorizationCode(Long userId, String code) throws Exception {
        TokenResponse response = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                new GsonFactory(),
                clientId,
                clientSecret,
                code,
                "http://wooserver76.iptime.org/card/oauth/callback"
        ).execute();

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(response.getExpiresInSeconds());

        GoogleOauthToken token = repository.findById(userId)
                .orElse(new GoogleOauthToken(userId));

        token.updateOAuthTokens(
                response.getAccessToken(),
                response.getRefreshToken(),
                expiresAt
        );

        repository.save(token);

        mgrLogger.info("[MGR-ACTION] 시스템이 유저 ID '{}'의 구글 서비스 연동(주소록 및 애널리틱스 권한)을 승인하고 자격 증명을 저장함.", userId);
    }
}