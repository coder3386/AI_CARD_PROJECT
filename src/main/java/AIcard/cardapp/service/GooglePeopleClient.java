package AIcard.cardapp.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.Person;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Component;

@Component
public class GooglePeopleClient {

    public void createContact(String accessTokenStr, Person person) throws Exception {
        // 1. 토큰 생성
        AccessToken accessToken = new AccessToken(accessTokenStr, null);
        GoogleCredentials credentials = GoogleCredentials.create(accessToken);

        // 2. 빌더 단계에서 인증 정보(HttpCredentialsAdapter)를 직접 주입
        PeopleService peopleService = new PeopleService.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)) // <--- 여기서 주입
                .setApplicationName("AI-Card-Project")
                .build();

        // 3. API 실행
        peopleService.people().createContact(person).execute();
    }
}