package AIcard.cardapp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class inactivatecardController {

    @GetMapping("/dont")
    public String terms() {
        log.info("비활성화 명함 페이지로 이동?");
        return "CardPage/InActivatePage";
    }

}
