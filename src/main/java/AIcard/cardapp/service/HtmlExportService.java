package AIcard.cardapp.service;

import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.entity.CardAiResult;
import AIcard.cardapp.entity.CardLayout;
import AIcard.cardapp.entity.CardLink;
import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.repository.CardAiResultRepository;
import AIcard.cardapp.repository.CardLayoutRepository;
import AIcard.cardapp.repository.CardLinkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class HtmlExportService {

    private static final int CARD_WIDTH = 860;
    private static final int CARD_HEIGHT = 480;
    private static final int SAFE_MARGIN = 36;
    private static final int EXTRA_AREA_WIDTH = 360;
    private static final int EXTRA_AREA_HEIGHT = 178;
    private static final int LINK_AREA_HEIGHT = 96;
    private static final String[] CARD_CHILD_IDS = {
            "nameText",
            "jobText",
            "companyText",
            "departmentText",
            "introText",
            "emailText",
            "phoneText",
            "profileImage",
            "portfolioArea",
            "linkArea"
    };

    private final BusinessCardRepository businessCardRepository;
    private final CardAiResultRepository cardAiResultRepository;
    private final CardLayoutRepository cardLayoutRepository;
    private final CardLinkRepository cardLinkRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.generated-card-dir:generated-cards}")
    private String generatedCardDir;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public HtmlExportService(
            BusinessCardRepository businessCardRepository,
            CardAiResultRepository cardAiResultRepository,
            CardLayoutRepository cardLayoutRepository,
            CardLinkRepository cardLinkRepository
    ) {
        this.businessCardRepository = businessCardRepository;
        this.cardAiResultRepository = cardAiResultRepository;
        this.cardLayoutRepository = cardLayoutRepository;
        this.cardLinkRepository = cardLinkRepository;
    }

    @Transactional
    public String exportCard(Long cardId) {
        BusinessCard card = businessCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("명함을 찾을 수 없습니다. cardId=" + cardId));
        CardAiResult aiResult = cardAiResultRepository.findTopByCardIdOrderByCreatedAtDesc(cardId)
                .orElseThrow(() -> new IllegalArgumentException("AI 생성 결과를 찾을 수 없습니다. cardId=" + cardId));

        String layoutJson = cardLayoutRepository.findByCardId(cardId)
                .map(CardLayout::getLayoutJson)
                .orElse("");
        boolean userDrawingLayout = isUserDrawingLayoutJson(layoutJson);
        String templateCode = extractTemplateCode(layoutJson);

        String htmlFragment;
        String css;
        if (userDrawingLayout) {
            htmlFragment = removeStandaloneExtraItems(aiResult.getGeneratedHtml(), card.getCardId());
            htmlFragment = removeAiGeneratedExtraPanels(htmlFragment, card.getCardId());
            htmlFragment = repairMalformedClosingTags(htmlFragment);
            htmlFragment = keepRequiredElementsInsideCardRoot(htmlFragment);
            htmlFragment = ensureRequiredElementsExist(htmlFragment, card);
            htmlFragment = applyBusinessText(htmlFragment, card);
            htmlFragment = keepExtraAreaInlineStylesInsideCard(htmlFragment);
            css = removeAiGeneratedExtraPseudoContent(aiResult.getGeneratedCss());
            css = keepExtraAreasInsideCard(css);
        } else {
            htmlFragment = buildStableTextCardHtml(templateCode);
            htmlFragment = applyBusinessText(htmlFragment, card);
            css = buildStableTextCardCss(templateCode, layoutJson);
        }
        htmlFragment = applyExtraItems(htmlFragment, card.getCardId());
        htmlFragment = keepRequiredElementsInsideCardRoot(htmlFragment);
        htmlFragment = applyLayoutJsonStyles(htmlFragment, layoutJson);
        htmlFragment = applyRequiredElementLayoutFallbacks(htmlFragment, css, userDrawingLayout);
        String fullHtml = wrapFullHtml(htmlFragment, css);

        Path cardDirectory = Path.of(generatedCardDir, "card_" + cardId);
        Path htmlPath = cardDirectory.resolve("index.html");
        Path cssPath = cardDirectory.resolve("style.css");

        try {
            Files.createDirectories(cardDirectory);
            Files.writeString(htmlPath, fullHtml, StandardCharsets.UTF_8);
            Files.writeString(cssPath, css == null ? "" : css, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("명함 HTML 파일 생성에 실패했습니다.", ex);
        }

        String normalizedDirectory = cardDirectory.toString().replace("\\", "/") + "/";
        String normalizedHtmlPath = htmlPath.toString().replace("\\", "/");
        card.setOutputPath(normalizedDirectory);
        card.setOutputHtml(normalizedHtmlPath);
        card.setLastExportedAt(LocalDateTime.now());
        businessCardRepository.save(card);
        return fullHtml;
    }

    public String readExportedDocument(BusinessCard card) {
        if (card.getOutputHtml() == null || card.getOutputHtml().isBlank()) {
            return "";
        }

        Path htmlPath = Path.of(card.getOutputHtml());
        try {
            if (Files.exists(htmlPath)) {
                return Files.readString(htmlPath, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            return "";
        }
        return "";
    }

    public String buildPreviewDocument(Long cardId) {
        Optional<BusinessCard> card = businessCardRepository.findById(cardId);
        if (card.isEmpty()) {
            return "";
        }
        String exported = readExportedDocument(card.get());
        String layoutJson = cardLayoutRepository.findByCardId(card.get().getCardId())
                .map(CardLayout::getLayoutJson)
                .orElse("");
        boolean needsStableTextExport = !isUserDrawingLayoutJson(layoutJson)
                && (!exported.contains("card-safe-layout") || !exported.contains("card-safe-layout-v2"));
        if (!exported.isBlank() && exported.contains("ai-card-inline-style")
                && !hasAiGeneratedExtraPseudoContent(exported)
                && !needsStableTextExport
                && !needsExportRepair(exported)) {
            return exported;
        }
        return exportCard(cardId);
    }

    public void deleteExportedCardDirectory(Long cardId) {
        Path cardDirectory = Path.of(generatedCardDir, "card_" + cardId).normalize();
        try {
            if (!Files.exists(cardDirectory)) {
                return;
            }
            try (var paths = Files.walk(cardDirectory)) {
                paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ex) {
                                throw new IllegalStateException("명함 파일 삭제에 실패했습니다. path=" + path, ex);
                            }
                        });
            }
        } catch (IOException ex) {
            throw new IllegalStateException("명함 폴더 삭제에 실패했습니다. cardId=" + cardId, ex);
        }
    }

    private String wrapFullHtml(String bodyHtml, String css) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>AI Digital Business Card</title>
                  <link rel="stylesheet" href="style.css">
                  <style id="ai-card-inline-style">
                  %s
                  %s
                  </style>
                </head>
                <body>
                %s
                %s
                %s
                </body>
                </html>
                """.formatted(
                css == null ? "" : css,
                extraCardCss(),
                bodyHtml == null ? "" : bodyHtml,
                extraCardModal(),
                extraCardScript()
        );
    }

    private String buildStableTextCardHtml(String templateCode) {
        String templateClass = "card-safe-" + normalizeTemplateCode(templateCode).replace("_", "-");
        return """
                <div id="cardRoot" class="card-root card-safe-layout card-safe-color card-safe-layout-v2 %s">
                  <div class="card-safe-grid">
                    <section class="card-safe-identity" aria-label="identity">
                      <div id="nameText" class="card-safe-name editable"></div>
                      <div id="jobText" class="card-safe-job editable"></div>
                      <div id="introText" class="card-safe-intro editable"></div>
                    </section>
                    <section class="card-safe-side" aria-label="profile and contact">
                      <div id="profileImage" class="card-safe-profile" aria-hidden="true"></div>
                      <div class="card-safe-meta">
                        <div id="companyText" class="card-safe-row editable"></div>
                        <div id="departmentText" class="card-safe-row editable"></div>
                        <div id="emailText" class="card-safe-row editable"></div>
                        <div id="phoneText" class="card-safe-row editable"></div>
                      </div>
                    </section>
                    <div id="portfolioArea" class="card-safe-extra"></div>
                    <div id="linkArea" class="card-safe-link"></div>
                  </div>
                </div>
                """.formatted(templateClass);
    }

    private String buildStableTextCardCss(String templateCode, String layoutJson) {
        String code = normalizeTemplateCode(templateCode);
        String accentColor = extractCssColor(layoutJson, "pointColor");
        String backgroundColor = extractCssColor(layoutJson, "backgroundColor");
        String common = """
                #cardRoot.card-safe-layout,
                #cardRoot.card-safe-layout * {
                  box-sizing: border-box;
                  word-break: keep-all;
                  overflow-wrap: anywhere;
                }
                #cardRoot.card-safe-layout {
                  width: 860px;
                  height: 480px;
                  padding: 36px;
                  position: relative;
                  overflow: hidden;
                  border-radius: 24px;
                  font-family: Arial, 'Noto Sans KR', sans-serif;
                }
                #cardRoot.card-safe-layout .card-safe-grid {
                  width: 100%;
                  height: 100%;
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) 360px;
                  grid-template-rows: 202px 178px;
                  column-gap: 32px;
                  row-gap: 20px;
                  position: relative;
                  z-index: 1;
                }
                #cardRoot.card-safe-layout .card-safe-identity {
                  grid-column: 1;
                  grid-row: 1;
                  min-width: 0;
                  display: flex;
                  flex-direction: column;
                  align-items: flex-start;
                  justify-content: flex-start;
                }
                #cardRoot.card-safe-layout .card-safe-name {
                  max-width: 100%;
                  font-size: 40px;
                  line-height: 1.08;
                  font-weight: 900;
                  letter-spacing: 0;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: nowrap;
                }
                #cardRoot.card-safe-layout .card-safe-job {
                  margin-top: 8px;
                  max-width: 100%;
                  font-size: 18px;
                  line-height: 1.3;
                  font-weight: 800;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: nowrap;
                }
                #cardRoot.card-safe-layout .card-safe-intro {
                  margin-top: 16px;
                  width: min(100%, 420px);
                  max-height: 88px;
                  font-size: 15px;
                  line-height: 1.55;
                  overflow: hidden;
                  display: -webkit-box;
                  -webkit-line-clamp: 3;
                  -webkit-box-orient: vertical;
                }
                #cardRoot.card-safe-layout .card-safe-side {
                  grid-column: 2;
                  grid-row: 1;
                  min-width: 0;
                  display: grid;
                  grid-template-columns: 132px minmax(0, 1fr);
                  gap: 14px;
                  align-items: start;
                }
                #cardRoot.card-safe-layout .card-safe-profile {
                  width: 132px;
                  height: 132px;
                  border-radius: 20px;
                  overflow: hidden;
                }
                #cardRoot.card-safe-layout .card-safe-meta {
                  min-width: 0;
                  display: grid;
                  gap: 7px;
                }
                #cardRoot.card-safe-layout .card-safe-row {
                  min-width: 0;
                  min-height: 32px;
                  max-height: 36px;
                  padding: 7px 10px;
                  border-radius: 10px;
                  display: flex;
                  align-items: center;
                  font-size: 12.5px;
                  line-height: 1.25;
                  overflow: hidden;
                }
                #cardRoot.card-safe-layout #portfolioArea {
                  grid-column: 1;
                  grid-row: 2;
                  width: 360px;
                  height: 178px;
                  align-self: start;
                  justify-self: start;
                  overflow: hidden;
                }
                #cardRoot.card-safe-layout #linkArea {
                  display: none;
                }
                """;

        return common + switch (code) {
            case "simple_white" -> """
                    #cardRoot.card-safe-simple-white {
                      background: linear-gradient(180deg, #fffaf0 0%%, #ffffff 62%%, #f8fafc 100%%);
                      color: #1f2937;
                      border: 1px solid #eadcc6;
                      --card-accent: %s;
                    }
                    #cardRoot.card-safe-simple-white .card-safe-grid {
                      grid-template-columns: 176px minmax(0, 1fr);
                      grid-template-rows: 162px 218px;
                      column-gap: 28px;
                      row-gap: 20px;
                    }
                    #cardRoot.card-safe-simple-white .card-safe-identity {
                      grid-column: 2;
                      grid-row: 1;
                      padding: 8px 0 0;
                      border-bottom: 1px solid color-mix(in srgb, var(--card-accent) 26%%, #eadcc6);
                    }
                    #cardRoot.card-safe-simple-white .card-safe-name {
                      font-size: 38px;
                      color: #111827;
                    }
                    #cardRoot.card-safe-simple-white .card-safe-job { color: var(--card-accent); }
                    #cardRoot.card-safe-simple-white .card-safe-intro {
                      color: #4b5563;
                      width: min(100%%, 520px);
                      max-height: 58px;
                      -webkit-line-clamp: 2;
                    }
                    #cardRoot.card-safe-simple-white .card-safe-side {
                      grid-column: 1;
                      grid-row: 1 / span 2;
                      display: flex;
                      flex-direction: column;
                      gap: 12px;
                    }
                    #cardRoot.card-safe-simple-white .card-safe-profile {
                      width: 146px;
                      height: 146px;
                      background: #ffffff;
                      border: 1px solid var(--card-accent);
                      box-shadow: 0 10px 24px rgba(15, 23, 42, .06);
                    }
                    #cardRoot.card-safe-simple-white .card-safe-meta {
                      gap: 8px;
                    }
                    #cardRoot.card-safe-simple-white .card-safe-row {
                      color: #374151;
                      background: rgba(255,255,255,.88);
                      border: 1px solid color-mix(in srgb, var(--card-accent) 35%%, #e7e1d8);
                    }
                    #cardRoot.card-safe-simple-white #portfolioArea {
                      grid-column: 2;
                      grid-row: 2;
                      border-radius: 18px;
                      border: 1px solid color-mix(in srgb, var(--card-accent) 42%%, #e2d8c8);
                      background: rgba(255,255,255,.78);
                      box-shadow: 0 18px 40px rgba(120, 90, 50, .09);
                    }
                    """.formatted(defaultText(accentColor, "#8a5a2b"));
            case "portfolio_grid" -> """
                    #cardRoot.card-safe-portfolio-grid {
                      background: linear-gradient(135deg, #fff7ed 0%%, %s 48%%, #ecfeff 100%%);
                      color: #172554;
                      border: 1px solid #bae6fd;
                      --card-accent: %s;
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-grid {
                      grid-template-columns: 360px minmax(0, 1fr);
                      grid-template-rows: 178px 202px;
                      column-gap: 28px;
                      row-gap: 20px;
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-identity {
                      grid-column: 1;
                      grid-row: 1;
                      padding: 20px;
                      border-radius: 22px;
                      background: rgba(255,255,255,.72);
                      border: 1px solid color-mix(in srgb, var(--card-accent) 24%%, rgba(14, 116, 144, .18));
                      box-shadow: 0 14px 32px rgba(14, 116, 144, .08);
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-name {
                      font-size: 34px;
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-job { color: var(--card-accent); }
                    #cardRoot.card-safe-portfolio-grid .card-safe-intro {
                      color: #334155;
                      max-height: 48px;
                      -webkit-line-clamp: 2;
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-side {
                      grid-column: 2;
                      grid-row: 1 / span 2;
                      display: flex;
                      flex-direction: column;
                      gap: 14px;
                      padding: 18px;
                      border-radius: 22px;
                      background: rgba(255,255,255,.58);
                      border: 1px solid rgba(14, 116, 144, .14);
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-profile {
                      width: 150px;
                      height: 150px;
                      background: rgba(255,255,255,.9);
                      border: 1px solid color-mix(in srgb, var(--card-accent) 40%%, rgba(14, 116, 144, .24));
                      box-shadow: 0 14px 32px rgba(14, 116, 144, .12);
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-meta {
                      grid-template-columns: 1fr 1fr;
                      gap: 8px;
                    }
                    #cardRoot.card-safe-portfolio-grid .card-safe-row {
                      color: #164e63;
                      background: rgba(255,255,255,.72);
                      border: 1px solid color-mix(in srgb, var(--card-accent) 32%%, rgba(14, 116, 144, .18));
                    }
                    #cardRoot.card-safe-portfolio-grid #portfolioArea {
                      grid-column: 1;
                      grid-row: 2;
                      border-radius: 20px;
                      border: 1px solid color-mix(in srgb, var(--card-accent) 45%%, rgba(14, 116, 144, .28));
                      background: rgba(15, 23, 42, .82);
                      box-shadow: 0 20px 42px rgba(15, 23, 42, .16);
                    }
                    """.formatted(defaultText(backgroundColor, "#f0fdf4"), defaultText(accentColor, "#0f766e"));
            default -> """
                    #cardRoot.card-safe-modern-dark {
                      background: linear-gradient(135deg, #07111f 0%%, %s 52%%, #063b3b 100%%);
                      color: #f8fafc;
                      border: 1px solid rgba(56, 189, 248, .22);
                      --card-accent: %s;
                    }
                    #cardRoot.card-safe-modern-dark .card-safe-grid {
                      grid-template-columns: minmax(0, 1fr) 320px;
                      grid-template-rows: 184px 196px;
                      column-gap: 40px;
                      row-gap: 20px;
                    }
                    #cardRoot.card-safe-modern-dark .card-safe-identity {
                      grid-column: 1;
                      grid-row: 1 / span 2;
                      justify-content: center;
                      padding: 0 28px 0 0;
                    }
                    #cardRoot.card-safe-modern-dark .card-safe-name {
                      font-size: 46px;
                    }
                    #cardRoot.card-safe-modern-dark .card-safe-job { color: var(--card-accent); }
                    #cardRoot.card-safe-modern-dark .card-safe-intro {
                      color: #cbd5e1;
                      width: min(100%%, 430px);
                      max-height: 112px;
                      -webkit-line-clamp: 4;
                    }
                    #cardRoot.card-safe-modern-dark .card-safe-side {
                      grid-column: 2;
                      grid-row: 1;
                      display: grid;
                      grid-template-columns: 120px minmax(0, 1fr);
                      gap: 12px;
                      align-items: start;
                    }
                    #cardRoot.card-safe-modern-dark .card-safe-profile {
                      width: 120px;
                      height: 120px;
                      background: rgba(255,255,255,.08);
                      border: 1px solid color-mix(in srgb, var(--card-accent) 38%%, rgba(148, 163, 184, .32));
                      box-shadow: 0 18px 42px rgba(0,0,0,.24);
                    }
                    #cardRoot.card-safe-modern-dark .card-safe-row {
                      color: #e2e8f0;
                      background: rgba(15,23,42,.56);
                      border: 1px solid color-mix(in srgb, var(--card-accent) 28%%, rgba(56,189,248,.16));
                    }
                    #cardRoot.card-safe-modern-dark #portfolioArea {
                      grid-column: 2;
                      grid-row: 2;
                      border-radius: 20px;
                      border: 1px solid color-mix(in srgb, var(--card-accent) 38%%, rgba(56,189,248,.25));
                      background: rgba(2,6,23,.62);
                      box-shadow: 0 20px 44px rgba(0,0,0,.22);
                    }
                    """.formatted(defaultText(backgroundColor, "#0f172a"), defaultText(accentColor, "#67e8f9"));
        };
    }

    private String applyBusinessText(String html, BusinessCard card) {
        String updated = html == null ? "" : html;
        Map<String, String> values = Map.of(
                "nameText", text(card.getDisplayName()),
                "jobText", text(card.getJobTitle()),
                "companyText", text(card.getCompany()),
                "departmentText", text(card.getDepartment()),
                "introText", text(card.getIntro()),
                "emailText", text(card.getEmail()),
                "phoneText", text(card.getPhone())
        );

        for (Map.Entry<String, String> entry : values.entrySet()) {
            updated = replaceElementText(updated, entry.getKey(), HtmlUtils.htmlEscape(entry.getValue()));
        }
        return applyProfileImage(updated, card);
    }

    private String applyProfileImage(String html, BusinessCard card) {
        if (!card.hasProfileImage()) {
            return html;
        }
        String imageUrl = normalizeContextPath() + "/cards/" + card.getCardId() + "/profile-image";
        String imageHtml = "<img class=\"card-profile-uploaded\" src=\"" + attr(imageUrl) + "\" alt=\"profile image\">";
        return replaceElementHtml(html, "profileImage", imageHtml);
    }

    private String ensureRequiredElementsExist(String html, BusinessCard card) {
        String updated = html == null ? "" : html;
        ElementBlock cardRoot = findElementById(updated, "cardRoot", 0);
        if (cardRoot == null) {
            return updated;
        }

        List<String> missingElements = new ArrayList<>();
        if (card.hasProfileImage() && findElementById(updated, "profileImage", 0) == null) {
            missingElements.add("""
                    <div id="profileImage" class="card-profile-fallback" style="position:absolute; right:48px; top:42px; width:140px; height:140px; border-radius:24px; overflow:hidden; z-index:3;"></div>
                    """.strip());
        }
        if (findElementById(updated, "portfolioArea", 0) == null) {
            missingElements.add("""
                    <div id="portfolioArea" class="card-portfolio" style="position:absolute; right:48px; bottom:36px; width:360px; height:178px;"></div>
                    """.strip());
        }
        if (findElementById(updated, "linkArea", 0) == null) {
            missingElements.add("""
                    <div id="linkArea" class="card-link" style="display:none;"></div>
                    """.strip());
        }
        if (missingElements.isEmpty()) {
            return updated;
        }

        String insertion = "\n" + String.join("\n", missingElements) + "\n";
        return updated.substring(0, cardRoot.closeStart())
                + insertion
                + updated.substring(cardRoot.closeStart());
    }

    private String keepRequiredElementsInsideCardRoot(String html) {
        String updated = html == null ? "" : html;
        ElementBlock cardRoot = findElementById(updated, "cardRoot", 0);
        if (cardRoot == null) {
            return updated;
        }

        List<ElementBlock> misplacedElements = new ArrayList<>();
        for (String id : CARD_CHILD_IDS) {
            misplacedElements.addAll(findElementsOutsideCardRoot(updated, id, cardRoot));
        }
        if (misplacedElements.isEmpty()) {
            return updated;
        }

        misplacedElements.sort((left, right) -> Integer.compare(right.start(), left.start()));
        List<String> movedHtml = new ArrayList<>();
        for (ElementBlock element : misplacedElements) {
            movedHtml.add(0, element.html());
            updated = updated.substring(0, element.start()) + updated.substring(element.end());
        }

        ElementBlock updatedCardRoot = findElementById(updated, "cardRoot", 0);
        if (updatedCardRoot == null) {
            return updated;
        }

        String insertion = "\n" + String.join("\n", movedHtml) + "\n";
        return updated.substring(0, updatedCardRoot.closeStart())
                + insertion
                + updated.substring(updatedCardRoot.closeStart());
    }

    private String applyLayoutJsonStyles(String html, String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return html;
        }

        try {
            JsonNode root = objectMapper.readTree(layoutJson);
            if (!isUserDrawingLayoutJson(root)) {
                return html;
            }
            JsonNode elements = root.path("elements");
            if (!elements.isArray()) {
                return html;
            }

            int sourceWidth = positiveOrDefault(root.path("canvasWidth").asInt(CARD_WIDTH), CARD_WIDTH);
            int sourceHeight = positiveOrDefault(root.path("canvasHeight").asInt(CARD_HEIGHT), CARD_HEIGHT);
            String updated = html == null ? "" : html;
            List<String> layoutRoles = new ArrayList<>();
            for (JsonNode element : elements) {
                String role = text(element.path("role").asText()).trim();
                if (isCardChildRole(role) && !"linkArea".equals(role) && !layoutRoles.contains(role)) {
                    layoutRoles.add(role);
                }
            }
            updated = moveElementsToCardRoot(updated, layoutRoles);

            for (JsonNode element : elements) {
                String role = text(element.path("role").asText()).trim();
                if (!isCardChildRole(role) || "linkArea".equals(role)) {
                    continue;
                }
                if (!element.has("x") || !element.has("y") || !element.has("width") || !element.has("height")) {
                    continue;
                }

                int x = scaleCoordinate(element.path("x").asInt(), sourceWidth, CARD_WIDTH);
                int y = scaleCoordinate(element.path("y").asInt(), sourceHeight, CARD_HEIGHT);
                int width = scaleCoordinate(element.path("width").asInt(), sourceWidth, CARD_WIDTH);
                int height = scaleCoordinate(element.path("height").asInt(), sourceHeight, CARD_HEIGHT);
                LayoutBox box = clampLayoutBox(role, x, y, width, height);
                updated = addInlineStyleToElement(updated, role, buildLayoutStyle(role, box), false);
            }
            return updated;
        } catch (Exception ignored) {
            return html;
        }
    }

    private boolean isUserDrawingLayoutJson(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return false;
        }

        try {
            return isUserDrawingLayoutJson(objectMapper.readTree(layoutJson));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isUserDrawingLayoutJson(JsonNode root) {
        return root != null
                && root.has("canvasWidth")
                && root.has("canvasHeight")
                && root.path("elements").isArray();
    }

    private String extractTemplateCode(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return "";
        }

        try {
            return text(objectMapper.readTree(layoutJson).path("templateCode").asText());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractCssColor(String layoutJson, String fieldName) {
        if (layoutJson == null || layoutJson.isBlank()) {
            return "";
        }

        try {
            return sanitizeCssColor(objectMapper.readTree(layoutJson).path(fieldName).asText());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String sanitizeCssColor(String value) {
        String color = text(value).trim();
        if (Pattern.compile("^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$").matcher(color).matches()) {
            return color.toUpperCase();
        }
        return "";
    }

    private String normalizeTemplateCode(String templateCode) {
        String value = text(templateCode).trim().toLowerCase();
        return switch (value) {
            case "simple_white", "portfolio_grid" -> value;
            default -> "modern_dark";
        };
    }

    private String moveElementsToCardRoot(String html, List<String> roles) {
        if (roles.isEmpty()) {
            return html;
        }

        ElementBlock cardRoot = findElementById(html, "cardRoot", 0);
        if (cardRoot == null) {
            return html;
        }

        List<ElementBlock> roleElements = new ArrayList<>();
        for (String role : roles) {
            ElementBlock element = findElementById(html, role, 0);
            if (element != null && element.start() > cardRoot.openEnd() && element.end() <= cardRoot.closeStart()) {
                roleElements.add(element);
            }
        }
        if (roleElements.isEmpty()) {
            return html;
        }

        roleElements.sort((left, right) -> Integer.compare(right.start(), left.start()));
        List<String> movedHtml = new ArrayList<>();
        String updated = html;
        for (ElementBlock element : roleElements) {
            movedHtml.add(0, element.html());
            updated = updated.substring(0, element.start()) + updated.substring(element.end());
        }

        ElementBlock updatedCardRoot = findElementById(updated, "cardRoot", 0);
        if (updatedCardRoot == null) {
            return updated;
        }
        return updated.substring(0, updatedCardRoot.closeStart())
                + "\n" + String.join("\n", movedHtml) + "\n"
                + updated.substring(updatedCardRoot.closeStart());
    }

    private boolean isCardChildRole(String role) {
        for (String cardChildId : CARD_CHILD_IDS) {
            if (cardChildId.equals(role)) {
                return true;
            }
        }
        return false;
    }

    private int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private int scaleCoordinate(int value, int sourceSize, int targetSize) {
        if (sourceSize <= 0 || sourceSize == targetSize) {
            return value;
        }
        return Math.round(value * (targetSize / (float) sourceSize));
    }

    private LayoutBox clampLayoutBox(String role, int x, int y, int width, int height) {
        int safeWidth = Math.max(minWidth(role), Math.min(width, CARD_WIDTH));
        int safeHeight = Math.max(minHeight(role), Math.min(height, CARD_HEIGHT));
        int safeX = Math.max(0, Math.min(x, CARD_WIDTH - safeWidth));
        int safeY = Math.max(0, Math.min(y, CARD_HEIGHT - safeHeight));
        return new LayoutBox(safeX, safeY, safeWidth, safeHeight);
    }

    private int minWidth(String role) {
        return switch (role) {
            case "profileImage" -> 72;
            case "nameText", "jobText" -> 100;
            case "emailText" -> 210;
            case "phoneText" -> 170;
            case "introText" -> 180;
            case "portfolioArea" -> EXTRA_AREA_WIDTH;
            default -> 80;
        };
    }

    private int minHeight(String role) {
        return switch (role) {
            case "profileImage" -> 72;
            case "nameText", "jobText", "emailText", "phoneText" -> 36;
            case "introText" -> 70;
            case "portfolioArea" -> EXTRA_AREA_HEIGHT;
            default -> 24;
        };
    }

    private String buildLayoutStyle(String role, LayoutBox box) {
        StringBuilder style = new StringBuilder();
        style.append("position:absolute; left:")
                .append(box.x())
                .append("px; top:")
                .append(box.y())
                .append("px; width:")
                .append(box.width())
                .append("px; ");
        style.append("height:").append(box.height()).append("px; overflow:hidden; ");
        if ("emailText".equals(role) || "phoneText".equals(role)) {
            style.append("padding:12px 14px; border-radius:16px; z-index:2; display:flex; align-items:center; ");
        } else if (!"profileImage".equals(role) && !"portfolioArea".equals(role)) {
            style.append("z-index:2; ");
        }
        if ("profileImage".equals(role)) {
            style.append("z-index:3; ");
        }
        return style.toString();
    }

    private String applyRequiredElementLayoutFallbacks(String html, String css, boolean useAbsoluteFallbacks) {
        String updated = html == null ? "" : html;
        if (!useAbsoluteFallbacks) {
            return updated;
        }

        updated = addInlineStyleToElement(updated, "profileImage", "position:absolute; overflow:hidden; z-index:3;", false);

        boolean phoneNeedsFallback = needsLooseContactFallback(updated, css, "phoneText");
        boolean emailNeedsFallback = needsLooseContactFallback(updated, css, "emailText");
        boolean contactNeedsFallback = phoneNeedsFallback || emailNeedsFallback;

        if (phoneNeedsFallback) {
            updated = addInlineStyleToElement(updated, "phoneText",
                    "position:absolute; left:36px; top:344px; width:250px; min-height:44px; padding:12px 14px; border-radius:16px; z-index:2; display:flex; align-items:center;",
                    true);
        }

        if (emailNeedsFallback) {
            updated = addInlineStyleToElement(updated, "emailText",
                    "position:absolute; left:36px; top:396px; width:250px; min-height:44px; padding:12px 14px; border-radius:16px; z-index:2; display:flex; align-items:center;",
                    true);
        }

        ElementBlock company = findElementById(updated, "companyText", 0);
        if (company != null && !isInsideClassBlock(updated, company, "card-meta")
                && !hasPositionRule(css, "companyText")) {
            updated = addInlineStyleToElement(updated, "companyText",
                    "position:absolute; left:36px; top:" + (contactNeedsFallback ? 276 : 386) + "px; width:220px; min-height:18px; font-size:13px; line-height:1.25; z-index:2;",
                    true);
        }

        ElementBlock department = findElementById(updated, "departmentText", 0);
        if (department != null && !isInsideClassBlock(updated, department, "card-meta")
                && !hasPositionRule(css, "departmentText")) {
            updated = addInlineStyleToElement(updated, "departmentText",
                    "position:absolute; left:36px; top:" + (contactNeedsFallback ? 300 : 408) + "px; width:220px; min-height:18px; font-size:12px; line-height:1.25; z-index:2;",
                    true);
        }
        return updated;
    }

    private boolean needsLooseContactFallback(String html, String css, String id) {
        ElementBlock element = findElementById(html, id, 0);
        if (element == null || isInsideClassBlock(html, element, "card-contactGroup")) {
            return false;
        }

        String openTag = html.substring(element.start(), element.openEnd());
        return !hasStyleAttribute(openTag) && !hasPositionRule(css, id);
    }

    private boolean hasPositionRule(String css, String id) {
        Pattern rulePattern = Pattern.compile("(?is)#[a-zA-Z0-9_-]*" + Pattern.quote(id) + "[^{]*\\{(.*?)\\}");
        java.util.regex.Matcher matcher = rulePattern.matcher(css == null ? "" : css);
        while (matcher.find()) {
            String body = matcher.group(1).toLowerCase();
            if (body.contains("position:")
                    || readPixelProperty(body, "left") != null
                    || readPixelProperty(body, "right") != null
                    || readPixelProperty(body, "top") != null
                    || readPixelProperty(body, "bottom") != null) {
                return true;
            }
        }
        return false;
    }

    private String addInlineStyleToElement(String html, String id, String style, boolean onlyWhenNoInlineStyle) {
        ElementBlock element = findElementById(html, id, 0);
        if (element == null) {
            return html;
        }

        String openTag = html.substring(element.start(), element.openEnd());
        if (onlyWhenNoInlineStyle && hasStyleAttribute(openTag)) {
            return html;
        }

        String fixedOpenTag = appendInlineStyle(openTag, style);
        return html.substring(0, element.start())
                + fixedOpenTag
                + html.substring(element.openEnd());
    }

    private String appendInlineStyle(String openTag, String style) {
        Pattern stylePattern = Pattern.compile("(?is)\\bstyle\\s*=\\s*([\"'])(.*?)\\1");
        java.util.regex.Matcher matcher = stylePattern.matcher(openTag);
        if (matcher.find()) {
            String mergedStyle = removePropertiesPresentIn(matcher.group(2).stripTrailing(), style);
            if (!mergedStyle.endsWith(";")) {
                mergedStyle += ";";
            }
            mergedStyle += " " + style;
            return matcher.replaceFirst("style=" + matcher.group(1)
                    + java.util.regex.Matcher.quoteReplacement(mergedStyle)
                    + matcher.group(1));
        }
        int insertAt = openTag.endsWith("/>") ? openTag.length() - 2 : openTag.length() - 1;
        return openTag.substring(0, insertAt)
                + " style=\"" + attr(style) + "\""
                + openTag.substring(insertAt);
    }

    private String removePropertiesPresentIn(String currentStyle, String nextStyle) {
        String updated = currentStyle;
        String[] properties = {
                "position",
                "left",
                "top",
                "right",
                "bottom",
                "width",
                "height",
                "min-height",
                "max-height",
                "overflow",
                "z-index",
                "display",
                "align-items",
                "padding",
                "border-radius"
        };
        String next = nextStyle.toLowerCase();
        for (String property : properties) {
            if (next.contains(property.toLowerCase() + ":")) {
                updated = removeProperty(updated, property);
            }
        }
        return updated.strip();
    }

    private boolean hasStyleAttribute(String openTag) {
        return Pattern.compile("(?is)\\bstyle\\s*=").matcher(openTag).find();
    }

    private boolean isInsideClassBlock(String html, ElementBlock child, String className) {
        Pattern pattern = Pattern.compile("(?is)<([a-zA-Z0-9]+)(?=[^>]*\\bclass\\s*=\\s*([\"'])(?:(?!\\2).)*\\b"
                + Pattern.quote(className)
                + "\\b(?:(?!\\2).)*\\2)[^>]*>");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String openTag = matcher.group();
            if (!hasClass(openTag, className)) {
                continue;
            }
            int closeEnd = findMatchingCloseTag(html, matcher.group(1).toLowerCase(), matcher.end());
            if (closeEnd < 0) {
                continue;
            }
            if (matcher.start() < child.start() && child.end() <= closeEnd) {
                return true;
            }
        }
        return false;
    }

    private boolean hasClass(String openTag, String className) {
        Pattern classPattern = Pattern.compile("(?is)\\bclass\\s*=\\s*([\"'])(.*?)\\1");
        java.util.regex.Matcher matcher = classPattern.matcher(openTag);
        if (!matcher.find()) {
            return false;
        }
        String[] classes = matcher.group(2).trim().split("\\s+");
        for (String classValue : classes) {
            if (className.equals(classValue)) {
                return true;
            }
        }
        return false;
    }

    private List<ElementBlock> findElementsOutsideCardRoot(String html, String id, ElementBlock cardRoot) {
        List<ElementBlock> elements = new ArrayList<>();
        int cursor = 0;
        while (cursor < html.length()) {
            ElementBlock element = findElementById(html, id, cursor);
            if (element == null) {
                break;
            }
            if (element.start() < cardRoot.openEnd() || element.end() > cardRoot.closeStart()) {
                elements.add(element);
            }
            cursor = Math.max(element.end(), cursor + 1);
        }
        return elements;
    }

    private ElementBlock findElementById(String html, String id, int fromIndex) {
        Pattern pattern = Pattern.compile("(?is)<([a-zA-Z0-9]+)(?=[^>]*\\bid=[\"']"
                + Pattern.quote(id) + "[\"'])[^>]*>");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (!matcher.find(fromIndex)) {
            return null;
        }

        String tag = matcher.group(1).toLowerCase();
        int openStart = matcher.start();
        int openEnd = matcher.end();
        String openTag = html.substring(openStart, openEnd);
        if (isVoidElement(tag) || openTag.stripTrailing().endsWith("/>")) {
            return new ElementBlock(openStart, openEnd, openEnd, openEnd, html.substring(openStart, openEnd));
        }

        int closeEnd = findMatchingCloseTag(html, tag, openEnd);
        if (closeEnd < 0) {
            return null;
        }

        int closeStart = html.toLowerCase().lastIndexOf("</" + tag + ">", closeEnd);
        if (closeStart < openEnd) {
            return null;
        }
        return new ElementBlock(openStart, openEnd, closeStart, closeEnd, html.substring(openStart, closeEnd));
    }

    private boolean isVoidElement(String tag) {
        return switch (tag) {
            case "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr" -> true;
            default -> false;
        };
    }

    private String replaceElementText(String html, String id, String value) {
        String idPattern = Pattern.quote(id);
        Pattern pattern = Pattern.compile("(?is)<([a-zA-Z0-9]+)(?=[^>]*\\bid=[\"']" + idPattern + "[\"'])[^>]*>");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        StringBuilder updated = new StringBuilder();
        int cursor = 0;

        while (matcher.find(cursor)) {
            String tag = matcher.group(1);
            int openStart = matcher.start();
            int openEnd = matcher.end();
            int closeEnd = findMatchingCloseTag(html, tag, openEnd);
            if (closeEnd < 0) {
                int nextTag = html.indexOf('<', openEnd);
                if (nextTag < 0) {
                    nextTag = html.length();
                }
                updated.append(html, cursor, openEnd);
                updated.append(value);
                updated.append("</").append(tag).append(">");
                cursor = nextTag;
                continue;
            }

            int closeStart = html.toLowerCase().lastIndexOf("</" + tag.toLowerCase() + ">", closeEnd);
            if (closeStart < openEnd) {
                cursor = openEnd;
                continue;
            }

            updated.append(html, cursor, openEnd);
            updated.append(value);
            updated.append(html, closeStart, closeEnd);
            cursor = closeEnd;
        }

        if (cursor == 0) {
            return html;
        }
        updated.append(html.substring(cursor));
        return updated.toString();
    }

    private String applyExtraItems(String html, Long cardId) {
        List<CardLink> items = cardLinkRepository.findByCardIdOrderBySortOrderAscLinkIdAsc(cardId);

        String updated = replaceElementHtml(html, "portfolioArea", buildPortfolioHtml(items));
        updated = replaceElementHtml(updated, "linkArea", buildLinkHtml(items));
        return updated;
    }

    private String removeAiGeneratedExtraPseudoContent(String css) {
        String updated = css == null ? "" : css;
        String[] selectors = {
                "#portfolioArea::before",
                "#portfolioArea:before",
                "#portfolioArea::after",
                "#portfolioArea:after",
                "#linkArea::before",
                "#linkArea:before",
                "#linkArea::after",
                "#linkArea:after"
        };
        for (String selector : selectors) {
            updated = updated.replaceAll("(?is)" + Pattern.quote(selector) + "\\s*\\{.*?}", "");
        }
        return updated;
    }

    private boolean hasAiGeneratedExtraPseudoContent(String html) {
        String lower = html == null ? "" : html.toLowerCase();
        return lower.contains("#portfolioarea::before")
                || lower.contains("#portfolioarea:before")
                || lower.contains("#portfolioarea::after")
                || lower.contains("#portfolioarea:after")
                || lower.contains("#linkarea::before")
                || lower.contains("#linkarea:before")
                || lower.contains("#linkarea::after")
                || lower.contains("#linkarea:after");
    }

    private String repairMalformedClosingTags(String html) {
        return (html == null ? "" : html).replaceAll("(?i)(?<!<)/div>", "</div>");
    }

    private boolean needsExportRepair(String html) {
        String value = html == null ? "" : html;
        if (Pattern.compile("(?i)(?<!<)/div>").matcher(value).find()) {
            return true;
        }
        if (hasElementWithClassWithoutInlineStyle(value, "profileImage", "card-profile-wrap")) {
            return true;
        }

        ElementBlock company = findElementById(value, "companyText", 0);
        if (company != null && !isInsideClassBlock(value, company, "card-meta")
                && hasElementWithClassWithoutInlineStyle(value, "companyText", "card-meta-item")) {
            return true;
        }

        ElementBlock department = findElementById(value, "departmentText", 0);
        if (department != null && !isInsideClassBlock(value, department, "card-meta")
                && hasElementWithClassWithoutInlineStyle(value, "departmentText", "card-meta-item")) {
            return true;
        }

        ElementBlock phone = findElementById(value, "phoneText", 0);
        if (phone != null && !isInsideClassBlock(value, phone, "card-contactGroup")
                && hasElementWithClassWithoutInlineStyle(value, "phoneText", "card-contact")) {
            return true;
        }

        ElementBlock email = findElementById(value, "emailText", 0);
        if (email != null && !isInsideClassBlock(value, email, "card-contactGroup")
                && hasElementWithClassWithoutInlineStyle(value, "emailText", "card-contact")) {
            return true;
        }

        boolean hasOldMetaFallback = hasFallbackStyle(value, "companyText", "top:386px");
        boolean hasDrawingMetaFallback = hasFallbackStyle(value, "companyText", "top:276px")
                || hasFallbackStyle(value, "departmentText", "top:300px");
        boolean hasContactFallback = hasFallbackStyle(value, "phoneText", "top:344px")
                || hasFallbackStyle(value, "emailText", "top:396px");
        return (hasOldMetaFallback || hasDrawingMetaFallback) && hasContactFallback;
    }

    private boolean hasElementWithClassWithoutInlineStyle(String html, String id, String className) {
        ElementBlock element = findElementById(html, id, 0);
        if (element == null) {
            return false;
        }

        String openTag = html.substring(element.start(), element.openEnd());
        return hasClass(openTag, className) && !hasStyleAttribute(openTag);
    }

    private boolean hasFallbackStyle(String html, String id, String styleFragment) {
        ElementBlock element = findElementById(html, id, 0);
        if (element == null) {
            return false;
        }

        String openTag = html.substring(element.start(), element.openEnd()).replace(" ", "").toLowerCase();
        return openTag.contains(styleFragment.replace(" ", "").toLowerCase());
    }

    private String keepExtraAreaInlineStylesInsideCard(String html) {
        String updated = html == null ? "" : html;
        updated = keepInlineAreaInsideCard(updated, "portfolioArea", EXTRA_AREA_WIDTH, EXTRA_AREA_HEIGHT);
        updated = keepInlineAreaInsideCard(updated, "linkArea", Math.min(EXTRA_AREA_WIDTH, 360), LINK_AREA_HEIGHT);
        return updated;
    }

    private String keepInlineAreaInsideCard(String html, String id, int fallbackWidth, int fallbackHeight) {
        Pattern tagPattern = Pattern.compile("(?is)<([a-zA-Z0-9]+)(?=[^>]*\\bid=[\"']"
                + Pattern.quote(id) + "[\"'])[^>]*>");
        java.util.regex.Matcher matcher = tagPattern.matcher(html);
        StringBuilder updated = new StringBuilder();

        while (matcher.find()) {
            String tag = matcher.group();
            String fixedTag = fixInlineStyleOnTag(tag, fallbackWidth, fallbackHeight);
            matcher.appendReplacement(updated, java.util.regex.Matcher.quoteReplacement(fixedTag));
        }
        matcher.appendTail(updated);
        return updated.toString();
    }

    private String fixInlineStyleOnTag(String tag, int fallbackWidth, int fallbackHeight) {
        Pattern stylePattern = Pattern.compile("(?is)\\bstyle\\s*=\\s*([\"'])(.*?)\\1");
        java.util.regex.Matcher styleMatcher = stylePattern.matcher(tag);
        if (!styleMatcher.find()) {
            return tag;
        }

        String style = styleMatcher.group(2);
        String fixedStyle = keepAreaStyleInsideCard(style, fallbackWidth, fallbackHeight);
        return styleMatcher.replaceFirst("style=" + styleMatcher.group(1)
                + java.util.regex.Matcher.quoteReplacement(fixedStyle)
                + styleMatcher.group(1));
    }

    private String keepExtraAreasInsideCard(String css) {
        String updated = css == null ? "" : css;
        updated = keepAreaInsideCard(updated, "portfolioArea", EXTRA_AREA_WIDTH, EXTRA_AREA_HEIGHT);
        updated = keepAreaInsideCard(updated, "linkArea", Math.min(EXTRA_AREA_WIDTH, 360), LINK_AREA_HEIGHT);
        updated = keepAreaClassInsideCard(updated, "card-portfolio", EXTRA_AREA_WIDTH, EXTRA_AREA_HEIGHT);
        updated = keepAreaClassInsideCard(updated, "card-links", Math.min(EXTRA_AREA_WIDTH, 360), LINK_AREA_HEIGHT);
        return updated;
    }

    private String keepAreaInsideCard(String css, String id, int fallbackWidth, int fallbackHeight) {
        return keepAreaRuleInsideCard(css, "#[a-zA-Z0-9_-]*" + Pattern.quote(id), fallbackWidth, fallbackHeight);
    }

    private String keepAreaClassInsideCard(String css, String className, int fallbackWidth, int fallbackHeight) {
        return keepAreaRuleInsideCard(css, "\\.[a-zA-Z0-9_-]*" + Pattern.quote(className), fallbackWidth, fallbackHeight);
    }

    private String keepAreaRuleInsideCard(String css, String selectorPattern, int fallbackWidth, int fallbackHeight) {
        Pattern rulePattern = Pattern.compile("(?is)(" + selectorPattern + "[^{]*\\{)(.*?)(\\})");
        java.util.regex.Matcher matcher = rulePattern.matcher(css);
        StringBuilder updated = new StringBuilder();

        while (matcher.find()) {
            String body = matcher.group(2);
            Integer left = readPixelProperty(body, "left");
            Integer right = readPixelProperty(body, "right");
            Integer width = readPixelProperty(body, "width");
            Integer top = readPixelProperty(body, "top");
            Integer bottom = readPixelProperty(body, "bottom");
            Integer height = readPixelProperty(body, "height");
            if (left == null && right == null && top == null && bottom == null) {
                matcher.appendReplacement(updated, java.util.regex.Matcher.quoteReplacement(matcher.group()));
                continue;
            }

            String fixedBody = keepAreaStyleInsideCard(body, fallbackWidth, fallbackHeight, left, right, width, top, bottom, height);
            matcher.appendReplacement(updated, java.util.regex.Matcher.quoteReplacement(matcher.group(1) + fixedBody + matcher.group(3)));
        }
        matcher.appendTail(updated);
        return updated.toString();
    }

    private String keepAreaStyleInsideCard(String style, int fallbackWidth, int fallbackHeight) {
        Integer left = readPixelProperty(style, "left");
        Integer right = readPixelProperty(style, "right");
        Integer width = readPixelProperty(style, "width");
        Integer top = readPixelProperty(style, "top");
        Integer bottom = readPixelProperty(style, "bottom");
        Integer height = readPixelProperty(style, "height");
        if (left == null && right == null && top == null && bottom == null) {
            return style;
        }
        return keepAreaStyleInsideCard(style, fallbackWidth, fallbackHeight, left, right, width, top, bottom, height);
    }

    private String keepAreaStyleInsideCard(
            String style,
            int fallbackWidth,
            int fallbackHeight,
            Integer left,
            Integer right,
            Integer width,
            Integer top,
            Integer bottom,
            Integer height
    ) {
        int actualWidth = width == null ? fallbackWidth : Math.min(width, fallbackWidth);
        int actualHeight = height == null ? fallbackHeight : Math.min(height, fallbackHeight);
        String fixedStyle = style;
        if (width == null || width > fallbackWidth) {
            fixedStyle = writePixelProperty(fixedStyle, "width", fallbackWidth);
        }
        if (height == null || height > fallbackHeight) {
            fixedStyle = writePixelProperty(fixedStyle, "height", fallbackHeight);
        }
        if (left != null) {
            int maxLeft = CARD_WIDTH - SAFE_MARGIN - actualWidth;
            int safeLeft = Math.max(SAFE_MARGIN, Math.min(left, maxLeft));
            if (safeLeft != left) {
                fixedStyle = writePixelProperty(fixedStyle, "left", safeLeft);
                fixedStyle = removeProperty(fixedStyle, "right");
            }
        } else if (right != null && right < SAFE_MARGIN) {
            fixedStyle = writePixelProperty(fixedStyle, "right", SAFE_MARGIN);
        }
        if (top != null) {
            int maxTop = CARD_HEIGHT - SAFE_MARGIN - actualHeight;
            int safeTop = Math.max(SAFE_MARGIN, Math.min(top, maxTop));
            if (safeTop != top) {
                fixedStyle = writePixelProperty(fixedStyle, "top", safeTop);
                fixedStyle = removeProperty(fixedStyle, "bottom");
            }
        } else if (bottom != null && bottom < SAFE_MARGIN) {
            fixedStyle = writePixelProperty(fixedStyle, "bottom", SAFE_MARGIN);
        }
        return fixedStyle;
    }

    private Integer readPixelProperty(String cssBody, String property) {
        Pattern pattern = Pattern.compile("(?is)(?:^|;)\\s*" + Pattern.quote(property) + "\\s*:\\s*(-?\\d+)px\\s*");
        java.util.regex.Matcher matcher = pattern.matcher(cssBody);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String writePixelProperty(String cssBody, String property, int value) {
        Pattern pattern = Pattern.compile("(?is)((?:^|;)\\s*" + Pattern.quote(property) + "\\s*:\\s*)[^;]+");
        java.util.regex.Matcher matcher = pattern.matcher(cssBody);
        String replacement = "$1" + value + "px";
        if (matcher.find()) {
            return matcher.replaceFirst(replacement);
        }
        String separator = cssBody.stripTrailing().endsWith(";") ? " " : "; ";
        return cssBody + separator + property + ": " + value + "px;";
    }

    private String removeProperty(String cssBody, String property) {
        return cssBody.replaceAll("(?is)(?:^|;)\\s*" + Pattern.quote(property) + "\\s*:\\s*[^;]+;?", ";");
    }

    private String removeStandaloneExtraItems(String html, Long cardId) {
        String updated = html == null ? "" : html;
        List<CardLink> items = cardLinkRepository.findByCardIdOrderBySortOrderAscLinkIdAsc(cardId);
        for (CardLink item : items) {
            updated = removeElementWithOnlyText(updated, item.getTitle());
            updated = removeElementWithOnlyText(updated, item.getUrl());
        }
        return updated;
    }

    private String removeAiGeneratedExtraPanels(String html, Long cardId) {
        String updated = html == null ? "" : html;
        List<CardLink> items = cardLinkRepository.findByCardIdOrderBySortOrderAscLinkIdAsc(cardId);
        if (items.isEmpty()) {
            return updated;
        }

        updated = removeElementsWithExtraAttributeKeywords(updated, items);

        String[] duplicateHeadings = {
                "PORTFOLIO / SKILLS / LINKS",
                "Portfolio / Skills / Links",
                "PORTFOLIO",
                "SKILLS",
                "LINKS",
                "Portfolio",
                "Skills",
                "Links",
                "기술",
                "자격증",
                "포트폴리오",
                "링크"
        };
        for (String heading : duplicateHeadings) {
            updated = removeElementWithOnlyText(updated, heading);
        }
        return updated;
    }

    private String removeElementsWithExtraAttributeKeywords(String html, List<CardLink> items) {
        String updated = html;
        String[] tags = {"div", "section", "aside", "article", "ul", "ol"};
        boolean removed;
        do {
            removed = false;
            for (String tag : tags) {
                int start = 0;
                while (start >= 0 && start < updated.length()) {
                    int openStart = findOpeningTag(updated, tag, start);
                    if (openStart < 0) {
                        break;
                    }
                    int openEnd = updated.indexOf('>', openStart);
                    if (openEnd < 0) {
                        break;
                    }
                    String openTag = updated.substring(openStart, openEnd + 1);
                    if (!hasExtraPanelAttribute(openTag)) {
                        start = openEnd + 1;
                        continue;
                    }
                    int closeEnd = findMatchingCloseTag(updated, tag, openEnd + 1);
                    if (closeEnd < 0) {
                        start = openEnd + 1;
                        continue;
                    }
                    String block = updated.substring(openStart, closeEnd);
                    String plainText = block.replaceAll("(?is)<[^>]+>", " ")
                            .replaceAll("\\s+", " ")
                            .toLowerCase();
                    if (looksLikeDuplicateExtraPanel(plainText, items)) {
                        updated = updated.substring(0, openStart) + updated.substring(closeEnd);
                        removed = true;
                        break;
                    }
                    start = openEnd + 1;
                }
                if (removed) {
                    break;
                }
            }
        } while (removed);
        return updated;
    }

    private int findOpeningTag(String html, String tag, int fromIndex) {
        String lower = html.toLowerCase();
        String needle = "<" + tag;
        int index = lower.indexOf(needle, fromIndex);
        while (index >= 0) {
            int next = index + needle.length();
            if (next < lower.length()) {
                char nextChar = lower.charAt(next);
                if (Character.isWhitespace(nextChar) || nextChar == '>' || nextChar == '/') {
                    return index;
                }
            }
            index = lower.indexOf(needle, next);
        }
        return -1;
    }

    private int findMatchingCloseTag(String html, String tag, int fromIndex) {
        String lower = html.toLowerCase();
        int depth = 1;
        int index = fromIndex;
        while (index < lower.length()) {
            int nextOpen = findOpeningTag(html, tag, index);
            int nextClose = lower.indexOf("</" + tag + ">", index);
            if (nextClose < 0) {
                return -1;
            }
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                index = nextOpen + tag.length() + 1;
                continue;
            }
            depth--;
            index = nextClose + tag.length() + 3;
            if (depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasExtraPanelAttribute(String openTag) {
        String lower = openTag.toLowerCase();
        if (lower.contains("id=\"portfolioarea\"") || lower.contains("id='portfolioarea'")
                || lower.contains("id=\"linkarea\"") || lower.contains("id='linkarea'")
                || lower.contains("id=\"cardroot\"") || lower.contains("id='cardroot'")) {
            return false;
        }
        if (!lower.contains("class=") && !lower.contains("id=")) {
            return false;
        }
        String[] keywords = {"portfolio", "skill", "skills", "link", "links", "certificate", "cert"};
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeDuplicateExtraPanel(String plainText, List<CardLink> items) {
        String[] sectionWords = {
                "portfolio",
                "skills",
                "links",
                "certificate",
                "cert",
                "포트폴리오",
                "기술",
                "링크",
                "자격증"
        };
        for (String word : sectionWords) {
            if (plainText.contains(word)) {
                return true;
            }
        }
        for (CardLink item : items) {
            String title = text(item.getTitle()).toLowerCase();
            String url = text(item.getUrl()).toLowerCase();
            if (!title.isBlank() && plainText.contains(title)) {
                return true;
            }
            if (!url.isBlank() && plainText.contains(url)) {
                return true;
            }
        }
        return false;
    }

    private String removeElementWithOnlyText(String html, String value) {
        String cleanValue = text(value);
        if (cleanValue.isBlank()) {
            return html;
        }

        String escapedValue = Pattern.quote(HtmlUtils.htmlEscape(cleanValue));
        String rawValue = Pattern.quote(cleanValue);
        String pattern = "(?is)<([a-zA-Z0-9]+)(?![^>]*\\bid=[\"'](?:portfolioArea|linkArea)[\"'])[^>]*>\\s*(?:" + escapedValue + "|" + rawValue + ")\\s*</\\1>";
        return html.replaceAll(pattern, "");
    }

    private String replaceElementHtml(String html, String id, String value) {
        String replacement = value == null ? "" : value;
        String idPattern = Pattern.quote(id);
        String pattern = "(?s)(<([a-zA-Z0-9]+)(?=[^>]*\\bid=[\"']" + idPattern + "[\"'])[^>]*>)(.*?)(</\\2>)";
        return html.replaceAll(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(replacement) + "$4");
    }

    private String buildPortfolioHtml(List<CardLink> items) {
        StringBuilder html = new StringBuilder();
        boolean hasItem = false;
        html.append("<div class=\"card-extra-title\">Portfolio / Skills / Links</div>");
        html.append("<div class=\"card-extra-list\">");
        for (CardLink item : items) {
            String type = type(item);
            String title = defaultText(item.getTitle(), item.getUrl());
            String url = text(item.getUrl());
            String imageUrl = text(item.getImageUrl());
            if (text(title).isBlank() && url.isBlank() && imageUrl.isBlank()) {
                continue;
            }
            hasItem = true;
            html.append("<button type=\"button\" class=\"card-extra-item card-extra-button card-extra-")
                    .append(type.toLowerCase())
                    .append("\" data-card-extra-button data-extra-type=\"")
                    .append(attr(label(type)))
                    .append("\" data-extra-title=\"")
                    .append(attr(title))
                    .append("\" data-extra-url=\"")
                    .append(attr(url))
                    .append("\" data-extra-image=\"")
                    .append(attr(imageUrl))
                    .append("\">");
            html.append("<span class=\"card-extra-kind\">").append(label(type)).append("</span>");
            if (!text(title).isBlank()) {
                html.append("<strong>").append(escape(title)).append("</strong>");
            }
            html.append("</button>");
        }
        html.append("</div>");
        if (hasItem) {
            return html.toString();
        }
        return """
                <div class="card-extra-title">Portfolio / Skills / Links</div>
                <div class="card-extra-empty">추가 정보 영역</div>
                """;
    }

    private String buildLinkHtml(List<CardLink> items) {
        return "";
    }

    private String extraCardCss() {
        return """
                .card-extra-title { font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; margin-bottom: 8px; }
                .card-extra-list { display: flex; flex-wrap: wrap; gap: 7px; align-items: flex-start; }
                .card-extra-item { display: inline-flex; flex-direction: column; gap: 2px; max-width: 150px; padding: 6px 8px; border-radius: 12px; background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.18); color: inherit; }
                .card-extra-button { cursor: pointer; font: inherit; text-align: left; appearance: none; transition: transform .16s ease, background .16s ease, border-color .16s ease; }
                .card-extra-button:hover { transform: translateY(-1px); background: rgba(255,255,255,.2); border-color: rgba(255,255,255,.34); }
                .card-extra-item strong { font-size: 12px; line-height: 1.2; }
                .card-extra-item small { font-size: 11px; line-height: 1.35; opacity: .82; word-break: break-word; }
                .card-extra-kind { font-size: 10px; font-weight: 800; opacity: .72; }
                .card-extra-empty { min-height: 48px; display: grid; place-items: center; border: 1px dashed rgba(255,255,255,.22); border-radius: 12px; color: inherit; opacity: .42; font-size: 12px; font-weight: 700; }
                #cardRoot { position: relative; overflow: hidden; }
                #profileImage { overflow: hidden; }
                #profileImage img.card-profile-uploaded { width: 100%; height: 100%; object-fit: contain; object-position: center; border-radius: inherit; display: block; background: rgba(255,255,255,.92); }
                #portfolioArea:not(:empty), #linkArea:not(:empty) {
                  box-sizing: border-box;
                  max-width: min(360px, calc(100% - 72px)) !important;
                  z-index: 5;
                  padding: 18px !important;
                  border: 1px solid rgba(255,255,255,.24) !important;
                  border-radius: 18px !important;
                  color: #f8fafc !important;
                  background: rgba(15,23,42,.86) !important;
                  backdrop-filter: blur(8px);
                  box-shadow: 0 18px 44px rgba(2,6,23,.22) !important;
                }
                #portfolioArea:not(:empty) {
                  max-height: 178px;
                }
                #linkArea:not(:empty) {
                  max-height: 96px;
                }
                #portfolioArea:empty, #linkArea:empty { display: none !important; }
                #portfolioArea, #linkArea { box-sizing: border-box; overflow: hidden; }
                #portfolioArea .card-extra-title, #linkArea .card-extra-title { color: #f8fafc !important; }
                #portfolioArea .card-extra-list, #linkArea .card-extra-list { max-width: 100%; max-height: 100%; overflow: hidden; }
                #portfolioArea .card-extra-item, #linkArea .card-extra-item {
                  min-width: 0;
                  max-width: 100%;
                  color: #f8fafc !important;
                  background: rgba(255,255,255,.1) !important;
                  border-color: rgba(255,255,255,.2) !important;
                }
                #portfolioArea .card-extra-empty, #linkArea .card-extra-empty {
                  color: #cbd5e1 !important;
                  border-color: rgba(203,213,225,.3) !important;
                  opacity: .76 !important;
                }
                .card-extra-modal-backdrop {
                  position: fixed;
                  inset: 0;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: 24px;
                  background: rgba(2, 6, 23, .42);
                  z-index: 9999;
                }
                .card-extra-modal-backdrop[hidden] { display: none !important; }
                .card-extra-modal {
                  width: min(360px, calc(100vw - 48px));
                  box-sizing: border-box;
                  position: relative;
                  padding: 22px;
                  border-radius: 18px;
                  border: 1px solid rgba(255,255,255,.22);
                  color: #f8fafc;
                  background: rgba(15, 23, 42, .96);
                  box-shadow: 0 24px 70px rgba(0,0,0,.35);
                  font-family: Arial, sans-serif;
                }
                .card-extra-modal-close {
                  position: absolute;
                  top: 10px;
                  right: 10px;
                  width: 32px;
                  height: 32px;
                  border-radius: 999px;
                  border: 1px solid rgba(255,255,255,.2);
                  color: #f8fafc;
                  background: rgba(255,255,255,.08);
                  cursor: pointer;
                }
                .card-extra-modal-type { margin: 0 0 8px; font-size: 12px; font-weight: 800; color: #38bdf8; letter-spacing: .08em; }
                .card-extra-modal-title { margin: 0; padding-right: 34px; font-size: 22px; line-height: 1.3; }
                .card-extra-modal-image {
                  width: 92px;
                  height: 68px;
                  margin-top: 14px;
                  border: 1px solid rgba(255,255,255,.22);
                  border-radius: 12px;
                  object-fit: cover;
                  cursor: zoom-in;
                }
                .card-extra-modal-url {
                  width: 100%;
                  margin-top: 14px;
                  padding: 12px 14px;
                  border: 1px solid rgba(94,234,212,.35);
                  border-radius: 12px;
                  color: #99f6e4;
                  background: rgba(94,234,212,.1);
                  font-weight: 800;
                  font-size: 13px;
                  line-height: 1.45;
                  word-break: break-all;
                  text-align: left;
                  cursor: pointer;
                }
                .card-extra-image-viewer {
                  position: fixed;
                  inset: 0;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: 24px;
                  background: rgba(2, 6, 23, .78);
                  z-index: 10000;
                }
                .card-extra-image-viewer[hidden] { display: none !important; }
                .card-extra-image-viewer img {
                  max-width: min(920px, calc(100vw - 48px));
                  max-height: calc(100vh - 48px);
                  border-radius: 16px;
                  box-shadow: 0 24px 70px rgba(0,0,0,.45);
                }
                .card-extra-image-close {
                  position: fixed;
                  top: 18px;
                  right: 18px;
                  width: 38px;
                  height: 38px;
                  border-radius: 999px;
                  border: 1px solid rgba(255,255,255,.28);
                  color: #fff;
                  background: rgba(15,23,42,.86);
                  cursor: pointer;
                  font-weight: 900;
                }
                """;
    }

    private String extraCardModal() {
        return """
                <div id="cardExtraModal" class="card-extra-modal-backdrop" hidden>
                  <div class="card-extra-modal" role="dialog" aria-modal="true" aria-labelledby="cardExtraModalTitle">
                    <button type="button" class="card-extra-modal-close" data-card-extra-close aria-label="close">X</button>
                    <p class="card-extra-modal-type" data-card-extra-modal-type></p>
                    <h2 id="cardExtraModalTitle" class="card-extra-modal-title" data-card-extra-modal-title></h2>
                    <img class="card-extra-modal-image" data-card-extra-modal-image hidden alt="portfolio image">
                    <button type="button" class="card-extra-modal-url" data-card-extra-modal-url hidden></button>
                  </div>
                </div>
                <div id="cardExtraImageViewer" class="card-extra-image-viewer" hidden>
                  <button type="button" class="card-extra-image-close" data-card-extra-image-close aria-label="close">X</button>
                  <img data-card-extra-image-large alt="portfolio image">
                </div>
                """;
    }

    private String extraCardScript() {
        return """
                <script>
                (function () {
                  var modal = document.getElementById("cardExtraModal");
                  if (!modal) return;
                  var typeEl = modal.querySelector("[data-card-extra-modal-type]");
                  var titleEl = modal.querySelector("[data-card-extra-modal-title]");
                  var urlEl = modal.querySelector("[data-card-extra-modal-url]");
                  var imageEl = modal.querySelector("[data-card-extra-modal-image]");
                  var viewer = document.getElementById("cardExtraImageViewer");
                  var largeImage = viewer ? viewer.querySelector("[data-card-extra-image-large]") : null;
                  var currentUrl = "";
                  var currentImage = "";

                  function openModal(button) {
                    currentUrl = button.getAttribute("data-extra-url") || "";
                    currentImage = button.getAttribute("data-extra-image") || "";
                    typeEl.textContent = button.getAttribute("data-extra-type") || "ITEM";
                    titleEl.textContent = button.getAttribute("data-extra-title") || "상세 정보";
                    urlEl.textContent = currentUrl;
                    urlEl.hidden = !currentUrl;
                    imageEl.src = currentImage;
                    imageEl.hidden = !currentImage;
                    modal.hidden = false;
                  }

                  function closeModal() {
                    modal.hidden = true;
                    currentUrl = "";
                    currentImage = "";
                  }

                  document.addEventListener("click", function (event) {
                    var itemButton = event.target.closest("[data-card-extra-button]");
                    if (itemButton) {
                      openModal(itemButton);
                      return;
                    }
                    if (event.target.closest("[data-card-extra-close]") || event.target === modal) {
                      closeModal();
                      return;
                    }
                    if (event.target.closest("[data-card-extra-modal-image]") && currentImage && viewer && largeImage) {
                      largeImage.src = currentImage;
                      viewer.hidden = false;
                      return;
                    }
                    if (event.target.closest("[data-card-extra-image-close]") || event.target === viewer) {
                      if (viewer) viewer.hidden = true;
                      return;
                    }
                    if (event.target.closest("[data-card-extra-modal-url]") && currentUrl) {
                      if (window.confirm(currentUrl + " 로 이동하시겠습니까?")) {
                        window.open(currentUrl, "_blank", "noopener");
                      }
                    }
                  });

                  document.addEventListener("keydown", function (event) {
                    if (event.key === "Escape") {
                      closeModal();
                      if (viewer) viewer.hidden = true;
                    }
                  });
                })();
                </script>
                """;
    }

    private String type(CardLink item) {
        return text(item.getItemType()).isBlank() ? "SKILL" : item.getItemType().trim().toUpperCase();
    }

    private String label(String type) {
        return switch (type) {
            case "CERTIFICATE" -> "CERT";
            case "PORTFOLIO" -> "PORTFOLIO";
            case "LINK" -> "LINK";
            default -> "SKILL";
        };
    }

    private String defaultText(String value, String fallback) {
        return text(value).isBlank() ? text(fallback) : value;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(text(value));
    }

    private String attr(String value) {
        return HtmlUtils.htmlEscape(text(value).replace("\"", ""));
    }

    private String normalizeContextPath() {
        String value = text(contextPath).trim();
        if (value.isBlank() || "/".equals(value)) {
            return "";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private record ElementBlock(int start, int openEnd, int closeStart, int end, String html) {
    }

    private record LayoutBox(int x, int y, int width, int height) {
    }
}
