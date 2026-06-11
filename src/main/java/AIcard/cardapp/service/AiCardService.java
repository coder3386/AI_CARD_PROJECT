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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AiCardService {

    private static final Long TEST_USER_ID = 1L;
    private static final org.slf4j.Logger userLog = org.slf4j.LoggerFactory.getLogger("USER_LOGGER");
    private static final int MAX_EXTRA_ITEMS = 8;
    private static final int MAX_DISPLAY_NAME_LENGTH = 15;
    private static final int MAX_JOB_TITLE_LENGTH = 20;
    private static final int MAX_COMPANY_LENGTH = 25;
    private static final int MAX_DEPARTMENT_LENGTH = 25;
    private static final int MAX_INTRO_LENGTH = 50;
    private static final int MAX_EMAIL_LENGTH = 50;
    private static final int MAX_PHONE_LENGTH = 20;
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
            throw new IllegalStateException("濡쒓렇???ъ슜???뺣낫瑜?李얠쓣 ???놁뒿?덈떎.");
        }
        validatePersonalInfo(request);
        List<Template> templates = templateRepository.findByActiveTrue();
        if (templates.isEmpty()) {
            throw new IllegalStateException("?ъ슜 媛?ν븳 紐낇븿 ?쒗뵆由우씠 ?놁뒿?덈떎.");
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
        if (userId == null) {
            throw new IllegalStateException("濡쒓렇???ъ슜???뺣낫瑜?李얠쓣 ???놁뒿?덈떎.");
        }
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
        cardRequest.setProfileImage(request.getProfileImage());
        cardRequest.setExtraItems(request.getExtraItems());
        cardRequest.setMood("그림 또는 스케치 배치를 바탕으로 한 시각적인 명함");
        cardRequest.setPreferredColor("사용자 그림 설명에 어울리는 색상");
        return generateDrawingCustom(cardRequest, userId);
    }

    private Long generateDrawingCustom(CardCreateRequest cardRequest, Long userId) {
        validatePersonalInfo(cardRequest);
        Template drawingTemplate = getDrawingCustomTemplate();

        BusinessCard card = new BusinessCard();
        card.setUserId(userId);
        card.setTemplateId(drawingTemplate.getTemplateId());
        card.setTitle(defaultText(cardRequest.getDisplayName(), "Drawing Card"));
        card.setDisplayName(cardRequest.getDisplayName());
        card.setPublicUrl(makePublicUrl());
        card = businessCardRepository.save(card);

        BusinessCardDetail detail = saveDetail(card.getCardId(), cardRequest);
        card.setDetail(detail);
        saveExtraItems(card.getCardId(), cardRequest.getExtraItems());

        AiCardResponse aiResponse = openAiCardService.generateDrawingCardDraft(card, cardRequest);

        CardAiResult aiResult = new CardAiResult();
        aiResult.setCardId(card.getCardId());
        aiResult.setModelName(defaultValue(aiResponse.getModelName(), model));
        aiResult.setPrompt(aiResponse.getPrompt());
        aiResult.setResultJson(aiResponse.getRawJson());
        aiResult.setGeneratedHtml(aiResponse.getHtml());
        aiResult.setGeneratedCss(aiResponse.getCss());
        aiResult.setSelectedTemplateId(drawingTemplate.getTemplateId());
        aiResult.setAiReason(aiResponse.getReason());
        cardAiResultRepository.save(aiResult);

        CardLayout layout = new CardLayout();
        layout.setCardId(card.getCardId());
        layout.setLayoutJson(defaultValue(cardRequest.getDrawingLayoutJson(), aiResponse.getLayoutJson()));
        cardLayoutRepository.save(layout);

        htmlExportService.exportCard(card.getCardId());
        return card.getCardId();
    }

    @Transactional
    public void updateText(Long cardId, CardUpdateTextRequest request) {
        validatePersonalInfo(request);
        BusinessCard card = getCard(cardId);
        card.setDisplayName(request.getDisplayName());
        businessCardRepository.save(card);
        BusinessCardDetail detail = saveDetail(cardId, request);
        card.setDetail(detail);
        saveExtraItems(cardId, request.getExtraItems());
        htmlExportService.exportCard(cardId);
    }

    @Transactional
    public void fixLayout(Long cardId, CardUpdateTextRequest request) {
        validatePersonalInfo(request);
        BusinessCard card = getCard(cardId);
        card.setDisplayName(request.getDisplayName());
        businessCardRepository.save(card);
        BusinessCardDetail detail = saveDetail(cardId, request);
        card.setDetail(detail);
        saveExtraItems(cardId, request.getExtraItems());

        CardAiResult latest = cardAiResultRepository.findTopByCardIdOrderByCreatedAtDesc(cardId)
                .orElseThrow(() -> new IllegalArgumentException("AI ?앹꽦 寃곌낵瑜?李얠쓣 ???놁뒿?덈떎. cardId=" + cardId));
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
        String preservedLayoutJson = layout.getLayoutJson();
        layout.setCardId(cardId);
        layout.setLayoutJson(isUserDrawingLayout(preservedLayoutJson) ? preservedLayoutJson : fixed.getLayoutJson());
        cardLayoutRepository.save(layout);

        htmlExportService.exportCard(cardId);
    }

    @Transactional
    public void deleteCard(Long cardId) {
        BusinessCard card = getCard(cardId);
        Long userId = card.getUserId();
        String title = displayCardTitle(card);
        htmlExportService.deleteExportedCardDirectory(cardId);
        businessCardRepository.delete(card);
        userLog.info("[USER-ACTION]|명함 삭제 완료. userId={}, cardId={}, title={}", userId, cardId, title);
    }

    @Transactional(readOnly = true)
    public BusinessCard getCard(Long cardId) {
        BusinessCard card = businessCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("紐낇븿??李얠쓣 ???놁뒿?덈떎. cardId=" + cardId));
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

    @Transactional(readOnly = true)
    public boolean isLatestAiResultFallback(Long cardId) {
        return cardAiResultRepository.findTopByCardIdOrderByCreatedAtDesc(cardId)
                .map(result -> "fallback".equals(result.getModelName()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String getLatestAiResultReason(Long cardId) {
        return cardAiResultRepository.findTopByCardIdOrderByCreatedAtDesc(cardId)
                .map(CardAiResult::getAiReason)
                .filter(reason -> !reason.isBlank())
                .orElse("AI 생성에 실패하여 기본 템플릿을 준비했습니다.");
    }

    @Transactional(readOnly = true)
    public BusinessCardDetail getProfileImageDetail(Long cardId) {
        BusinessCardDetail detail = businessCardDetailRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("프로필 이미지가 없습니다. cardId=" + cardId));
        if (!detail.hasProfileImage()) {
            throw new IllegalArgumentException("프로필 이미지가 없습니다. cardId=" + cardId);
        }
        return detail;
    }

    @Transactional
    public String readPublicCard(String publicUrl) {
        return readPublicCard(publicUrl, null);
    }

    @Transactional
    public String readPublicCard(String publicUrl, Long viewerUserId) {
        BusinessCard card = businessCardRepository.findByPublicUrl(publicUrl)
                .orElseThrow(() -> new IllegalArgumentException("怨듦컻 紐낇븿??李얠쓣 ???놁뒿?덈떎."));
        businessCardDetailRepository.findById(card.getCardId()).ifPresent(card::setDetail);

        if (!canViewCard(card, viewerUserId)) {
            throw new IllegalStateException("怨듦컻?섏? ?딆? 紐낇븿?낅땲??");
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

    private boolean canViewCard(BusinessCard card, Long viewerUserId) {
        if (viewerUserId != null && viewerUserId.equals(card.getUserId())) {
            return true;
        }
        return "ACTIVE".equals(card.getStatus()) && Boolean.TRUE.equals(card.getPublicCard());
    }

    private void validatePersonalInfo(CardCreateRequest request) {
        validateLength("이름", request.getDisplayName(), MAX_DISPLAY_NAME_LENGTH);
        validateLength("직무/직책", request.getJobTitle(), MAX_JOB_TITLE_LENGTH);
        validateLength("회사/소속", request.getCompany(), MAX_COMPANY_LENGTH);
        validateLength("부서/전공", request.getDepartment(), MAX_DEPARTMENT_LENGTH);
        validateLength("자기소개", request.getIntro(), MAX_INTRO_LENGTH);
        validateLength("이메일", request.getEmail(), MAX_EMAIL_LENGTH);
        validateLength("전화번호", request.getPhone(), MAX_PHONE_LENGTH);
    }

    private void validatePersonalInfo(CardUpdateTextRequest request) {
        validateLength("이름", request.getDisplayName(), MAX_DISPLAY_NAME_LENGTH);
        validateLength("직무/직책", request.getJobTitle(), MAX_JOB_TITLE_LENGTH);
        validateLength("회사/소속", request.getCompany(), MAX_COMPANY_LENGTH);
        validateLength("부서/전공", request.getDepartment(), MAX_DEPARTMENT_LENGTH);
        validateLength("자기소개", request.getIntro(), MAX_INTRO_LENGTH);
        validateLength("이메일", request.getEmail(), MAX_EMAIL_LENGTH);
        validateLength("전화번호", request.getPhone(), MAX_PHONE_LENGTH);
    }

    private void validateLength(String label, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalStateException(label + "은(는) " + maxLength + "자 이하로 입력해주세요.");
        }
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
        saveProfileImageIfPresent(detail, request.getProfileImage());
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
        saveProfileImageIfPresent(detail, request.getProfileImage());
        return businessCardDetailRepository.save(detail);
    }

    private void saveProfileImageIfPresent(BusinessCardDetail detail, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalStateException("?꾨줈???ъ쭊? ?대?吏 ?뚯씪留??낅줈?쒗븷 ???덉뒿?덈떎.");
        }

        try {
            detail.setProfileImage(file.getBytes());
            detail.setProfileImageContentType(contentType);
            detail.setProfileImageFilename(trimToNull(file.getOriginalFilename()));
        } catch (IOException ex) {
            throw new IllegalStateException("프로필 사진 저장에 실패했습니다.", ex);
        }
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

    private boolean isUserDrawingLayout(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(layoutJson);
            JsonNode elements = root.path("elements");
            return root.has("canvasWidth")
                    && root.has("canvasHeight")
                    && elements.isArray()
                    && elements.size() > 0;
        } catch (Exception ignored) {
            return false;
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

        modernScore += score(text, "dark", "tech", "developer", "backend", "frontend", "java", "api", "system", "ai", "navy", "black", "neon", "개발", "백엔드", "프론트", "기술", "어두", "다크", "블랙");
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

    private Template getDrawingCustomTemplate() {
        return templateRepository.findByTemplateCode("drawing_custom")
                .orElseGet(() -> {
                    List<Template> templates = templateRepository.findByActiveTrue();
                    if (templates.isEmpty()) {
                        throw new IllegalStateException("?ъ슜 媛?ν븳 紐낇븿 ?쒗뵆由우씠 ?놁뒿?덈떎.");
                    }
                    return templates.getFirst();
                });
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

    @Transactional(readOnly = true)
    public BusinessCard getCardByUrl(String publicUrl) {
        return businessCardRepository.findByPublicUrl(publicUrl)
                .orElseThrow(() -> new IllegalArgumentException("해당 URL의 명함을 찾을 수 없습니다."));
    }

    public List<BusinessCard> getCardsByUserId(Long userId) {
        return businessCardRepository.findByUserId(userId);
    }
    
    private String displayCardTitle(BusinessCard card) {
        if (card.getTitle() != null && !card.getTitle().isBlank()) {
            return card.getTitle();
        }
        if (card.getDisplayName() != null && !card.getDisplayName().isBlank()) {
            return card.getDisplayName();
        }
        return "card-" + card.getCardId();
    }
}
