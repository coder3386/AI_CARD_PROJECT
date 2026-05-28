package AIcard.cardapp.controller;

import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.service.ManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

@Controller
@Slf4j
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final ManagerService managerService;

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
        List<UsersMember> users = managerService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("contentFragment", "Manager/fragments/rbac :: rbacContent");
        return "manager/manager";
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
        model.addAttribute("activeMenu", "qna");
        model.addAttribute("currentMenuName", "문의 대답 페이지");
        model.addAttribute("contentFragment", "Manager/fragments/qna :: qnaContent");
        return "manager/manager";
    }

    // 7. 전체공지 페이지
    @GetMapping("/notice")
    public String noticePage(Model model) {
        model.addAttribute("activeMenu", "notice");
        model.addAttribute("currentMenuName", "전체공지 페이지");
        model.addAttribute("contentFragment", "Manager/fragments/notice :: noticeContent");
        return "manager/manager";
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
