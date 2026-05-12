package AIcard.cardapp.repository;

import AIcard.cardapp.entity.UsersMember;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface UsersMemberRepository extends JpaRepository<UsersMember, Long> {

    Optional<UsersMember> findByLoginId(String loginId);

}
