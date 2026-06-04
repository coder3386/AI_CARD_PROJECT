package AIcard.cardapp.controller;

import AIcard.cardapp.entity.Inquiry;
import AIcard.cardapp.entity.User;
import AIcard.cardapp.repository.UserRepository;
import AIcard.cardapp.service.InquiryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
public class InquiryController {

    private final InquiryService inquiryService;
    // 🚨 유저 정보를 찾기 위해 UserRepository를 추가합니다.
    private final UserRepository userRepository;

    @Autowired
    public InquiryController(InquiryService inquiryService, UserRepository userRepository) {
        this.inquiryService = inquiryService;
        this.userRepository = userRepository; // 의존성 주입
    }

    // 1. 문의 작성 페이지 이동 + 본인 문의 목록 조회 (GET)
    @GetMapping("/inquiry/create")
    public String showInquiryPage(Model model, Principal principal) {

        // 1) 비로그인 상태면 로그인 페이지로 이동
        if (principal == null) {
            return "redirect:/card/login";
        }

        // 2) 시큐리티를 통해 현재 로그인한 사용자의 login_id를 가져옴
        String loginId = principal.getName();

        // 3) DB에서 loginId로 User 객체를 찾아오고, 없다면 에러 발생
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다. loginId: " + loginId));

        // 4) User 엔티티에서 고유 번호(Long 타입의 userId)를 꺼냄
        Long realUserId = user.getUserId();

        // 5) 알아낸 진짜 userId로 본인이 쓴 문의 내역만 가져오기
        List<Inquiry> actualList = inquiryService.getMyInquiries(realUserId);

        model.addAttribute("myInquiries", actualList);
        return "userinquiry";
    }

    // 2. 문의 등록 처리 (POST)
    @PostMapping("/inquiry/create")
    public String submitInquiry(@ModelAttribute Inquiry inquiry,
                                RedirectAttributes redirectAttributes,
                                Principal principal) {

        // 1) 비로그인 상태 체크
        if (principal == null) {
            return "redirect:/card/login";
        }

        // 2) 현재 로그인한 사용자의 login_id를 가져옴
        String loginId = principal.getName();

        // 3) DB에서 유저 정보를 찾고 고유 번호 꺼내기
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다. loginId: " + loginId));
        Long realUserId = user.getUserId();

        // 4) 알아낸 진짜 userId를 담아서 문의사항 DB에 저장!
        inquiryService.saveInquiry(inquiry, realUserId);

        redirectAttributes.addFlashAttribute("successMessage", "작성이 완료되었습니다.");
        return "redirect:/inquiry/create";
    }
}