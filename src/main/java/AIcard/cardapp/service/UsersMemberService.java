package AIcard.cardapp.service;

import AIcard.cardapp.DTO.UsersMemberRequestDTO;
import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.repository.UsersMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class UsersMemberService {

    private final UsersMemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public Long join(UsersMemberRequestDTO dto) {
        UsersMember member = UsersMember.builder()
                .loginId(dto.getLoginid()) // DTO의 username을 login_id로 저장
                .password(passwordEncoder.encode(dto.getPassword())) // 암호화 필수!
                .name(dto.getName())
                .role("USER")
                .build();
        return memberRepository.save(member).getId();
    }


}
