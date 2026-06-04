package AIcard.cardapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notice_read_history", uniqueConstraints = {
        // 중복 저장 방지를 위한 복합 유니크 키 설정
        @UniqueConstraint(name = "uk_notice_user", columnNames = {"notice_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeReadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    // 복잡한 연관관계 매핑 없이 단순 ID만 저장하여 성능 최적화
    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_at", updatable = false)
    private LocalDateTime readAt;

    // Service에서 저장할 때 사용할 생성자
    public NoticeReadHistory(Long noticeId, Long userId) {
        this.noticeId = noticeId;
        this.userId = userId;
        this.readAt = LocalDateTime.now();
    }
}