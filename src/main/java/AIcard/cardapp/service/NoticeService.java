package AIcard.cardapp.service;

import AIcard.cardapp.DTO.NoticeProjection;
import AIcard.cardapp.entity.Notice;
import AIcard.cardapp.entity.NoticeReadHistory;
import AIcard.cardapp.repository.NoticeReadHistoryRepository;
import AIcard.cardapp.repository.NoticeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeReadHistoryRepository historyRepository;

    public List<NoticeProjection> getUnreadNotices(Long userId) {
        return noticeRepository.findUnreadActiveNotices(userId);
    }

    @Transactional
    public void markAsRead(Long noticeId, Long userId) {
        // 이미 읽은 이력이 있는지 방어 로직 추가 가능
        NoticeReadHistory history = new NoticeReadHistory(noticeId, userId);
        historyRepository.save(history); // INSERT 쿼리 실행
    }
    // [NoticeService.java 내부에 추가할 내용]

    @Transactional
    public void createNotice(String title, String content, LocalDateTime expiresAt) {
        // 엔티티를 생성하여 DB에 저장
        Notice notice = new Notice(title, content, expiresAt);
        noticeRepository.save(notice);
    }
}