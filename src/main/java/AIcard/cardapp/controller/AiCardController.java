package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.CardCreateRequest;
import AIcard.cardapp.DTO.CardDrawingCreateRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AiCardController {

    private final AiCardService aiCardService;

    public AiCardController(AiCardService aiCardService) {
        this.aiCardService = aiCardService;
    }

    @GetMapping("/cards/select-type")
    public String selectType() {
        return "cards/select-type";
    }

    @GetMapping("/cards/new")
    public String newCard(Model model) {
        model.addAttribute("cardCreateRequest", new CardCreateRequest());
        return "cards/ai-new";
    }

    @PostMapping("/cards/generate")
    public String generate(@ModelAttribute CardCreateRequest request, RedirectAttributes redirectAttributes) {
        try {
            Long cardId = aiCardService.generate(request);
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
    public String generateDrawing(@ModelAttribute CardDrawingCreateRequest request, RedirectAttributes redirectAttributes) {
        try {
            Long cardId = aiCardService.generateDrawing(request);
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
    public String updateText(@PathVariable Long cardId, @ModelAttribute CardUpdateTextRequest request) {
        aiCardService.updateText(cardId, request);
        return "redirect:/cards/" + cardId + "/preview";
    }

    @PostMapping("/cards/{cardId}/delete")
    public String deleteCard(@PathVariable Long cardId) {
        aiCardService.deleteCard(cardId);
        return "redirect:/cards/new";
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

    private void addPreviewModel(Long cardId, Model model) {
        BusinessCard card = aiCardService.getCard(cardId);
        model.addAttribute("card", card);
        model.addAttribute("extraItems", aiCardService.getExtraItems(cardId));
        model.addAttribute("previewDocument", aiCardService.getPreviewDocument(cardId));
    }
}
