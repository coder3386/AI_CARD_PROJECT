package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.CardCreateRequest;
import AIcard.cardapp.DTO.CardDrawingCreateRequest;
import AIcard.cardapp.DTO.CardUpdateTextRequest;
import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.entity.BusinessCardDetail;
import AIcard.cardapp.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

@Controller
public class AiCardController {

    private static final org.slf4j.Logger userLog = org.slf4j.LoggerFactory.getLogger("USER_LOGGER");

    private final AiCardService aiCardService;
    private final CardQrService cardQrService;
    private final CurrentUserService currentUserService;
    private final PublicCardUrlService publicCardUrlService;

    public AiCardController(
            AiCardService aiCardService,
            CardQrService cardQrService,
            CurrentUserService currentUserService,
            PublicCardUrlService publicCardUrlService
    ) {
        this.aiCardService = aiCardService;
        this.cardQrService = cardQrService;
        this.currentUserService = currentUserService;
        this.publicCardUrlService = publicCardUrlService;
    }

    @GetMapping("/cards/select-type")
    public String selectType(RedirectAttributes redirectAttributes) {
        if (currentUserService.getCurrentUserIdOrNull() == null) {
            redirectAttributes.addFlashAttribute("loginRequiredMessage", "로그인 후 이용해주세요.");
            return "redirect:/card/login";
        }
        return "cards/select-type";
    }

    @GetMapping("/cards/new")
    public String newCard(Model model) {
        model.addAttribute("cardCreateRequest", new CardCreateRequest());
        return "cards/ai-new";
    }

    @PostMapping("/cards/generate")
    public String generate(
            @ModelAttribute CardCreateRequest request,
            RedirectAttributes redirectAttributes,
            HttpServletRequest httpRequest
    ) {
        try {
            validateAtLeastOneApiKey(request.getApiKey(), request.getGeminiApiKey());
            Long userId = requireCurrentUserId(redirectAttributes);
            if (userId == null) {
                return "redirect:/card/login";
            }
            Long cardId = aiCardService.generate(request, userId);
            createQrCode(cardId, httpRequest);
            userLog.info("[USER-ACTION]|명함 생성 완료. type=text, userId={}, cardId={}, QR 생성 포함", userId, cardId);
            if (aiCardService.isLatestAiResultFallback(cardId)) {
                return "redirect:/cards/" + cardId + "/ai-fallback?retryType=text";
            }
            return "redirect:/cards/" + cardId + "/preview";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cards/new";
        }
    }

    @GetMapping("/cards/drawing/new")
    public String newDrawingCard(Model model) {
        model.addAttribute("cardDrawingCreateRequest", new CardDrawingCreateRequest());
        return "cards/drawing-new";
    }

    @PostMapping("/cards/drawing/generate")
    public String generateDrawing(
            @ModelAttribute CardDrawingCreateRequest request,
            RedirectAttributes redirectAttributes,
            HttpServletRequest httpRequest
    ) {
        try {
            validateAtLeastOneApiKey(request.getApiKey(), request.getGeminiApiKey());
            Long userId = requireCurrentUserId(redirectAttributes);
            if (userId == null) {
                return "redirect:/card/login";
            }
            Long cardId = aiCardService.generateDrawing(request, userId);
            createQrCode(cardId, httpRequest);
            userLog.info("[USER-ACTION]|명함 생성 완료. type=drawing, userId={}, cardId={}, QR 생성 포함", userId, cardId);
            if (aiCardService.isLatestAiResultFallback(cardId)) {
                return "redirect:/cards/" + cardId + "/ai-fallback?retryType=drawing";
            }
            return "redirect:/cards/drawing/" + cardId + "/preview";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cards/drawing/new";
        }
    }

    @GetMapping("/cards/{cardId}/preview")
    public String preview(@PathVariable Long cardId, Model model) {
        addPreviewModel(cardId, model);
        return "cards/ai-preview";
    }

    @GetMapping("/cards/drawing/{cardId}/preview")
    public String drawingPreview(@PathVariable Long cardId, Model model) {
        addPreviewModel(cardId, model);
        return "cards/ai-preview";
    }

