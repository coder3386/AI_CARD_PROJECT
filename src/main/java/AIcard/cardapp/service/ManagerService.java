package AIcard.cardapp.service;

import AIcard.cardapp.DTO.ActiveUserDTO;
import AIcard.cardapp.DTO.PrincipalDetails;
import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.repository.UsersMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagerService {

    private final UsersMemberRepository usersMemberRepository;
    private final SessionRegistry sessionRegistry;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("MANAGER_LOGGER");

    // 전체 유저 목록 조회
    public List<UsersMember> getAllUsers() {
        return usersMemberRepository.findAll();
    }

    // 유저 권한 변경 변경 (핵심 로직)
    @Transactional
    public void changeUserRole(Long targetUserId, String newRole, String adminLoginId) {
        // 1. 요청을 보낸 관리자의 정보 및 권한 조회
        UsersMember admin = usersMemberRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자입니다."));

        // 2. 권한 체크: MANAGER나 ADMIN이 아니면 예외 발생
        if (!"ADMIN".equals(admin.getRole()) && !"MANAGER".equals(admin.getRole())) {
            log.warn("[MGR-WARN]|권한 부족 사용자의 권한 변경 시도 발견 - 요청자 ID: {}, 대상 유저 PK: {}", adminLoginId, targetUserId);
            throw new SecurityException("권한 변경 자격이 없습니다. (MANAGER 또는 ADMIN만 가능)");
        }

        // 3. 변경할 대상 유저 조회 후 권한 변경
        UsersMember targetUser = usersMemberRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("대상을 찾을 수 없습니다."));

        String oldRole = targetUser.getRole();
        targetUser.updateRole(newRole);

        log.info("[MGR-ACTION]|관리자(ID: {})가 유저(ID: {}, 이름: {})의 권한을 [{}]에서 [{}]로 변경 완료함.",
                admin.getLoginId(),
                targetUser.getLoginId(),
                targetUser.getName(),
                oldRole,
                newRole
        );
    }

    // 현재 접속 중인 사용자 세션 조회
    public List<ActiveUserDTO> getActiveUsers() {
        return sessionRegistry.getAllPrincipals().stream()
                // 만료되지 않은 세션만 필터링
                .filter(principal -> !sessionRegistry.getAllSessions(principal, false).isEmpty())
                .map(principal -> {
                    // 우리가 만든 PrincipalDetails 타입인지 확인
                    if (principal instanceof PrincipalDetails) {
                        UsersMember user = ((PrincipalDetails) principal).getUsersMember();
                        return ActiveUserDTO.builder()
                                .userId(user.getId())
                                .loginId(user.getLoginId())
                                .name(user.getName())
                                .email(user.getEmail())
                                .phone(user.getPhone())
                                .role(user.getRole())
                                .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
