package AIcard.cardapp.controller;


import AIcard.cardapp.DTO.UsersMemberRequestDTO;
import AIcard.cardapp.service.UsersMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/card")
@RequiredArgsConstructor
public class UsersMemberController {
    private final UsersMemberService memberService;

    @GetMapping("/join")
    public String joinForm() {
        return "user/join";
    }

    @PostMapping("/join")
    public String join(UsersMemberRequestDTO dto) {
        //memberService.join(dto.getUsername(), dto.getPassword(), dto.getName());
        System.out.println("회원가입 요청 아이디: " + dto.getLoginid());
        memberService.join(dto);
        return "redirect:/card/login";
    }

    @GetMapping("/login")
    public String loginForm() {
        return "user/login";
    }
}
