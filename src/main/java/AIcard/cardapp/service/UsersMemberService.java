package AIcard.cardapp.service;

import AIcard.cardapp.DTO.UsersMemberRequestDTO;
import AIcard.cardapp.DTO.UsersMemberUpdateDTO;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("USER_LOGGER");

    public Long join(UsersMemberRequestDTO dto) {
        UsersMember member = UsersMember.builder()
                .loginId(dto.getLoginid()) // DTO의 username을 login_id로 저장
                .password(passwordEncoder.encode(dto.getPassword())) // 암호화 필수!
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .role("USER")
                .build();
        log.info("[USER-ACTION]|새로운 회원가입 완료. 등록된 ID: {}, 로그인 ID: {}", memberRepository.save(member).getId(), member.getLoginId());
        return memberRepository.save(member).getId();
    }


    // updateMember 메서드의 반환 타입을 void에서 boolean으로 변경하여 성공/실패 여부를 컨트롤러에 알려줍니다.
    @Transactional
    public boolean updateMember(String loginId, UsersMemberUpdateDTO dto) {
        UsersMember member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 기존 비밀번호가 일치하는지 검사 (매우 중요!)
        // matches(입력한 평문 비밀번호, DB에 암호화된 비밀번호)
        if (!passwordEncoder.matches(dto.getCurrentPassword(), member.getPassword())) {
            log.info("[USER-ACTION]|회원정보 수정 실패 (비밀번호 불일치). 시도자 ID: {}", loginId);
            return false; // 비밀번호가 틀리면 false 반환
        }

        // 2. 새 비밀번호 수정 (새 비밀번호를 입력했을 때만 변경)
        boolean isPasswordChanged = false;
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            member.updatePassword(passwordEncoder.encode(dto.getPassword()));
            isPasswordChanged = true;
        }

        // 3. 휴대폰 번호 수정
        member.updatePhone(dto.getPhone());

        log.info("[USER-ACTION]|회원정보 수정 완료. 사용자 ID: {} (비밀번호 변경 여부: {})", loginId, isPasswordChanged);

        return true; // 성공적으로 수정되면 true 반환
    }
}