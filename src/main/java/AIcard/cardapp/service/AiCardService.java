package AIcard.cardapp.service;

import AIcard.cardapp.DTO.AiCardResponse;
import AIcard.cardapp.DTO.CardCreateRequest;
import AIcard.cardapp.DTO.CardExtraItemRequest;
import AIcard.cardapp.DTO.CardUpdateTextRequest;
import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.entity.CardAiResult;
import AIcard.cardapp.entity.CardDailyView;
import AIcard.cardapp.entity.CardLayout;
import AIcard.cardapp.entity.CardLink;
import AIcard.cardapp.entity.Template;
import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.repository.CardAiResultRepository;
import AIcard.cardapp.repository.CardDailyViewRepository;
import AIcard.cardapp.repository.CardLayoutRepository;
import AIcard.cardapp.repository.CardLinkRepository;
import AIcard.cardapp.repository.TemplateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AiCardService {

    private static final Long TEST_USER_ID = 1L;

    private final BusinessCardRepository businessCardRepository;
    private final TemplateRepository templateRepository;
    private final CardAiResultRepository cardAiResultRepository;
    private final CardLayoutRepository cardLayoutRepository;
    private final CardLinkRepository cardLinkRepository;
    private final CardDailyViewRepository cardDailyViewRepository;
    private final OpenAiCardService openAiCardService;
    private final HtmlExportService htmlExportService;

    @Value("${openai.model:gpt-5.4-mini}")
    private String model;

    public AiCardService(
            BusinessCardRepository businessCardRepository,
            TemplateRepository templateRepository,
            CardAiResultRepository cardAiResultRepository,
            CardLayoutRepository cardLayoutRepository,
            CardLinkRepository cardLinkRepository,
            CardDailyViewRepository cardDailyViewRepository,
            OpenAiCardService openAiCardService,
            HtmlExportService htmlExportService
    ) {
        this.businessCardRepository = businessCardRepository;
        this.templateRepository = templateRepository;
        this.cardAiResultRepository = cardAiResultRepository;
        this.cardLayoutRepository = cardLayoutRepository;
        this.cardLinkRepository = cardLinkRepository;
        this.cardDailyViewRepository = cardDailyViewRepository;
        this.openAiCardService = openAiCardService;
        this.htmlExportService = htmlExportService;
    }

    @Transactional
    public Long generate(CardCreateRequest request) {
        List<Template> templates = templateRepository.findByActiveTrue();
        Template selectedTemplate = templates.isEmpty() ? null : templates.getFirst();

        BusinessCard card = new BusinessCard();
        card.setUserId(TEST_USER_ID);
        card.setTemplateId(selectedTemplate == null ? null : selectedTemplate.getTemplateId());
        card.setTitle(defaultText(request.getDisplayName(), "AI 명함"));
        applyCreateRequest(card, request);
        card.setPublicUrl(makePublicUrl());
        card = businessCardRepository.save(card);
        saveExtraItems(card.getCardId(), request.getExtraItems());

        AiCardResponse aiResponse = openAiCardService.generateCardDraft(card, templates, request);

        CardAiResult aiResult = new CardAiResult();
        aiResult.setCardId(card.getCardId());
        aiResult.setModelName(model);
        aiResult.setPrompt(aiResponse.getPrompt());
        aiResult.setResultJson(aiResponse.getRawJson());
        aiResult.setGeneratedHtml(aiResponse.getHtml());
        aiResult.setGeneratedCss(aiResponse.getCss());
        aiResult.setSelectedTemplateId(card.getTemplateId());
        aiResult.setAiReason(aiResponse.getReason());
        cardAiResultRepository.save(aiResult);

        CardLayout layout = new CardLayout();
        layout.setCardId(card.getCardId());
        layout.setLayoutJson(aiResponse.getLayoutJson());
        cardLayoutRepository.save(layout);

        htmlExportService.exportCard(card.getCardId());
        return card.getCardId();
    }

    @Transactional
    public void updateText(Long cardId, CardUpdateTextRequest request) {
        BusinessCard card = getCard(cardId);
        card.setDisplayName(request.getDisplayName());
        card.setJobTitle(request.getJobTitle());
        card.setCompany(request.getCompany());
        card.setDepartment(request.getDepartment());
        card.setIntro(request.getIntro());
        card.setEmail(request.getEmail());
        card.setPhone(request.getPhone());
        businessCardRepository.save(card);
        saveExtraItems(cardId, request.getExtraItems());
        htmlExportService.exportCard(cardId);
    }

    @Transactional(readOnly = true)
    public BusinessCard getCard(Long cardId) {
        return businessCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("명함을 찾을 수 없습니다. cardId=" + cardId));
    }

    @Transactional
    public String getPreviewDocument(Long cardId) {
        return htmlExportService.buildPreviewDocument(cardId);
    }

    @Transactional(readOnly = true)
    public List<CardLink> getExtraItems(Long cardId) {
        return cardLinkRepository.findByCardIdOrderBySortOrderAscLinkIdAsc(cardId);
    }

    @Transactional
    public String readPublicCard(String publicUrl) {
        BusinessCard card = businessCardRepository.findByPublicUrl(publicUrl)
                .orElseThrow(() -> new IllegalArgumentException("공개 명함을 찾을 수 없습니다."));

        if (!"ACTIVE".equals(card.getStatus()) || !Boolean.TRUE.equals(card.getPublicCard())) {
            throw new IllegalStateException("공개되지 않은 명함입니다.");
        }

        card.setViewCount(card.getViewCount() == null ? 1L : card.getViewCount() + 1L);
        businessCardRepository.save(card);

        LocalDate today = LocalDate.now();
        CardDailyView dailyView = cardDailyViewRepository.findByCardIdAndStatDate(card.getCardId(), today)
                .orElseGet(() -> {
                    CardDailyView view = new CardDailyView();
                    view.setCardId(card.getCardId());
                    view.setStatDate(today);
                    view.setViewCount(0);
                    view.setVisitorCount(0);
                    return view;
                });
        dailyView.setViewCount(dailyView.getViewCount() == null ? 1 : dailyView.getViewCount() + 1);
        dailyView.setVisitorCount(dailyView.getVisitorCount() == null ? 1 : dailyView.getVisitorCount() + 1);
        cardDailyViewRepository.save(dailyView);

        String exported = htmlExportService.readExportedDocument(card);
        if (exported.isBlank() || !exported.contains("ai-card-inline-style")) {
            exported = htmlExportService.exportCard(card.getCardId());
        }
        return exported;
    }

    private void applyCreateRequest(BusinessCard card, CardCreateRequest request) {
        card.setDisplayName(request.getDisplayName());
        card.setJobTitle(request.getJobTitle());
        card.setCompany(request.getCompany());
        card.setDepartment(request.getDepartment());
        card.setIntro(request.getIntro());
        card.setEmail(request.getEmail());
        card.setPhone(request.getPhone());
    }

    private void saveExtraItems(Long cardId, List<CardExtraItemRequest> extraItems) {
        cardLinkRepository.deleteByCardId(cardId);
        if (extraItems == null || extraItems.isEmpty()) {
            return;
        }

        int sortOrder = 0;
        for (CardExtraItemRequest itemRequest : extraItems) {
            if (itemRequest == null || isBlankExtraItem(itemRequest)) {
                continue;
            }

            CardLink item = new CardLink();
            item.setCardId(cardId);
            item.setItemType(defaultType(itemRequest.getItemType()));
            item.setTitle(trimToNull(itemRequest.getTitle()));
            item.setUrl(trimToNull(itemRequest.getUrl()));
            item.setDescription(trimToNull(itemRequest.getDescription()));
            item.setImageUrl(trimToNull(itemRequest.getImageUrl()));
            item.setSortOrder(sortOrder++);
            cardLinkRepository.save(item);
        }
    }

    private boolean isBlankExtraItem(CardExtraItemRequest item) {
        return trimToNull(item.getTitle()) == null
                && trimToNull(item.getUrl()) == null
                && trimToNull(item.getDescription()) == null
                && trimToNull(item.getImageUrl()) == null;
    }

    private String defaultType(String itemType) {
        String value = trimToNull(itemType);
        return value == null ? "SKILL" : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String makePublicUrl() {
        String publicUrl;
        do {
            publicUrl = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (businessCardRepository.existsByPublicUrl(publicUrl));
        return publicUrl;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value + " 명함";
    }
}
