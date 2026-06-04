package AIcard.cardapp.controller;

import AIcard.cardapp.entity.Inquiry;
import AIcard.cardapp.entity.Notice;
import AIcard.cardapp.service.InquiryService;
import AIcard.cardapp.service.NoticeService;
import AIcard.cardapp.DTO.ActiveUserDTO;
import AIcard.cardapp.DTO.LogResponseDTO;
import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.service.ManagerLogService;
import AIcard.cardapp.service.ManagerService;
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
import java.security.Principal;
import java.util.List;

@Controller
@Slf4j
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final NoticeService noticeService;
    private final InquiryService inquiryService;
    private final ManagerService managerService;
    private final ManagerLogService logService;

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
        return "Manager/Manager"; // 소문자로 통일
    }

    // 1. 운영자 권한 관리 (RBAC)
    @GetMapping("/rbac")
    public String rbacManagement(Model model) {
        model.addAttribute("activeMenu", "rbac");
        model.addAttribute("currentMenuName", "운영자 권한 관리 (RBAC)");
        List<UsersMember> users = managerService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("contentFragment", "Manager/fragments/rbac :: rbacContent");
        return "Manager/Manager";
    }

    @PostMapping("/rbac/update-role")
    public String updateRole(@RequestParam("userId") Long userId,
                             @RequestParam("newRole") String newRole,
                             Principal principal) {

        // 인증 정보가 없다면 (로그인 안 됨)
        if (principal == null) {
            return "redirect:/login";
        }

        String loggedInManagerId = principal.getName();

        try {
            managerService.changeUserRole(userId, newRole, loggedInManagerId);
        } catch (Exception e) {
            return "redirect:/manager/rbac?error=" + e.getMessage();
        }

        return "redirect:/manager/rbac";
    }

    // 2. 활동 로그 확인 (Manager Activity Log)
    @GetMapping("/log/manager")
    public String managerLog(@RequestParam(value = "logDate", required = false) String logDate, Model model) {
        List<LogResponseDTO> logList = logService.getParsedLogs("manager", logDate);
        List<String> archiveDates = logService.getArchiveDates("manager");

        model.addAttribute("logs", logList);
        model.addAttribute("archiveDates", archiveDates);
        model.addAttribute("selectedDate", logDate);
        model.addAttribute("currentType", "manager");

        model.addAttribute("activeMenu", "managerLog");
        model.addAttribute("currentMenuName", "활동 로그 확인 (Manager)");
        model.addAttribute("contentFragment", "Manager/fragments/manager_log :: logContent");
        return "Manager/Manager";
    }

    // 3. 활동 로그 확인 (User Activity Log)
    @GetMapping("/log/user")
    public String userLog(@RequestParam(value = "logDate", required = false) String logDate, Model model) {
        List<LogResponseDTO> logList = logService.getParsedLogs("user", logDate);
        List<String> archiveDates = logService.getArchiveDates("user");

        model.addAttribute("logs", logList);
        model.addAttribute("archiveDates", archiveDates);
        model.addAttribute("selectedDate", logDate);
        model.addAttribute("currentType", "user");

        model.addAttribute("activeMenu", "userLog");
        model.addAttribute("currentMenuName", "활동 로그 확인 (User)");
        model.addAttribute("contentFragment", "Manager/fragments/user_log :: logContent");
        return "Manager/Manager";
    }

    // 8. 에러로그
    @GetMapping("/log/error")
    public String errorlog(@RequestParam(value = "logDate", required = false) String logDate, Model model) {
        List<LogResponseDTO> logList = logService.getParsedLogs("error", logDate);
        List<String> archiveDates = logService.getArchiveDates("error");

        model.addAttribute("logs", logList);
        model.addAttribute("archiveDates", archiveDates);
        model.addAttribute("selectedDate", logDate);
        model.addAttribute("currentType", "error");

        model.addAttribute("activeMenu", "errorLog");
        model.addAttribute("currentMenuName", "에러로그");
        model.addAttribute("contentFragment", "Manager/fragments/error_log :: logContent");
        return "Manager/Manager";
    }

    // 4. 사용자 세션 조회
    @GetMapping("/session")
    public String sessionLookup(Model model) {
        model.addAttribute("activeMenu", "session");
        model.addAttribute("currentMenuName", "사용자 세션 조회");

        // ★ 현재 접속 중인 세션 유저 DTO 리스트를 조회해서 모델에 주입
        List<ActiveUserDTO> activeUsers = managerService.getActiveUsers();
        model.addAttribute("activeUsers", activeUsers);

        model.addAttribute("contentFragment", "Manager/fragments/session :: sessionContent");
        return "Manager/Manager";
    }

    // 5. 커뮤니케이션 도구
    @GetMapping("/comm")
    public String communicationTools(Model model) {
        model.addAttribute("activeMenu", "communication");
        model.addAttribute("currentMenuName", "커뮤니케이션 도구");
        model.addAttribute("contentFragment", "Manager/fragments/comm :: commContent");
        return "Manager/Manager";
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
        return "Manager/Manager";
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
        return "Manager/Manager";
    }
    @GetMapping("/cards")
    public String cardStatusPage(Model model) {
        model.addAttribute("activeMenu", "cards");
        model.addAttribute("currentMenuName", "\uC6B4\uC601\uC790 \uBA85\uD568 \uBE44\uD65C\uC131\uD654");
        model.addAttribute("cards", managerService.getAllCards());
        model.addAttribute("contentFragment", "Manager/fragments/cards :: cardContent");
        return "Manager/Manager";
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

    @PostMapping("/cards/update-status")
    public String updateCardStatus(
            @RequestParam("cardId") Long cardId,
            @RequestParam("newStatus") String newStatus,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String title = managerService.changeCardStatus(cardId, newStatus);
            String statusLabel = "ACTIVE".equals(newStatus) ? "\uD65C\uC131\uD654" : "\uBE44\uD65C\uC131\uD654";
            redirectAttributes.addFlashAttribute("successMessage", title + " \uBA85\uD568\uC774 " + statusLabel + "\uB418\uC5C8\uC2B5\uB2C8\uB2E4.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/manager/cards";
    }
}
