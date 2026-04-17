package AIcard.cardapp.dto.sfr004;

import lombok.Data;

@Data
public class CardGenerateRequestDto {
    private String name;
    private String email;
    private String phone;
    private String title;
    private String intro;
    private String stylePrompt;
}