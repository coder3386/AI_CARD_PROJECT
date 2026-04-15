package AIcard.cardapp;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class TestController {
    @GetMapping("/pipboy")
    public String pipboyPage() {
        return "pipboytestpage"; // templates/pipboytestpage.html을 찾아감
    }
}
