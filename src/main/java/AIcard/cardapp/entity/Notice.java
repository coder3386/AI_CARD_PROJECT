package AIcard.cardapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 공지 등록 시 자동으로 현재 시간 입력
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // Service에서 저장할 때 사용할 생성자 추가
    public Notice(String title, String content, LocalDateTime expiresAt) {
        this.title = title;
        this.content = content;
        this.expiresAt = expiresAt;
    }
}