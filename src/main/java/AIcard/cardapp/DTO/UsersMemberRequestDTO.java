package AIcard.cardapp.DTO;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsersMemberRequestDTO {
    private String loginid;
    private String password;
    private String name;
    private String email;
    private String phone;
}
