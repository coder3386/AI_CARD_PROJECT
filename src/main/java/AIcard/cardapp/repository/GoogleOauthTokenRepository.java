package AIcard.cardapp.repository;

import AIcard.cardapp.entity.GoogleOauthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoogleOauthTokenRepository extends JpaRepository<GoogleOauthToken, Long> {
}