package AIcard.cardapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class PolicyController {

    @GetMapping("/terms")
    public String terms() {
        log.info("이용약관으로 이동?");
        return "policy/terms";
    }

    @GetMapping("/privacypolicy")
    public String privacypolicy() {
        log.info("개인정보처리방침으로 이동?");
        return "policy/privacypolicy";
    }
}