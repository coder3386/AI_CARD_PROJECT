package AIcard.cardapp.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class GoogleAnalyticsDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveRequest {
        private String trackingId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long cardId;
        private List<DailyViewCount> viewStats;
    }


    // 일자별 조회수를 매핑할 단위 DTO
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyViewCount {
        private String date;      // 구글이 주는 날짜 포맷 (예: "20260609")
        private long viewCount;   // 해당 날짜의 명함 조회수
    }

    // OAuth 토큰 수신용 DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("expires_in")
        private Long expiresIn;
    }
}