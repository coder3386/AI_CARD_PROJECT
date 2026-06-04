package AIcard.cardapp.controller;

import AIcard.cardapp.service.CardQrService;
import AIcard.cardapp.service.CurrentUserService;
import AIcard.cardapp.service.PublicCardUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class CardQrController {

    private final CardQrService cardQrService;
    private final CurrentUserService currentUserService;
    private final PublicCardUrlService publicCardUrlService;

    public CardQrController(
            CardQrService cardQrService,
            CurrentUserService currentUserService,
            PublicCardUrlService publicCardUrlService
    ) {
        this.cardQrService = cardQrService;
        this.currentUserService = currentUserService;
        this.publicCardUrlService = publicCardUrlService;
    }

    @GetMapping("/cards/my/{cardId}/qr-image")
    public ResponseEntity<byte[]> myCardQrImage(@PathVariable Long cardId, HttpServletRequest request) {
        Long userId = currentUserService.getCurrentUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            String publicUrl = cardQrService.getOwnedPublicUrl(userId, cardId);
            String targetUrl = publicCardUrlService.buildPublicCardUrl(request, publicUrl);
            byte[] qrImage = cardQrService.readOrCreateOwnedQrImage(userId, cardId, targetUrl);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(qrImage);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
