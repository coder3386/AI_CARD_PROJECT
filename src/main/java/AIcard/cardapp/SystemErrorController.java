package AIcard.cardapp;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
public class SystemErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        // 에러 상태 코드를 가져옵니다. (예: 404, 500 등)
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

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
        return "error/error";
    }
}
