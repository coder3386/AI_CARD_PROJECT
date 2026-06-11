package AIcard.cardapp.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleContactDTO {

    private String name;          // 이름 (성+이름)
    private String phoneNumber;   // 전화번호
    private String email;         // 이메일
    private String jobTitle;      // 직함 (예: 팀장, 개발자)
    private String organization;  // 회사명/소속
    private String notes;         // 메모 (명함 URL)
}