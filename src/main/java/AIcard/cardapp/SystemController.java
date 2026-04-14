/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package AIcard.cardapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 *
 * @author namw2
 */
@Slf4j
@Controller
public class SystemController {

    @GetMapping("/")
    public String index(Model model) {
        return main(model);
    }
    
    @GetMapping("/main")
    public String main(Model model) {
        // 화면에 전달할 데이터
        //log.debug("JSP 교재 전체 프로젝트 목록 보여주기");
        setData(model);
        return "index"; // templates/index.html을 찾아감
    }
    
    private void setData(Model model) {
        model.addAttribute("title", "Alcard Card App");
        model.addAttribute("status", "서버가 성공적으로 가동 중입니다!");
    }

}
