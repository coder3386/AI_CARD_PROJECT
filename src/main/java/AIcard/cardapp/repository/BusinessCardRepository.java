package AIcard.cardapp.repository;

import AIcard.cardapp.DTO.ManagerCardDTO;
import AIcard.cardapp.entity.BusinessCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BusinessCardRepository extends JpaRepository<BusinessCard, Long> {
    Page<BusinessCard> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<BusinessCard> findByCardIdAndUserId(Long cardId, Long userId);

    Optional<BusinessCard> findByPublicUrl(String publicUrl);

    boolean existsByPublicUrl(String publicUrl);

    @Query("""
            select new AIcard.cardapp.DTO.ManagerCardDTO(
                c.cardId,
                c.userId,
                u.loginId,
                u.name,
                c.title,
                c.displayName,
                c.publicUrl,
                c.publicCard,
                c.status,
                c.viewCount,
                c.createdAt
            )
            from BusinessCard c
            left join c.user u
            order by c.createdAt desc
            """)
    List<ManagerCardDTO> findManagerCardSummaries();

    List<BusinessCard> findByUserId(Long userId);
}
