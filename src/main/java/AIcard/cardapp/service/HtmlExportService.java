package AIcard.cardapp.service;

import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.entity.CardAiResult;
import AIcard.cardapp.entity.CardLink;
import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.repository.CardAiResultRepository;
import AIcard.cardapp.repository.CardLayoutRepository;
import AIcard.cardapp.repository.CardLinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class HtmlExportService {

    private final BusinessCardRepository businessCardRepository;
    private final CardAiResultRepository cardAiResultRepository;
    private final CardLayoutRepository cardLayoutRepository;
    private final CardLinkRepository cardLinkRepository;

    @Value("${app.generated-card-dir:generated-cards}")
    private String generatedCardDir;

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

        cardLayoutRepository.findByCardId(cardId);

        String htmlFragment = removeStandaloneExtraItems(aiResult.getGeneratedHtml(), card.getCardId());
        htmlFragment = applyBusinessText(htmlFragment, card);
        String css = aiResult.getGeneratedCss();
        htmlFragment = applyExtraItems(htmlFragment, card.getCardId());
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
        if (!exported.isBlank() && exported.contains("ai-card-inline-style")) {
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
        return updated;
    }

    private String replaceElementText(String html, String id, String value) {
        String idPattern = Pattern.quote(id);
        String pattern = "(?s)(<([a-zA-Z0-9]+)(?=[^>]*\\bid=[\"']" + idPattern + "[\"'])[^>]*>)(.*?)(</\\2>)";
        return html.replaceAll(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(value) + "$4");
    }

    private String applyExtraItems(String html, Long cardId) {
        List<CardLink> items = cardLinkRepository.findByCardIdOrderBySortOrderAscLinkIdAsc(cardId);

        String updated = replaceElementHtml(html, "portfolioArea", buildPortfolioHtml(items));
        updated = replaceElementHtml(updated, "linkArea", buildLinkHtml(items));
        return updated;
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
                #cardRoot { position: relative; }
                #portfolioArea:not(:empty), #linkArea:not(:empty) {
                  box-sizing: border-box;
                  width: min(430px, 100%);
                  max-width: 430px;
                  z-index: 5;
                  padding: 18px !important;
                  border: 1px solid rgba(255,255,255,.18) !important;
                  border-radius: 18px !important;
                  color: inherit !important;
                  background: rgba(255,255,255,.08) !important;
                  backdrop-filter: blur(8px);
                }
                #portfolioArea:not(:empty) {
                  max-height: 178px;
                }
                #linkArea:not(:empty) {
                  max-height: 96px;
                }
                #portfolioArea:empty, #linkArea:empty { display: none !important; }
                #portfolioArea, #linkArea { box-sizing: border-box; overflow: hidden; }
                #portfolioArea .card-extra-title, #linkArea .card-extra-title { color: inherit !important; }
                #portfolioArea .card-extra-list, #linkArea .card-extra-list { max-width: 100%; max-height: 100%; overflow: hidden; }
                #portfolioArea .card-extra-item, #linkArea .card-extra-item { min-width: 0; max-width: 100%; }
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

    private String text(String value) {
        return value == null ? "" : value;
    }
}
