package AIcard.cardapp.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ActiveUserDTO {
    private Long userId;
    private String loginId;
    private String name;
    private String email;
    private String phone;
    private String role;
}
