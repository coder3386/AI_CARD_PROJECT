package AIcard.cardapp.DTO;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardDownloadDTO {
    private String name;
    private String position;
    private String email;
    private String phone;
    private String imagePath;
}