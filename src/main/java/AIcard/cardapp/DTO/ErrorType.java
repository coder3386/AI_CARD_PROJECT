package AIcard.cardapp.DTO;

import lombok.Getter;

@Getter
public enum ErrorType {
    // --- 4xx Client Errors (사용자 요청 오류) ---
    BAD_REQUEST(400, "잘못된 요청", "입력하신 정보를 다시 확인해 주세요."),
    UNAUTHORIZED(401, "권한 없음", "해당 기능을 이용하시려면 로그인을 해주세요."),
    FORBIDDEN(403, "접근 거부", "이 페이지를 볼 수 있는 권한이 부족한 것 같아요."),
    NOT_FOUND(404, "찾을수 없음", "요청하신 페이지가 사라졌거나 주소가 잘못되었습니다."),
    METHOD_NOT_ALLOWED(405, "허용되지 않는 방식", "잘못된 경로로 접근하셨습니다."),
    NOT_ACCEPTABLE(406, "받아들일수 없음", "받을수 없는 요청입니다."),
    REQUEST_TIMEOUT(408, "요청시간 초과", "요청중 시간이 초과되었습니다."),
    TOO_MANY_REQUEST(429, "너무 많은 요청", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),
    // --- 5xx Server Errors (서버 내부 오류) ---
    INTERNAL_SERVER_ERROR(500, "내부 서버 오류", "시스템 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요"),
    SERVICE_UNAVAILABLE(503, "점검 중입니다.", "서버가 잠시 다운되었습니다. 잠시 후 다시 이용해 주세요."),
    BAD_GATEWAY(502, "게이트웨이 불량", "서버로부터 잘못된 응답을 받았습니다."),

    // --- Custom Errors (우리 서비스 전용) ---
    NOT_IMPLEMENTED(501, "미지원 기능", "해당 기능은 다음 업데이트에 추가될 예정입니다."),

    UNUSED(418, "I'm a teapot", "찻주전자로 커피를 만들 수 없음"),
    UNDEFINED_ERROR(000, "예상치 못한 오류", "알지 못한 오류가 발생하였습니다.");


    private final int code;
    private final String title;
    private final String description;

    ErrorType(int code, String title, String description) {
        this.code = code;
        this.title = title;
        this.description = description;
    }

    // 상태 코드로 해당 Enum을 찾아주는 정적 메서드
    public static ErrorType of(int code) {
        for (ErrorType type : ErrorType.values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return UNDEFINED_ERROR; // 기본값
    }
}

