/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package AIcard.cardapp;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 *
 * @author namw2
 */
@Controller
@Slf4j
public class SystemErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        model.addAttribute("code", status.toString());
        model.addAttribute("msg", HttpStatus.valueOf(Integer.parseInt(status.toString())));
        //model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("error", "Not Found");
        model.addAttribute("message", "The requested resource was not found");
        return "error/error";
    }

    /*
    @RequestMapping("/error")
    public String errorPage(HttpServletRequest request, RedirectAttributes attrs) {
        //spring이 필요한 객체를 자동으로 만들어서 주입해준다. 개발자가 일일히 객체 생성하지 않아도 해줌 -> 의존성 주입(Dependency Injection)
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        attrs.addFlashAttribute("msg",
                "오류가 발생하여 컨텍스트 루트로 이동하였습니다: 오류코드 = " + status.toString());
        return "redirect:/"; //    :/ -> ood/
    }
     */
}
