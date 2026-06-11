package AIcard.cardapp.service;

import AIcard.cardapp.DTO.GoogleAnalyticsDTO;
import com.google.analytics.data.v1beta.*;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.entity.BusinessCard;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoogleAnalyticsService {

    private final String PROPERTY_ID = "539587337";
    private final BusinessCardRepository businessCardRepository;

    public GoogleAnalyticsDTO.Response getCardViewStatistics(Long cardId) {
        List<GoogleAnalyticsDTO.DailyViewCount> statList = new ArrayList<>();

        try {
            BusinessCard card = businessCardRepository.findById(cardId)
                    .orElseThrow(() -> new RuntimeException("명함을 찾을 수 없습니다."));
            String publicUrl = card.getPublicUrl();

            String targetPath = "/card/public/card/" + publicUrl;

            ClassPathResource resource = new ClassPathResource("ga-key.json");

            try (InputStream inputStream = resource.getInputStream()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                        .createScoped("https://www.googleapis.com/auth/analytics.readonly");

                BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newBuilder()
                        .setCredentialsProvider(() -> credentials)
                        .build();

                try (BetaAnalyticsDataClient analyticsData = BetaAnalyticsDataClient.create(settings)) {

                    RunReportRequest request = RunReportRequest.newBuilder()
                            .setProperty("properties/" + PROPERTY_ID)
                            .addDateRanges(DateRange.newBuilder().setStartDate("30daysAgo").setEndDate("today"))
                            .addDimensions(Dimension.newBuilder().setName("date")) // 👈 날짜별 조회를 위해 수정!
                            .addMetrics(Metric.newBuilder().setName("screenPageViews"))
                            .setDimensionFilter(FilterExpression.newBuilder()
                                    .setFilter(Filter.newBuilder()
                                            .setFieldName("pagePath")
                                            .setStringFilter(Filter.StringFilter.newBuilder()
                                                    .setMatchType(Filter.StringFilter.MatchType.CONTAINS)
                                                    .setValue(targetPath))))
                            .build();

                    RunReportResponse response = analyticsData.runReport(request);

                    for (Row row : response.getRowsList()) {
                        String date = row.getDimensionValues(0).getValue(); // 날짜 (예: 20260610)
                        long views = Long.parseLong(row.getMetricValues(0).getValue()); // 해당 날짜의 조회수

                        statList.add(new GoogleAnalyticsDTO.DailyViewCount(date, views));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ 구글 애널리틱스 통계 수집 중 예외 발생: " + e.getMessage());
            e.printStackTrace();
        }

        return GoogleAnalyticsDTO.Response.builder()
                .cardId(cardId)
                .viewStats(statList)
                .build();
    }
}