package AIcard.cardapp.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoticeReadRequestDTO {
    private Long noticeId;
    private Long userId;
}