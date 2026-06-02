package AIcard.cardapp.repository;

import AIcard.cardapp.entity.Notice;
import AIcard.cardapp.DTO.NoticeProjection; // DTO 경로
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 만료되지 않고, 사용자가 아직 읽지 않은 공지 조회
    @Query(value = "SELECT n.notice_id as noticeId, n.title, n.content " +
            "FROM notices n " +
            "LEFT JOIN notice_read_history h ON n.notice_id = h.notice_id AND h.user_id = :userId " +
            "WHERE n.expires_at > NOW() AND h.history_id IS NULL",
            nativeQuery = true)
    List<NoticeProjection> findUnreadActiveNotices(@Param("userId") Long userId);
}