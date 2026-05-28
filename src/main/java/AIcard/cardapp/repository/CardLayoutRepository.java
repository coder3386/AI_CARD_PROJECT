package AIcard.cardapp.repository;

import AIcard.cardapp.entity.CardLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardLayoutRepository extends JpaRepository<CardLayout, Long> {
    Optional<CardLayout> findByCardId(Long cardId);
}
