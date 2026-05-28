package AIcard.cardapp.repository;

import AIcard.cardapp.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {
    // JpaRepository를 상속받으면 기본적인 CRUD(조회, 저장, 삭제 등) 기능이 자동으로 구현됩니다.
}