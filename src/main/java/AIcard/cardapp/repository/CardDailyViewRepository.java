package AIcard.cardapp.repository;

import AIcard.cardapp.entity.CardDailyView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CardDailyViewRepository extends JpaRepository<CardDailyView, Long> {
    Optional<CardDailyView> findByCardIdAndStatDate(Long cardId, LocalDate statDate);
}
