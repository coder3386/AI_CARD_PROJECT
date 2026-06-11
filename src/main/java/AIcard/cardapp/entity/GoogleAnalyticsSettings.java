package AIcard.cardapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "google_analytics_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleAnalyticsSettings {

    @Id
    private Long cardId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "card_id")
    private BusinessCard businessCard;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tracking_id", nullable = false)
    private String trackingId;

    @Column(name = "measurement_id")
    private String measurementId;

    public GoogleAnalyticsSettings(Long cardId, Long userId, String trackingId) {
        this.cardId = cardId;
        this.userId = userId;
        this.trackingId = trackingId;
    }

    public void updateTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getMeasurementId() {
        return measurementId;
    }
}