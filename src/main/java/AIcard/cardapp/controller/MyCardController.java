package AIcard.cardapp.controller;

import AIcard.cardapp.service.CurrentUserService;
import AIcard.cardapp.service.MyCardService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MyCardController {

    private final MyCardService myCardService;
    private final CurrentUserService currentUserService;

    public MyCardController(MyCardService myCardService, CurrentUserService currentUserService) {
        this.myCardService = myCardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/cards/my")
    public String myCards(
            @RequestParam(defaultValue = "0") int page,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = requireLogin(redirectAttributes);
        if (userId == null) {
            return "redirect:/card/login";
        }

        Page<?> cards = myCardService.getMyCards(userId, page);
        model.addAttribute("cards", cards);
        model.addAttribute("myCardService", myCardService);
        return "cards/my-cards";
    }

    @PostMapping("/cards/my/{cardId}/visibility")
    public String updatePublicStatus(
            @PathVariable Long cardId,
            @RequestParam boolean publicCard,
            @RequestParam(defaultValue = "0") int page,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = requireLogin(redirectAttributes);
        if (userId == null) {
            return "redirect:/card/login";
        }

        String title = myCardService.updatePublicStatus(userId, cardId, publicCard);
        String status = publicCard ? "공개" : "비공개";
        redirectAttributes.addFlashAttribute("successMessage", title + "의 상태가 " + status + "로 변경되었습니다.");
        return "redirect:/cards/my?page=" + Math.max(page, 0);
    }

    @PostMapping("/cards/my/{cardId}/delete")
    public String deleteMyCard(
            @PathVariable Long cardId,
            @RequestParam(defaultValue = "0") int page,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = requireLogin(redirectAttributes);
        if (userId == null) {
            return "redirect:/card/login";
        }

        String title = myCardService.deleteMyCard(userId, cardId);
        redirectAttributes.addFlashAttribute("successMessage", title + "이 삭제되었습니다.");
        return "redirect:/cards/my?page=" + Math.max(page, 0);
    }

    private Long requireLogin(RedirectAttributes redirectAttributes) {
        Long userId = currentUserService.getCurrentUserIdOrNull();
        if (userId == null) {
            redirectAttributes.addFlashAttribute("loginRequiredMessage", "로그인 후 이용해주세요.");
        }
        return userId;
    }
}
