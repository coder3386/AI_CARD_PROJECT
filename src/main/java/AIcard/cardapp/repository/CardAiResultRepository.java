package AIcard.cardapp.repository;

import AIcard.cardapp.entity.CardAiResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardAiResultRepository extends JpaRepository<CardAiResult, Long> {
    Optional<CardAiResult> findTopByCardIdOrderByCreatedAtDesc(Long cardId);
}
