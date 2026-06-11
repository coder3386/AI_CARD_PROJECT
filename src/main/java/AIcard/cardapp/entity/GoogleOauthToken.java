package AIcard.cardapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "google_oauth_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleOauthToken {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "access_token", length = 500)
    private String accessToken;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    public GoogleOauthToken(Long userId) {
        this.userId = userId;
    }

    public void updateOAuthTokens(String accessToken, String refreshToken, LocalDateTime tokenExpiresAt) {
        this.accessToken = accessToken;
        // OAuth 가이드에 따라 재연동 시 refreshToken이 안 들어올 수 있으므로 방어 처리
        if (refreshToken != null) {
            this.refreshToken = refreshToken;
        }
        this.tokenExpiresAt = tokenExpiresAt;
    }
}