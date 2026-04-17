package AIcard.cardapp.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorDTO {

    private int error_code;
    private String error_title, error_message;

    public ErrorDTO(ErrorType type) {
        this.error_code = type.getCode();
        this.error_title = type.getTitle();
        this.error_message = type.getDescription();

    }

}
