package AIcard.cardapp.repository;

import AIcard.cardapp.DTO.LoginUserDto;
import AIcard.cardapp.entity.UsersMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginUserMemberRepository extends JpaRepository<UsersMember, Long> {

    // 💡 화면 표시용(DTO) 데이터만 조회하는 전용 메서드
    Optional<LoginUserDto> findProjectedByLoginId(String loginId);
}