    @PostMapping("/cards/{cardId}/update-text")
    public String updateText(
            @PathVariable Long cardId,
            @ModelAttribute CardUpdateTextRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            aiCardService.updateText(cardId, request);
            return "redirect:/cards/" + cardId + "/preview";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cards/" + cardId + "/preview";
        }
    }

    @PostMapping("/cards/{cardId}/fix-layout")
    public String fixLayout(@PathVariable Long cardId, @ModelAttribute CardUpdateTextRequest request, RedirectAttributes redirectAttributes) {
        try {
            validateAtLeastOneApiKey(request.getApiKey(), request.getGeminiApiKey());
            aiCardService.fixLayout(cardId, request);
            return "redirect:/cards/" + cardId + "/preview";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/cards/" + cardId + "/preview";
        }
    }

    @PostMapping("/cards/{cardId}/delete")
    public String deleteCard(@PathVariable Long cardId) {
        aiCardService.deleteCard(cardId);
        return "redirect:/cards/new";
    }

    @GetMapping("/cards/{cardId}/profile-image")
    public ResponseEntity<byte[]> profileImage(@PathVariable Long cardId) {
        try {
            BusinessCardDetail detail = aiCardService.getProfileImageDetail(cardId);
            String contentType = detail.getProfileImageContentType();
            MediaType mediaType = contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(detail.getProfileImage());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/public/card/{publicUrl}")
    public ResponseEntity<String> publicCard(@PathVariable String publicUrl, HttpServletRequest request) {

        Logger accessLogger = LoggerFactory.getLogger("ACCESS_LOGGER");

        try {
            Long viewerUserId = currentUserService.getCurrentUserIdOrNull();
            String html = aiCardService.readPublicCard(publicUrl, viewerUserId);

            String clientIp = request.getRemoteAddr();
            String requestUrl = request.getRequestURI();

            accessLogger.info("[ACCESS] IP: {} | 요청 URL: {}", clientIp, requestUrl);

            try {
                BusinessCard card = aiCardService.getCardByUrl(publicUrl);

                if (card != null && card.getGaSettings() != null) {
                    String trackingId = card.getGaSettings().getMeasurementId();

                    String gaScript = String.format("""
                        <script async src="https://www.googletagmanager.com/gtag/js?id=%s"></script>
                        <script>
                          window.dataLayer = window.dataLayer || [];
                          function gtag(){dataLayer.push(arguments);}
                          gtag('js', new Date());
                          gtag('config', '%s');
                        </script>
                        """, trackingId, trackingId);

                    html = html.replace("</head>", gaScript + "</head>");
                }
            } catch (Exception e) {
                System.err.println("⚠️ GA 스크립트 삽입 실패: " + e.getMessage());
                e.printStackTrace();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(302)
                    .location(URI.create(request.getContextPath() + "/dont"))
                    .build();
        }
    }

    @GetMapping("/cards/{cardId}/ai-fallback")
    public String aiFallbackNotice(
            @PathVariable Long cardId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "text") String retryType,
            Model model
    ) {
        BusinessCard card = aiCardService.getCard(cardId);
        model.addAttribute("card", card);
        model.addAttribute("failureReason", aiCardService.getLatestAiResultReason(cardId));
        model.addAttribute("retryType", retryType);
        return "cards/ai-fallback";
    }

    private void addPreviewModel(Long cardId, Model model) {
        BusinessCard card = aiCardService.getCard(cardId);
        model.addAttribute("card", card);
        model.addAttribute("extraItems", aiCardService.getExtraItems(cardId));
        model.addAttribute("previewDocument", aiCardService.getPreviewDocument(cardId));
    }

    private void createQrCode(Long cardId, HttpServletRequest request) {
        BusinessCard card = aiCardService.getCard(cardId);
        String targetUrl = publicCardUrlService.buildPublicCardUrl(request, card.getPublicUrl());
        cardQrService.createOrUpdateQr(cardId, targetUrl);
    }

    private void validateAtLeastOneApiKey(String openAiApiKey, String geminiApiKey) {
        if (isBlank(openAiApiKey) && isBlank(geminiApiKey)) {
            throw new IllegalStateException("GPT API Key 또는 Gemini API Key 중 하나는 입력해야 합니다.");
        }
    }

    private Long requireCurrentUserId(RedirectAttributes redirectAttributes) {
        Long userId = currentUserService.getCurrentUserIdOrNull();
        if (userId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "로그인 후 명함을 생성할 수 있습니다.");
        }
        return userId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
