package AIcard.cardapp.service;

import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.repository.UsersMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagerService {

    private final UsersMemberRepository usersMemberRepository;

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
            throw new SecurityException("권한 변경 자격이 없습니다. (MANAGER 또는 ADMIN만 가능)");
        }

        // 3. 변경할 대상 유저 조회 후 권한 변경
        UsersMember targetUser = usersMemberRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("대상을 찾을 수 없습니다."));

        targetUser.updateRole(newRole);
    }
}
