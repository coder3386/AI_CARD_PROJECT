package AIcard.cardapp.repository;

import AIcard.cardapp.entity.CardLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardLinkRepository extends JpaRepository<CardLink, Long> {
    List<CardLink> findByCardIdOrderBySortOrderAscLinkIdAsc(Long cardId);

    void deleteByCardId(Long cardId);
}
