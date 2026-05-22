package AIcard.cardapp.controller;


import AIcard.cardapp.DTO.UsersMemberRequestDTO;
import AIcard.cardapp.DTO.UsersMemberUpdateDTO;
import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.repository.UsersMemberRepository;
import AIcard.cardapp.service.UsersMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/card")
@RequiredArgsConstructor
public class UsersMemberController {
    private final UsersMemberService memberService;
    private final UsersMemberRepository memberRepository;

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

    @GetMapping("/edit")
    public String editPage(@AuthenticationPrincipal User user, Model model) {
        // 현재 로그인한 사용자의 아이디로 DB에서 정보를 가져옵니다.
        UsersMember member = memberRepository.findByLoginId(user.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        model.addAttribute("member", member); // 기존 정보를 화면에 전달
        return "user/UserInfoEdit";
    }

    @PostMapping("/edit")
    public String updateInfo(@AuthenticationPrincipal User user, UsersMemberUpdateDTO dto, Model model) {

        boolean isUpdated = memberService.updateMember(user.getUsername(), dto);

        if (!isUpdated) {
            model.addAttribute("error", "wrong_password");

            UsersMember member = memberRepository.findByLoginId(user.getUsername()).get();
            model.addAttribute("member", member);

            return "user/UserInfoEdit";
        }

        return "redirect:/main";
    }

    @GetMapping("/policy")
    public String policyPage() {
        return "policy/privacypolicy"; // 만들어두신 개인정보처리방침 html 파일명 (예: policy.html)
    }

}