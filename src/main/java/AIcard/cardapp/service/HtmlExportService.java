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

        String htmlFragment = applyBusinessText(aiResult.getGeneratedHtml(), card);
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
                </body>
                </html>
                """.formatted(css == null ? "" : css, extraCardCss(), bodyHtml == null ? "" : bodyHtml);
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
        if (items.isEmpty()) {
            return html;
        }

        String updated = replaceElementHtml(html, "portfolioArea", buildPortfolioHtml(items));
        updated = replaceElementHtml(updated, "linkArea", buildLinkHtml(items));

        String profileHtml = buildProfileImageHtml(items);
        if (!profileHtml.isBlank()) {
            updated = replaceElementHtml(updated, "profileImage", profileHtml);
        }
        return updated;
    }

    private String replaceElementHtml(String html, String id, String value) {
        if (value == null || value.isBlank()) {
            return html;
        }
        String idPattern = Pattern.quote(id);
        String pattern = "(?s)(<([a-zA-Z0-9]+)(?=[^>]*\\bid=[\"']" + idPattern + "[\"'])[^>]*>)(.*?)(</\\2>)";
        return html.replaceAll(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(value) + "$4");
    }

    private String buildPortfolioHtml(List<CardLink> items) {
        StringBuilder html = new StringBuilder();
        boolean hasItem = false;
        html.append("<div class=\"card-extra-title\">Portfolio / Skills</div>");
        html.append("<div class=\"card-extra-list\">");
        for (CardLink item : items) {
            String type = type(item);
            if ("LINK".equals(type)) {
                continue;
            }
            if ("PHOTO".equals(type) && text(item.getImageUrl()).isBlank()) {
                continue;
            }
            hasItem = true;
            html.append("<div class=\"card-extra-item card-extra-").append(type.toLowerCase()).append("\">");
            if ("PHOTO".equals(type)) {
                html.append("<img class=\"card-extra-image\" src=\"")
                        .append(attr(item.getImageUrl()))
                        .append("\" alt=\"")
                        .append(attr(defaultText(item.getTitle(), "portfolio image")))
                        .append("\">");
            }
            html.append("<span class=\"card-extra-kind\">").append(label(type)).append("</span>");
            if (!text(item.getTitle()).isBlank()) {
                html.append("<strong>").append(escape(item.getTitle())).append("</strong>");
            }
            if (!text(item.getDescription()).isBlank()) {
                html.append("<small>").append(escape(item.getDescription())).append("</small>");
            }
            if (!text(item.getUrl()).isBlank()) {
                html.append("<small>").append(escape(item.getUrl())).append("</small>");
            }
            html.append("</div>");
        }
        html.append("</div>");
        return hasItem ? html.toString() : "";
    }

    private String buildLinkHtml(List<CardLink> items) {
        StringBuilder html = new StringBuilder();
        boolean hasItem = false;
        html.append("<div class=\"card-extra-title\">Links</div>");
        html.append("<div class=\"card-extra-list\">");
        for (CardLink item : items) {
            if (!"LINK".equals(type(item))) {
                continue;
            }
            String title = defaultText(item.getTitle(), item.getUrl());
            if (text(title).isBlank() && text(item.getDescription()).isBlank()) {
                continue;
            }
            hasItem = true;
            html.append("<div class=\"card-extra-item card-extra-link\">");
            if (!text(title).isBlank()) {
                html.append("<strong>").append(escape(title)).append("</strong>");
            }
            if (!text(item.getDescription()).isBlank()) {
                html.append("<small>").append(escape(item.getDescription())).append("</small>");
            }
            if (!text(item.getUrl()).isBlank()) {
                html.append("<small>").append(escape(item.getUrl())).append("</small>");
            }
            html.append("</div>");
        }
        html.append("</div>");
        return hasItem ? html.toString() : "";
    }

    private String buildProfileImageHtml(List<CardLink> items) {
        for (CardLink item : items) {
            if ("PHOTO".equals(type(item)) && !text(item.getImageUrl()).isBlank()) {
                return "<img class=\"card-profile-img\" src=\"%s\" alt=\"profile image\">".formatted(attr(item.getImageUrl()));
            }
        }
        return "";
    }

    private String extraCardCss() {
        return """
                .card-extra-title { font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; margin-bottom: 8px; }
                .card-extra-list { display: flex; flex-wrap: wrap; gap: 8px; align-items: flex-start; }
                .card-extra-item { display: inline-flex; flex-direction: column; gap: 3px; max-width: 220px; padding: 7px 9px; border-radius: 12px; background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.18); color: inherit; }
                .card-extra-item strong { font-size: 13px; line-height: 1.25; }
                .card-extra-item small { font-size: 11px; line-height: 1.35; opacity: .82; word-break: break-word; }
                .card-extra-kind { font-size: 10px; font-weight: 800; opacity: .72; }
                .card-extra-image { width: 92px; height: 58px; object-fit: cover; border-radius: 8px; display: block; }
                .card-profile-img { width: 100%; height: 100%; object-fit: cover; border-radius: inherit; display: block; }
                """;
    }

    private String type(CardLink item) {
        return text(item.getItemType()).isBlank() ? "SKILL" : item.getItemType().trim().toUpperCase();
    }

    private String label(String type) {
        return switch (type) {
            case "CERTIFICATE" -> "CERT";
            case "PORTFOLIO" -> "PORTFOLIO";
            case "PHOTO" -> "PHOTO";
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
