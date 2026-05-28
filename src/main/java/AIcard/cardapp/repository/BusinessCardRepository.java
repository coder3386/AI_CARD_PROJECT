package AIcard.cardapp.repository;

import AIcard.cardapp.entity.BusinessCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessCardRepository extends JpaRepository<BusinessCard, Long> {
    Page<BusinessCard> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<BusinessCard> findByCardIdAndUserId(Long cardId, Long userId);

    Optional<BusinessCard> findByPublicUrl(String publicUrl);

    boolean existsByPublicUrl(String publicUrl);
}
