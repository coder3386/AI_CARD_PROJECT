package AIcard.cardapp.DTO;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LogResponseDTO {
    private String timestamp;    // 로그 발생 시간
    private String thread;       // 스레드 (에러용)
    private String level;        // 로그 레벨 (에러용)
    private String loggerName;   // 발생 위치 (에러용)
    private String message;      // 로그 본문 내용
}
