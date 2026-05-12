package AIcard.cardapp.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data                // Getter, Setter 등을 자동으로 만들어줍니다.
@NoArgsConstructor   // 기본 생성자
@AllArgsConstructor  // 모든 필드를 포함한 생성자
public class GoogleContactDTO {

    private String name;          // 이름 (성+이름)
    private String phoneNumber;   // 전화번호
    private String email;         // 이메일
    private String jobTitle;      // 직함 (예: 팀장, 개발자)
    private String organization;  // 회사명/소속

    // 필요하다면 메모나 웹사이트 주소도 추가할 수 있습니다.
    private String notes;         // 메모 (AI 명함에서 추출한 특징 등)
}
