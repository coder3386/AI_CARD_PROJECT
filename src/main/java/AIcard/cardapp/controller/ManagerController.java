package AIcard.cardapp.controller;

import AIcard.cardapp.entity.Inquiry;
import AIcard.cardapp.entity.Notice;
import AIcard.cardapp.service.InquiryService;
import AIcard.cardapp.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@Slf4j
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final NoticeService noticeService;
    private final InquiryService inquiryService;

    @GetMapping({"", "/"})
    public String manager(Model model) {
        return dashboard(model);
        //return "Manager/Manager";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("activeMenu", "main"); // HTML의 'main'과 일치시킴
        model.addAttribute("currentMenuName", "대시보드");
        model.addAttribute("pageTitle", "AICARD 매니저 시스템 - 대시보드");
        model.addAttribute("contentFragment", "Manager/fragments/dashboard :: dashboardContent");
        return "manager/manager"; // 소문자로 통일
    }

    // 1. 운영자 권한 관리 (RBAC)
    @GetMapping("/rbac")
    public String rbacManagement(Model model) {
        model.addAttribute("activeMenu", "rbac");
        model.addAttribute("currentMenuName", "운영자 권한 관리 (RBAC)");
        model.addAttribute("contentFragment", "Manager/fragments/rbac :: rbacContent");
        return "manager/manager";
    }

    // 2. 활동 로그 확인 (Manager Activity Log)
    @GetMapping("/log/manager")
    public String managerLog(Model model) {
        model.addAttribute("activeMenu", "managerLog");
        model.addAttribute("currentMenuName", "활동 로그 확인 (Manager)");
        model.addAttribute("contentFragment", "Manager/fragments/manager_log :: logContent");
        return "manager/manager";
    }

    // 3. 활동 로그 확인 (User Activity Log)
    @GetMapping("/log/user")
    public String userLog(Model model) {
        model.addAttribute("activeMenu", "userLog");
        model.addAttribute("currentMenuName", "활동 로그 확인 (User)");
        model.addAttribute("contentFragment", "Manager/fragments/user_log :: logContent");
        return "manager/manager";
    }

    // 4. 사용자 세션 조회
    @GetMapping("/session")
    public String sessionLookup(Model model) {
        model.addAttribute("activeMenu", "session");
        model.addAttribute("currentMenuName", "사용자 세션 조회");
        model.addAttribute("contentFragment", "Manager/fragments/session :: sessionContent");
        return "manager/manager";
    }

    // 5. 커뮤니케이션 도구
    @GetMapping("/comm")
    public String communicationTools(Model model) {
        model.addAttribute("activeMenu", "communication");
        model.addAttribute("currentMenuName", "커뮤니케이션 도구");
        model.addAttribute("contentFragment", "Manager/fragments/comm :: commContent");
        return "manager/manager";
    }

    // 6. 문의 대답 페이지
    @GetMapping("/qna")
    public String qnaPage(Model model) {

        // 🚨 [새로 추가된 부분] 서비스에서 모든 문의 내역을 가져와 화면으로 넘깁니다.
        List<Inquiry> allInquiries = inquiryService.getAllInquiries();
        model.addAttribute("allInquiries", allInquiries);

        // [기존 코드 유지] 레이아웃 및 파라미터 설정
        model.addAttribute("activeMenu", "qna");
        model.addAttribute("currentMenuName", "문의 대답 페이지");
        // 주의: HTML에서 이 조각(fragment)의 이름이 맞는지 확인하세요! (예: th:fragment="qnaContent")
        model.addAttribute("contentFragment", "Manager/fragments/qna :: qnaContent");

        return "manager/manager";
    }

    // 6-1. 관리자 답변 등록 처리
    @PostMapping("/qna/answer") // GET 주소가 /qna 이므로 거기에 맞췄습니다.
    public String submitAnswer(@RequestParam("id") Long id,
                               @RequestParam("answer") String answer,
                               RedirectAttributes redirectAttributes) {

        inquiryService.saveAnswer(id, answer);
        redirectAttributes.addFlashAttribute("successMessage", "답변이 성공적으로 등록되었습니다.");

        // 🚨 주의: 컨트롤러 클래스 위에 @RequestMapping("/manager")가 있다면 "redirect:/manager/qna" 로 하시고,
        // 없다면 "redirect:/qna" 로 적어주세요. (새로고침할 겟매핑 주소입니다)
        return "redirect:/manager/qna";
    }

    // 7. 전체 공지 페이지
    @GetMapping("/notice")
    public String noticeWriteForm(Model model) {
        model.addAttribute("activeMenu", "notice");
        model.addAttribute("currentMenuName", "전체공지 작성");
        model.addAttribute("contentFragment", "Manager/fragments/writenotice :: writeNoticeContent");
        return "manager/manager";
    }
    // 7-1. 전체 공지 등록 처리
    @PostMapping("/notice/save")
    public String saveNotice(@RequestParam("title") String title,
                             @RequestParam("content") String content,
                             // HTML의 datetime-local 값을 스프링의 LocalDateTime으로 안전하게 변환
                             @RequestParam("expiresAt") @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime expiresAt,
                             RedirectAttributes redirectAttributes) {

        noticeService.createNotice(title, content, expiresAt);

        // 저장이 완료되면 화면에 성공 메시지를 전달하고 다시 작성 폼으로 리다이렉트
        redirectAttributes.addFlashAttribute("successMessage", "전체 공지가 성공적으로 등록되었습니다. 사용자 팝업에 즉시 반영됩니다.");

        return "redirect:/manager/notice";
    }

    // 8. 통계
    @GetMapping("/stats")
    public String statisticsPage(Model model) {
        model.addAttribute("activeMenu", "stats");
        model.addAttribute("currentMenuName", "통계");
        model.addAttribute("contentFragment", "Manager/fragments/stats :: statsContent");
        return "manager/manager";
    }
}
