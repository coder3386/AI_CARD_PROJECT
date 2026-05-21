package AIcard.cardapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class TestController {

    @GetMapping("/testpage")
    public String testPage() {
        log.debug(">>> 테스트페이지 컨트롤러 진입 성공!");
        return "testpage";
    }

    @GetMapping("/managertest")
    public String managertest() {
        return "/Manager/Manager";
    }

    @GetMapping("/viewDemo")
    public String viewDemo() {
        log.debug(">>> viewDemo 컨트롤러 진입 성공!");
        return "demotestpage/viewDemo";
    }


}
