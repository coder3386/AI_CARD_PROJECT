package AIcard.cardapp.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "users")
public class UsersMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") // DB의 user_id와 매핑
    private Long id;

    @Column(name = "login_id", unique = true) // DB의 login_id 컬럼 사용
    private String loginId;

    private String password; // DB 컬럼명과 필드명이 같으면 @Column 생략 가능

    private String name;

    private String email;
    private String phone;
    private String role; // ADMIN 또는 USER

    // --- 개인정보 수정 관련 메서드 ---
    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

    public void updatePhone(String newPhone) {
        this.phone = newPhone;
    }

    @Builder
    public UsersMember(String loginId, String password, String name, String email, String phone, String role) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }
}