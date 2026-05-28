package AIcard.cardapp.service;

import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.repository.BusinessCardRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyCardService {

    private static final int PAGE_SIZE = 10;

    private final BusinessCardRepository businessCardRepository;
    private final HtmlExportService htmlExportService;

    public MyCardService(BusinessCardRepository businessCardRepository, HtmlExportService htmlExportService) {
        this.businessCardRepository = businessCardRepository;
        this.htmlExportService = htmlExportService;
    }

    @Transactional(readOnly = true)
    public Page<BusinessCard> getMyCards(Long userId, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        return businessCardRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public String updatePublicStatus(Long userId, Long cardId, boolean publicCard) {
        BusinessCard card = getOwnedCard(userId, cardId);
        card.setPublicCard(publicCard);
        businessCardRepository.save(card);
        return displayTitle(card);
    }

    @Transactional
    public String deleteMyCard(Long userId, Long cardId) {
        BusinessCard card = getOwnedCard(userId, cardId);
        String title = displayTitle(card);
        htmlExportService.deleteExportedCardDirectory(cardId);
        businessCardRepository.delete(card);
        return title;
    }

    private BusinessCard getOwnedCard(Long userId, Long cardId) {
        if (userId == null) {
            throw new IllegalStateException("로그인 후 이용해주세요.");
        }
        return businessCardRepository.findByCardIdAndUserId(cardId, userId)
                .orElseThrow(() -> new IllegalArgumentException("내 명함을 찾을 수 없습니다. cardId=" + cardId));
    }

    public String displayTitle(BusinessCard card) {
        String title = card.getTitle();
        if (title == null || title.isBlank()) {
            title = card.getDisplayName();
        }
        if (title == null || title.isBlank()) {
            return "제목 없는 명함";
        }
        return title.replace("紐낇븿", "명함");
    }
}
