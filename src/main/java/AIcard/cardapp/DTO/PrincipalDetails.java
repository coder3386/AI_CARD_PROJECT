package AIcard.cardapp.DTO;

import AIcard.cardapp.entity.UsersMember;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

@Getter
public class PrincipalDetails extends User {
    // 우리가 만든 엔티티를 통째로 보관합니다.
    private final UsersMember usersMember;

    public PrincipalDetails(UsersMember usersMember) {
        // 부모인 User 클래스 생성자 호출 (권한 앞에 ROLE_을 붙여서 넘겨줍니다)
        super(usersMember.getLoginId(),
                usersMember.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usersMember.getRole())));

        this.usersMember = usersMember;
    }

}
