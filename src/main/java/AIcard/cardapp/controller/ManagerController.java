package AIcard.cardapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    @GetMapping({"", "/"})
    public String manager() {
        return "Manager/Manager";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("activeMenu", "dashboard");
        model.addAttribute("currentMenuName", "대시보드");
        model.addAttribute("pageTitle", "AICARD 매니저 시스템 - 대시보드");
        return "manager/manager"; // templates/manager/manager.html 경로 기준
    }

    // 1. 운영자 권한 관리 (RBAC)
    @GetMapping("/rbac")
    public String rbacManagement(Model model) {
        model.addAttribute("activeMenu", "rbac");
        model.addAttribute("currentMenuName", "운영자 권한 관리 (RBAC)");
        return "manager/manager";
    }

    // 2. 활동 로그 확인 (Manager Activity Log)
    @GetMapping("/log/manager")
    public String managerLog(Model model) {
        model.addAttribute("activeMenu", "managerLog");
        model.addAttribute("currentMenuName", "활동 로그 확인 (Manager)");
        return "manager/manager";
    }

    // 3. 활동 로그 확인 (User Activity Log)
    @GetMapping("/log/user")
    public String userLog(Model model) {
        model.addAttribute("activeMenu", "userLog");
        model.addAttribute("currentMenuName", "활동 로그 확인 (User)");
        return "manager/manager";
    }

    // 4. 사용자 세션 조회
    @GetMapping("/session")
    public String sessionLookup(Model model) {
        model.addAttribute("activeMenu", "session");
        model.addAttribute("currentMenuName", "사용자 세션 조회");
        return "manager/manager";
    }

    // 5. 커뮤니케이션 도구
    @GetMapping("/comm")
    public String communicationTools(Model model) {
        model.addAttribute("activeMenu", "communication");
        model.addAttribute("currentMenuName", "커뮤니케이션 도구");
        return "manager/manager";
    }

    // 6. 문의 대답 페이지
    @GetMapping("/qna")
    public String qnaPage(Model model) {
        model.addAttribute("activeMenu", "qna");
        model.addAttribute("currentMenuName", "문의 대답 페이지");
        return "manager/manager";
    }

    // 7. 전체공지 페이지
    @GetMapping("/notice")
    public String noticePage(Model model) {
        model.addAttribute("activeMenu", "notice");
        model.addAttribute("currentMenuName", "전체공지 페이지");
        return "manager/manager";
    }

    // 8. 통계
    @GetMapping("/stats")
    public String statisticsPage(Model model) {
        model.addAttribute("activeMenu", "stats");
        model.addAttribute("currentMenuName", "통계");
        return "manager/manager";
    }
}
