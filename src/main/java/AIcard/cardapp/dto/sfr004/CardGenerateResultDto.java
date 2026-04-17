package AIcard.cardapp.dto.sfr004;

import lombok.Data;

@Data
public class CardGenerateResultDto {
    private String name;
    private String title;
    private String intro;
    private String themeColor;
    private String layoutType;
    private String email;
    private String phone;
}