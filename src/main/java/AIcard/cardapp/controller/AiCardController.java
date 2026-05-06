package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.CardCreateRequest;
import AIcard.cardapp.DTO.CardUpdateTextRequest;
import AIcard.cardapp.entity.BusinessCard;
import AIcard.cardapp.service.AiCardService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AiCardController {

    private final AiCardService aiCardService;

    public AiCardController(AiCardService aiCardService) {
        this.aiCardService = aiCardService;
    }

    @GetMapping("/cards/new")
    public String newCard(Model model) {
        model.addAttribute("cardCreateRequest", new CardCreateRequest());
        return "cards/ai-new";
    }

    @PostMapping("/cards/generate")
    public String generate(@ModelAttribute CardCreateRequest request) {
        Long cardId = aiCardService.generate(request);
        return "redirect:/cards/" + cardId + "/preview";
    }

    @GetMapping("/cards/{cardId}/preview")
    public String preview(@PathVariable Long cardId, Model model) {
        BusinessCard card = aiCardService.getCard(cardId);
        model.addAttribute("card", card);
        model.addAttribute("extraItems", aiCardService.getExtraItems(cardId));
        model.addAttribute("previewDocument", aiCardService.getPreviewDocument(cardId));
        return "cards/ai-preview";
    }

    @PostMapping("/cards/{cardId}/update-text")
    public String updateText(@PathVariable Long cardId, @ModelAttribute CardUpdateTextRequest request) {
        aiCardService.updateText(cardId, request);
        return "redirect:/cards/" + cardId + "/preview";
    }

    @GetMapping("/public/card/{publicUrl}")
    public ResponseEntity<String> publicCard(@PathVariable String publicUrl) {
        try {
            String html = aiCardService.readPublicCard(publicUrl);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage());
        }
    }
}
