package AIcard.cardapp.repository;

import AIcard.cardapp.entity.BusinessCardDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessCardDetailRepository extends JpaRepository<BusinessCardDetail, Long> {
}
