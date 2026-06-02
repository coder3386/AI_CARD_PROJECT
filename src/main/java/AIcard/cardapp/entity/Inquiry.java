package AIcard.cardapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Inquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; // 작성자 ID
    private String title;
    private String content;
    private String answer;

    @Enumerated(EnumType.STRING)
    private InquiryStatus status; // WAITING, COMPLETED

    private LocalDateTime createdAt;
}
