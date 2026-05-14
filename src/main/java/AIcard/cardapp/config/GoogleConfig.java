package AIcard.cardapp.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GoogleConfig {

    @Bean
    public PeopleService peopleService() throws IOException, GeneralSecurityException {
        // 1. resources 폴더에 넣은 JSON 키 파일 읽기 (파일명이 다르면 수정하세요!)
        InputStream in = GoogleConfig.class.getResourceAsStream("/google-key.json");

        GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/contacts"));

        // 2. 구글 PeopleService 객체 생성하여 스프링에 등록
        return new PeopleService.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("AI-Card-Project")
                .build();
    }
}