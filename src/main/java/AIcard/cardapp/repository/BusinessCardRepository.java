package AIcard.cardapp.repository;

import AIcard.cardapp.entity.BusinessCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessCardRepository extends JpaRepository<BusinessCard, Long> {
    Optional<BusinessCard> findByPublicUrl(String publicUrl);

    boolean existsByPublicUrl(String publicUrl);
}
