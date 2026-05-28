package AIcard.cardapp.repository;

import AIcard.cardapp.entity.CardQr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardQrRepository extends JpaRepository<CardQr, Long> {
    Optional<CardQr> findByCardId(Long cardId);
}
