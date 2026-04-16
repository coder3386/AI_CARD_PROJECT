package AIcard.cardapp.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Slf4j
@Controller
public class TestController {

    @GetMapping("/viewDemo")
    public String viewDemo() {
        log.debug(">>> viewDemo 컨트롤러 진입 성공!");
        return "demotestpage/viewDemo";
    }

    @GetMapping("/test-403")
    public void test403(HttpServletResponse response) throws IOException {
        // 403 Forbidden 에러를 강제로 발생시킴
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @GetMapping("/test-400")
    public void test400(HttpServletResponse response) throws IOException {
        // 400 Bad Request 에러를 강제로 발생시킴
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

}
