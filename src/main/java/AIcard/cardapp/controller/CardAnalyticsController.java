package AIcard.cardapp.controller;

import com.google.analytics.data.v1beta.*;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/card")
public class CardAnalyticsController {

    // GA4 속성 ID 설정 (숫자로만 구성된 ID)
    // 구글 애널리틱스 [관리] -> [속성 설정] 메뉴에서 확인한 숫자를 적어주세요.
    private final String GA4_PROPERTY_ID = "539587337";

    /**
     * 명함 조회 및 구글 애널리틱스 기반 실시간 통계 가져오기
     * URL: http://wooserver76.iptime.org/card/main/{cardId}
     */
    @GetMapping("/main/{cardId}")
    public String getCardDetailWithGA4(@PathVariable("cardId") String cardId, Model model) {

        // 사용자가 접속한 명함의 하위 경로 패턴 계산 (예: /card/main/james)
        String targetPagePath = "/card/main/" + cardId;

        // 통계 데이터를 담을 맵 초기화
        Map<String, Long> osStats = new HashMap<>();
        osStats.put("iOS", 0L);
        osStats.put("Android", 0L);
        osStats.put("Windows", 0L);
        osStats.put("Macintosh", 0L);
        osStats.put("Other", 0L);

        long totalViews = 0;

        try {
            // 1. resources 폴더에 둔 JSON 키 파일 읽어오기 및 인증 설정
            ClassPathResource resource = new ClassPathResource("google-key.json");
            GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream());

            BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            // 2. 구글 애널리틱스 데이터 클라이언트 실행
            try (BetaAnalyticsDataClient analyticsClient = BetaAnalyticsDataClient.create(settings)) {

                // 3. 구글에 보낼 리포트 요청(RunReportRequest) 조립
                RunReportRequest request = RunReportRequest.newBuilder()
                        .setProperty("properties/" + GA4_PROPERTY_ID)
                        // 조회 기간 설정 (예: 서비스 시작년도인 2026년부터 오늘까지)
                        .addDateRanges(DateRange.newBuilder().setStartDate("2026-01-01").setEndDate("today"))
                        // 가져올 기준 정보: URL 경로(pagePath)와 운영체제(operatingSystem)
                        .addDimensions(Dimension.newBuilder().setName("pagePath"))
                        .addDimensions(Dimension.newBuilder().setName("operatingSystem"))
                        // 가져올 수치: 조회수(screenPageViews)
                        .addMetrics(Metric.newBuilder().setName("screenPageViews"))
                        // 중요 필터: 모든 명함이 아니라 "현재 접속한 이 명함 주소"의 데이터만 골라내기
                        .setDimensionFilter(FilterExpression.newBuilder()
                                .setFilter(Filter.newBuilder()
                                        .setFieldName("pagePath")
                                        .setStringFilter(Filter.StringFilter.newBuilder()
                                                .setMatchType(Filter.StringFilter.MatchType.EXACT)
                                                .setValue(targetPagePath)
                                        )
                                )
                        )
                        .build();

                // 4. 구글 서버로부터 응답 결과 수신
                RunReportResponse response = analyticsClient.runReport(request);

                // 5. 받아온 로우(Row) 데이터를 돌면서 자바 맵 변수에 가공 및 합산
                for (Row row : response.getRowsList()) {
                    String osName = row.getDimensionValues(1).getValue(); // operatingSystem 값
                    long views = Long.parseLong(row.getMetricValues(0).getValue()); // screenPageViews 값

                    totalViews += views; // 전체 조회수 누적

                    if (osName.equalsIgnoreCase("iOS")) {
                        osStats.put("iOS", osStats.get("iOS") + views);
                    } else if (osName.equalsIgnoreCase("Android")) {
                        osStats.put("Android", osStats.get("Android") + views);
                    } else if (osName.equalsIgnoreCase("Windows")) {
                        osStats.put("Windows", osStats.get("Windows") + views);
                    } else if (osName.equalsIgnoreCase("Macintosh")) {
                        osStats.put("Macintosh", osStats.get("Macintosh") + views);
                    } else {
                        osStats.put("Other", osStats.get("Other") + views);
                    }
                }
            }

            // 6. 가공된 구글 애널리틱스 통계를 화면(View) 단으로 전송
            model.addAttribute("cardId", cardId);
            model.addAttribute("totalViews", totalViews);
            model.addAttribute("osStats", osStats);

            return "card_detail"; // card_detail.html 템플릿 호출

        } catch (IOException e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "구글 인증 및 데이터 로드 중 오류가 발생했습니다.");
            return "error";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "서버 오류가 발생했습니다.");
            return "error";
        }
    }
}