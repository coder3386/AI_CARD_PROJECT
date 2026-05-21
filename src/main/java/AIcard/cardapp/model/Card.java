package AIcard.cardapp.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "card") // DB 테이블 이름
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 명함 고유 ID

    @Column(nullable = false)
    private String name; // 이름

    private String position; // 직책

    @Column(nullable = false)
    private String email; // 이메일

    private String phone; // 연락처

    private String imagePath; // 서버에 저장된 이미지 경로
}