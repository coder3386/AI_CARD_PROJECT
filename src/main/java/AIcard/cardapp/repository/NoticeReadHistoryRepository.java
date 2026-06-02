package AIcard.cardapp.repository;

import AIcard.cardapp.entity.NoticeReadHistory; // NoticeReadHistory 엔티티 경로
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeReadHistoryRepository extends JpaRepository<NoticeReadHistory, Long> {
}