package AIcard.cardapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class UserController {

    @GetMapping("/login")
    public String login() {
        return "user/login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "임시: 에러페이지로 ㄱ"; //이렇게 하면 http 500 응답
        //return "user/signup";

    }

    @GetMapping("/card/testid")
    public String viewTestCard() {
        // src/main/resources/templates/card/testid.html 파일을 찾아 띄워줍니다.
        return "card/testid";
    }
}
