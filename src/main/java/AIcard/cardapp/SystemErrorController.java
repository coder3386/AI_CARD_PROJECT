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
        response.sendError(code);
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        // 에러 상태 코드를 가져옵니다. (예: 404, 500 등)
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        int statusCode = status != null ? Integer.parseInt(status.toString()) : 500;

        ErrorType type = ErrorType.of(statusCode);

        model.addAttribute("error", new ErrorDTO(type));
        return "error/error.html";

        /*
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            // 1. 404 - Not Found (페이지를 찾을 수 없음)
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "error/404";
            }

            // 2. 403 - Forbidden (접근 권한이 없음)
            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return "error/403";
            }

            // 3. 500 - Internal Server Error (서버 내부 오류)
            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                return "error/500";
            }
        }

        // 4. 그 외 나머지 모든 에러 (400, 405, 503 등)
        return "error/error";*/
    }
}
