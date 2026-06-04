package AIcard.cardapp.service;

import AIcard.cardapp.entity.Inquiry;
import AIcard.cardapp.entity.InquiryStatus;
import AIcard.cardapp.repository.InquiryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;

    @Autowired
    public InquiryService(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    // 1. 문의 등록 (userId를 Long 타입으로 변경, Status를 Enum으로 변경)
    @Transactional
    public void saveInquiry(Inquiry inquiry, Long loginUserId) {
        inquiry.setUserId(loginUserId);
        inquiry.setStatus(InquiryStatus.WAITING); // Enum 타입 사용
        inquiryRepository.save(inquiry);
    }

    // 2. 본인 문의 내역 조회 (메서드명을 컨트롤러에 맞게 getMyInquiries로 변경)
    public List<Inquiry> getMyInquiries(Long userId) {
        return inquiryRepository.findByUserId(userId);
    }

    // 3. 전체 문의 내역 조회
    public List<Inquiry> getAllInquiries() {
        return inquiryRepository.findAll();
    }

    // 4. 답변 작성 및 상태 업데이트
    @Transactional
    public void saveAnswer(Long inquiryId, String answer) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 문의를 찾을 수 없습니다. ID: " + inquiryId));

        inquiry.setAnswer(answer);
        inquiry.setStatus(InquiryStatus.COMPLETED); // Enum 타입 사용
    }

    // 5. 문의 삭제 (userId를 Long 타입으로 변경)
    @Transactional
    public void deleteInquiry(Long inquiryId, Long loginUserId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 문의를 찾을 수 없습니다. ID: " + inquiryId));

        // 보안 검증: 삭제 요청자와 작성자가 일치하는지 확인
        if (!inquiry.getUserId().equals(loginUserId)) {
            throw new IllegalArgumentException("삭제 권한이 없습니다.");
        }

        inquiryRepository.delete(inquiry);
    }
}