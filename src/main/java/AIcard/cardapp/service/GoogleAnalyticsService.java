package AIcard.cardapp.service;

import AIcard.cardapp.DTO.GoogleAnalyticsDTO;
import com.google.analytics.data.v1beta.*;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.entity.BusinessCard;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAnalyticsService {

    private final String PROPERTY_ID = "539587337";
    private final BusinessCardRepository businessCardRepository;
    @Value("${google.analytics.key-path}")
    private Resource keyFile;

    public GoogleAnalyticsDTO.Response getCardViewStatistics(Long cardId, String rangeType) {

        List<GoogleAnalyticsDTO.DailyViewCount> statList = new ArrayList<>();

        try {
            BusinessCard card = businessCardRepository.findById(cardId)
                    .orElseThrow(() -> new RuntimeException("명함을 찾을 수 없습니다."));

            String publicUrl = card.getPublicUrl();
            String targetPath = "/card/public/card/" + publicUrl;

            try (InputStream inputStream = keyFile.getInputStream()) {

                GoogleCredentials credentials = GoogleCredentials
                        .fromStream(inputStream)
                        .createScoped("https://www.googleapis.com/auth/analytics.readonly");

                BetaAnalyticsDataSettings settings = BetaAnalyticsDataSettings.newBuilder()
                        .setCredentialsProvider(() -> credentials)
                        .build();

                String startDate;
                String dimensionName;

                switch (rangeType) {
                    case "7days":
                        startDate = "7daysAgo";
                        dimensionName = "date";
                        break;

                    case "1year":
                        startDate = "365daysAgo";
                        dimensionName = "month";
                        break;

                    case "30days":
                    default:
                        startDate = "30daysAgo";
                        dimensionName = "date";
                        break;
                }

                try (BetaAnalyticsDataClient analyticsData = BetaAnalyticsDataClient.create(settings)) {

                    RunReportRequest request = RunReportRequest.newBuilder()
                            .setProperty("properties/" + PROPERTY_ID)
                            .addDateRanges(DateRange.newBuilder().setStartDate(startDate).setEndDate("today"))
                            .addDimensions(Dimension.newBuilder().setName(dimensionName))
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
                        String period = row.getDimensionValues(0).getValue();
                        long views = Long.parseLong(row.getMetricValues(0).getValue());// 해당 날짜의 조회수

                        statList.add(new GoogleAnalyticsDTO.DailyViewCount (period, views));
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