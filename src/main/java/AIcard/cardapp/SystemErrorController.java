package AIcard.cardapp;

import AIcard.cardapp.DTO.ErrorDTO;
import AIcard.cardapp.DTO.ErrorType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

@Slf4j
@Controller
public class SystemErrorController implements ErrorController {

    @GetMapping("/test-{code}")
    public void forceError(@PathVariable int code, HttpServletResponse response) throws IOException {
        log.info("에러테스트용 코드: " + code);
        response.sendError(code);
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        // 에러 상태 코드를 가져옵니다. (예: 404, 500 등)

        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        int statusCode = status != null ? Integer.parseInt(status.toString()) : 000;
        log.info("에러 핸들러 동작 - 상태 코드: {}", statusCode);
        try {
            ErrorType type = ErrorType.of(statusCode);
            model.addAttribute("error", new ErrorDTO(type));
        } catch (Exception e) {
            log.error("ErrorType 변환 중 오류 발생: {}", e.getMessage());
            // 예외 발생 시 기본 에러 정보 세팅
            model.addAttribute("error", new ErrorDTO(ErrorType.UNDEFINED_ERROR));
        }
        return "error/error";
    }
}
