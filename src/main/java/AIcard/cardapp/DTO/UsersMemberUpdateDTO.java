package AIcard.cardapp.DTO;

import lombok.Data;

@Data
public class UsersMemberUpdateDTO {
    private String currentPassword;
    private String password; // 새 비밀번호
    private String phone;    // 새 휴대폰 번호
}
