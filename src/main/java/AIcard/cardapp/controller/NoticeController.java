package AIcard.cardapp.controller;

import AIcard.cardapp.DTO.NoticeProjection;
import AIcard.cardapp.service.NoticeService;
import AIcard.cardapp.DTO.NoticeReadRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    // 1. 사용자 팝업용 공지사항 조회 API
    // GET /api/notices/active
    @GetMapping("/active")
    public ResponseEntity<List<NoticeProjection>> getActiveNotices(
            @RequestParam(value = "userId", defaultValue = "1") Long userId) { // 💡 RequestParam으로 변경

        System.out.println("요청받은 유저 ID: " + userId); // 💡 백엔드 콘솔에 찍히는지 확인

        List<NoticeProjection> notices = noticeService.getUnreadNotices(userId);
        System.out.println("조회된 공지 개수: " + notices.size()); // 💡 DB에서 몇 개를 가져왔는지 확인

        return ResponseEntity.ok(notices);
    }


    // 2. 사용자가 팝업을 닫았을 때 읽음 처리 API
    // POST /api/notices/read
    @PostMapping("/read")
    public ResponseEntity<Void> markNoticeAsRead(@RequestBody NoticeReadRequestDTO request) {
        noticeService.markAsRead(request.getNoticeId(), request.getUserId());
        return ResponseEntity.ok().build();
    }
}