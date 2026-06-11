package AIcard.cardapp.repository;

import AIcard.cardapp.entity.GoogleAnalyticsSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GoogleAnalyticsSettingsRepository extends JpaRepository<GoogleAnalyticsSettings, Long> {
    List<GoogleAnalyticsSettings> findByUserId(Long userId);
}