package AIcard.cardapp.service;

import AIcard.cardapp.DTO.AiCardResponse;
import AIcard.cardapp.DTO.CardCreateRequest;
import AIcard.cardapp.DTO.CardDrawingCreateRequest;
import AIcard.cardapp.DTO.CardExtraItemRequest;
import AIcard.cardapp.DTO.CardUpdateTextRequest;
import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.entity.BusinessCardDetail;
import AIcard.cardapp.entity.CardAiResult;
import AIcard.cardapp.entity.CardDailyView;
import AIcard.cardapp.entity.CardLayout;
import AIcard.cardapp.entity.CardLink;
import AIcard.cardapp.entity.Template;
import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.repository.BusinessCardDetailRepository;
import AIcard.cardapp.repository.CardAiResultRepository;
import AIcard.cardapp.repository.CardDailyViewRepository;
import AIcard.cardapp.repository.CardLayoutRepository;
import AIcard.cardapp.repository.CardLinkRepository;
import AIcard.cardapp.repository.TemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AiCardService {

    private static final Long TEST_USER_ID = 1L;
    private static final int MAX_EXTRA_ITEMS = 8;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final BusinessCardRepository businessCardRepository;
    private final BusinessCardDetailRepository businessCardDetailRepository;
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
            BusinessCardDetailRepository businessCardDetailRepository,
            TemplateRepository templateRepository,
            CardAiResultRepository cardAiResultRepository,
            CardLayoutRepository cardLayoutRepository,
            CardLinkRepository cardLinkRepository,
            CardDailyViewRepository cardDailyViewRepository,
            OpenAiCardService openAiCardService,
            HtmlExportService htmlExportService
    ) {
        this.businessCardRepository = businessCardRepository;
        this.businessCardDetailRepository = businessCardDetailRepository;
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
        return generate(request, TEST_USER_ID);
    }

    @Transactional
    public Long generate(CardCreateRequest request, Long userId) {
        if (userId == null) {
            throw new IllegalStateException("로그인 사용자 정보를 찾을 수 없습니다.");
        }
        List<Template> templates = templateRepository.findByActiveTrue();
        if (templates.isEmpty()) {
            throw new IllegalStateException("사용 가능한 명함 템플릿이 없습니다.");
        }
        Template selectedTemplate = chooseTemplateByRequest(templates, request);

        BusinessCard card = new BusinessCard();
        card.setUserId(userId);
        card.setTemplateId(selectedTemplate.getTemplateId());
        card.setTitle(defaultText(request.getDisplayName(), "AI 명함"));
        card.setDisplayName(request.getDisplayName());
        card.setPublicUrl(makePublicUrl());
        card = businessCardRepository.save(card);
        BusinessCardDetail detail = saveDetail(card.getCardId(), request);
        card.setDetail(detail);
        saveExtraItems(card.getCardId(), request.getExtraItems());

        AiCardResponse aiResponse = openAiCardService.generateCardDraft(card, templates, request);
        selectedTemplate = resolveAiSelectedTemplate(templates, aiResponse.getLayoutJson(), selectedTemplate);
        card.setTemplateId(selectedTemplate.getTemplateId());
        card = businessCardRepository.save(card);

        CardAiResult aiResult = new CardAiResult();
        aiResult.setCardId(card.getCardId());
        aiResult.setModelName(defaultValue(aiResponse.getModelName(), model));
        aiResult.setPrompt(aiResponse.getPrompt());
        aiResult.setResultJson(aiResponse.getRawJson());
        aiResult.setGeneratedHtml(aiResponse.getHtml());
        aiResult.setGeneratedCss(aiResponse.getCss());
        aiResult.setSelectedTemplateId(selectedTemplate.getTemplateId());
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
    public Long generateDrawing(CardDrawingCreateRequest request) {
        return generateDrawing(request, TEST_USER_ID);
    }

    @Transactional
    public Long generateDrawing(CardDrawingCreateRequest request, Long userId) {
        CardCreateRequest cardRequest = new CardCreateRequest();
        cardRequest.setApiKey(request.getApiKey());
        cardRequest.setGeminiApiKey(request.getGeminiApiKey());
        cardRequest.setDrawingDescription(request.getDrawingDescription());
        cardRequest.setDrawingLayoutJson(request.getDrawingLayoutJson());
        cardRequest.setDisplayName(defaultValue(request.getDisplayName(), "그림 기반 명함"));
        cardRequest.setJobTitle(request.getJobTitle());
        cardRequest.setCompany(request.getCompany());
        cardRequest.setDepartment(request.getDepartment());
        cardRequest.setIntro(request.getIntro());
        cardRequest.setEmail(request.getEmail());
        cardRequest.setPhone(request.getPhone());
        cardRequest.setExtraItems(request.getExtraItems());
        cardRequest.setMood("그림 또는 스케치 배치를 바탕으로 한 시각적인 명함");
        cardRequest.setPreferredColor("사용자 그림 설명에 어울리는 색상");
        return generate(cardRequest, userId);
    }

    @Transactional
    public void updateText(Long cardId, CardUpdateTextRequest request) {
        BusinessCard card = getCard(cardId);
        card.setDisplayName(request.getDisplayName());
        businessCardRepository.save(card);
        saveDetail(cardId, request);
        saveExtraItems(cardId, request.getExtraItems());
        htmlExportService.exportCard(cardId);
    }

    @Transactional
    public void fixLayout(Long cardId, CardUpdateTextRequest request) {
        BusinessCard card = getCard(cardId);
        card.setDisplayName(request.getDisplayName());
        businessCardRepository.save(card);
        saveDetail(cardId, request);
        saveExtraItems(cardId, request.getExtraItems());

        CardAiResult latest = cardAiResultRepository.findTopByCardIdOrderByCreatedAtDesc(cardId)
                .orElseThrow(() -> new IllegalArgumentException("AI 생성 결과를 찾을 수 없습니다. cardId=" + cardId));
        AiCardResponse fixed = openAiCardService.fixLayoutOnly(
                card,
                latest.getGeneratedHtml(),
                latest.getGeneratedCss(),
                request.getApiKey(),
                request.getGeminiApiKey()
        );

        CardAiResult aiResult = new CardAiResult();
        aiResult.setCardId(cardId);
        aiResult.setModelName(defaultValue(fixed.getModelName(), latest.getModelName()));
        aiResult.setPrompt(fixed.getPrompt());
        aiResult.setResultJson(fixed.getRawJson());
        aiResult.setGeneratedHtml(fixed.getHtml());
        aiResult.setGeneratedCss(fixed.getCss());
        aiResult.setSelectedTemplateId(latest.getSelectedTemplateId());
        aiResult.setAiReason(fixed.getReason());
        cardAiResultRepository.save(aiResult);

        CardLayout layout = cardLayoutRepository.findByCardId(cardId)
                .orElseGet(CardLayout::new);
        layout.setCardId(cardId);
        layout.setLayoutJson(fixed.getLayoutJson());
        cardLayoutRepository.save(layout);

        htmlExportService.exportCard(cardId);
    }

    @Transactional
    public void deleteCard(Long cardId) {
        BusinessCard card = getCard(cardId);
        htmlExportService.deleteExportedCardDirectory(cardId);
        businessCardRepository.delete(card);
    }

    @Transactional(readOnly = true)
    public BusinessCard getCard(Long cardId) {
        BusinessCard card = businessCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("명함을 찾을 수 없습니다. cardId=" + cardId));
        businessCardDetailRepository.findById(cardId).ifPresent(card::setDetail);
        return card;
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
        businessCardDetailRepository.findById(card.getCardId()).ifPresent(card::setDetail);

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

    private BusinessCardDetail saveDetail(Long cardId, CardCreateRequest request) {
        BusinessCardDetail detail = businessCardDetailRepository.findById(cardId)
                .orElseGet(BusinessCardDetail::new);
        detail.setCardId(cardId);
        detail.setJobTitle(request.getJobTitle());
        detail.setCompany(request.getCompany());
        detail.setDepartment(request.getDepartment());
        detail.setIntro(request.getIntro());
        detail.setEmail(request.getEmail());
        detail.setPhone(request.getPhone());
        return businessCardDetailRepository.save(detail);
    }

    private BusinessCardDetail saveDetail(Long cardId, CardUpdateTextRequest request) {
        BusinessCardDetail detail = businessCardDetailRepository.findById(cardId)
                .orElseGet(BusinessCardDetail::new);
        detail.setCardId(cardId);
        detail.setJobTitle(request.getJobTitle());
        detail.setCompany(request.getCompany());
        detail.setDepartment(request.getDepartment());
        detail.setIntro(request.getIntro());
        detail.setEmail(request.getEmail());
        detail.setPhone(request.getPhone());
        return businessCardDetailRepository.save(detail);
    }

    private void saveExtraItems(Long cardId, List<CardExtraItemRequest> extraItems) {
        cardLinkRepository.deleteByCardId(cardId);
        if (extraItems == null || extraItems.isEmpty()) {
            return;
        }

        int sortOrder = 0;
        for (CardExtraItemRequest itemRequest : extraItems) {
            if (sortOrder >= MAX_EXTRA_ITEMS) {
                break;
            }
            if (itemRequest == null || isBlankExtraItem(itemRequest)) {
                continue;
            }

            CardLink item = new CardLink();
            item.setCardId(cardId);
            item.setItemType(defaultType(itemRequest.getItemType()));
            item.setTitle(trimToNull(itemRequest.getTitle()));
            item.setUrl(trimToNull(itemRequest.getUrl()));
            item.setImageUrl(trimToNull(itemRequest.getImageUrl()));
            item.setSortOrder(sortOrder++);
            cardLinkRepository.save(item);
        }
    }

    private boolean isBlankExtraItem(CardExtraItemRequest item) {
        return trimToNull(item.getTitle()) == null
                && trimToNull(item.getUrl()) == null
                && trimToNull(item.getImageUrl()) == null;
    }

    private String defaultType(String itemType) {
        String value = trimToNull(itemType);
        return value == null ? "SKILL" : value;
    }

    private Template resolveAiSelectedTemplate(List<Template> templates, String layoutJson, Template fallback) {
        String selectedCode = extractTemplateCode(layoutJson);
        if (selectedCode == null) {
            return fallback;
        }

        Template aiSelected = templates.stream()
                .filter(template -> selectedCode.equalsIgnoreCase(template.getTemplateCode()))
                .findFirst()
                .orElse(fallback);

        if (isFirstTemplate(templates, aiSelected) && !isFirstTemplate(templates, fallback)) {
            return fallback;
        }

        return aiSelected;
    }

    private String extractTemplateCode(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(layoutJson);
            return trimToNull(root.path("templateCode").asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Template chooseTemplateByRequest(List<Template> templates, CardCreateRequest request) {
        Template fallback = templates.getFirst();
        int modernScore = 0;
        int simpleScore = 0;
        int portfolioScore = 0;

        String text = String.join(" ",
                defaultValue(request.getDisplayName(), ""),
                defaultValue(request.getJobTitle(), ""),
                defaultValue(request.getCompany(), ""),
                defaultValue(request.getDepartment(), ""),
                defaultValue(request.getIntro(), ""),
                defaultValue(request.getMood(), ""),
                defaultValue(request.getPreferredColor(), ""),
                defaultValue(request.getDrawingDescription(), "")
        ).toLowerCase();

        modernScore += score(text, "dark", "tech", "developer", "backend", "frontend", "java", "api", "system", "ai", "navy", "black", "neon", "개발", "백엔드", "프론트", "기술", "어두", "네이비", "블랙");
        simpleScore += score(text, "clean", "white", "bright", "minimal", "student", "academic", "formal", "simple", "깔끔", "밝", "화이트", "흰", "학생", "학교", "대학교", "전공", "단정", "심플");
        portfolioScore += score(text, "portfolio", "project", "creative", "design", "designer", "grid", "visual", "포트폴리오", "프로젝트", "창의", "디자인", "디자이너", "시각", "그림", "배치");

        if (request.getExtraItems() != null) {
            for (CardExtraItemRequest item : request.getExtraItems()) {
                String type = item == null ? "" : defaultValue(item.getItemType(), "").toUpperCase();
                if ("PORTFOLIO".equals(type)) {
                    portfolioScore += 3;
                } else if ("SKILL".equals(type) || "CERTIFICATE".equals(type)) {
                    modernScore += 1;
                }
            }
        }

        if (portfolioScore >= simpleScore && portfolioScore >= modernScore && portfolioScore > 0) {
            return findTemplateByCode(templates, "portfolio_grid", fallback);
        }
        if (simpleScore >= modernScore && simpleScore > 0) {
            return findTemplateByCode(templates, "simple_white", fallback);
        }
        if (modernScore > 0) {
            return findTemplateByCode(templates, "modern_dark", fallback);
        }
        return fallback;
    }

    private int score(String text, String... keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private Template findTemplateByCode(List<Template> templates, String code, Template fallback) {
        return templates.stream()
                .filter(template -> code.equalsIgnoreCase(template.getTemplateCode()))
                .findFirst()
                .orElse(fallback);
    }

    private boolean isFirstTemplate(List<Template> templates, Template template) {
        return template != null
                && !templates.isEmpty()
                && template.getTemplateId() != null
                && template.getTemplateId().equals(templates.getFirst().getTemplateId());
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

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
