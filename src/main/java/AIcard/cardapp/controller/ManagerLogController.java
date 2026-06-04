package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.LogResponseDTO;
import AIcard.cardapp.service.ManagerLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/manager/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')") // 보안 설정 유지
public class ManagerLogController {
    private final ManagerLogService logService; // 서비스 의존성 주입

    @GetMapping
    public String showLogs(@RequestParam(value = "type", defaultValue = "manager") String type,
                           @RequestParam(value = "logDate", required = false) String logDate, // 💡 과거 날짜 파라미터 추가
                           Model model) {
        // 1. 비즈니스 로직(파일 읽기, 파싱, 정렬)은 서비스에 전적으로 위임합니다.
        List<LogResponseDTO> logList = logService.getParsedLogs(type, logDate);

        List<String> archiveDates = logService.getArchiveDates(type);
        // 2. 받아온 껍데기 데이터를 화면(Model)에 바인딩합니다.
        model.addAttribute("logs", logList);
        model.addAttribute("archiveDates", archiveDates);
        model.addAttribute("selectedDate", logDate);
        model.addAttribute("currentType", type);

        return "manager/fragments/" + type + "_log";
    }
}
