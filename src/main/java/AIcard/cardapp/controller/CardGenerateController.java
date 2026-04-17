package AIcard.cardapp.controller;

import AIcard.cardapp.dto.sfr004.CardGenerateRequestDto;
import AIcard.cardapp.dto.sfr004.CardGenerateResultDto;
import AIcard.cardapp.service.sfr004.CardGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/generate")
public class CardGenerateController {

    private final CardGenerateService cardGenerateService;

    @GetMapping
    public String showGeneratePage(Model model) {
        model.addAttribute("requestDto", new CardGenerateRequestDto());
        return "sfr004/generate";
    }

    @PostMapping("/load-my-info")
    public String loadMyInfo(Model model) {
        CardGenerateRequestDto myInfo = cardGenerateService.loadMyInfoOrDefault();
        model.addAttribute("requestDto", myInfo);
        return "sfr004/generate";
    }

    @PostMapping
    public String generateCard(@ModelAttribute("requestDto") CardGenerateRequestDto requestDto,
                               Model model) {

        CardGenerateResultDto result = cardGenerateService.generateCard(requestDto);
        model.addAttribute("requestDto", requestDto);
        model.addAttribute("result", result);

        return "sfr004/preview";
    }
}