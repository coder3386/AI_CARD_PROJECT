package AIcard.cardapp.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GooglePeopleConfig {
    @Bean
    public PeopleService peopleService() throws Exception {
        return new PeopleService.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                null) // 여기는 나중에 토큰을 넣어 동적으로 빌드하거나,
                // 인터셉터를 사용하여 처리합니다.
                .setApplicationName("AI-Card-Project")
                .build();
    }
}