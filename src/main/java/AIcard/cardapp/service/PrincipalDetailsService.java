package AIcard.cardapp.service;

import AIcard.cardapp.DTO.PrincipalDetails;
import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.repository.UsersMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrincipalDetailsService implements UserDetailsService {
    private final UsersMemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // DB의 login_id 컬럼에서 사용자를 찾음
        UsersMember member = memberRepository.findByLoginId(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        // 기존 내역을 지우고, 우리 엔티티를 품은 커스텀 객체를 리턴합니다.
        return new PrincipalDetails(member);
        /*
        // 시큐리티 전용 세션에 담길 유저 정보 생성
        return User.builder()
                .username(member.getLoginId())
                .password(member.getPassword())
                .roles(member.getRole()) // DB에 'USER'라고 적혀있으면 ROLE_USER로 인식
                .build();*/
    }
}
