package AIcard.cardapp.service;

import AIcard.cardapp.DTO.GoogleContactDTO;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleContactService {

    /**
     * 프론트엔드에서 받은 Access Token과 DTO를 사용하여 구글 주소록에 저장하는 핵심 메서드
     */
    public void saveContact(String accessTokenStr, GoogleContactDTO contactDto) throws Exception {

        // 1. 전달받은 Access Token 문자열을 기반으로 구글 인증 객체(PeopleService) 동적 생성
        AccessToken accessToken = new AccessToken(accessTokenStr, null);
        GoogleCredentials credentials = GoogleCredentials.create(accessToken);

        PeopleService peopleService = new PeopleService.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("AI-Card-Project")
                .build();

        // 2. 구글이 이해할 수 있는 '사람(Person)' 객체 생성
        Person contactToCreate = new Person();

        // 3. 이름 설정
        Name name = new Name().setGivenName(contactDto.getName());
        contactToCreate.setNames(Collections.singletonList(name));

        // 4. 전화번호 설정
        PhoneNumber phone = new PhoneNumber().setValue(contactDto.getPhoneNumber());
        contactToCreate.setPhoneNumbers(Collections.singletonList(phone));

        // 5. 이메일 설정 (값이 있을 경우에만)
        if (contactDto.getEmail() != null && !contactDto.getEmail().isEmpty()) {
            EmailAddress email = new EmailAddress().setValue(contactDto.getEmail());
            contactToCreate.setEmailAddresses(Collections.singletonList(email));
        }

        // 6. 회사명(소속) 및 직함 설정 (값이 있을 경우에만)
        if ((contactDto.getOrganization() != null && !contactDto.getOrganization().isEmpty()) ||
                (contactDto.getJobTitle() != null && !contactDto.getJobTitle().isEmpty())) {

            Organization org = new Organization()
                    .setName(contactDto.getOrganization())
                    .setTitle(contactDto.getJobTitle());
            contactToCreate.setOrganizations(Collections.singletonList(org));
        }

        // 7. 메모(AI 명함 추출 특징 등) 설정 (값이 있을 경우에만)
        if (contactDto.getNotes() != null && !contactDto.getNotes().isEmpty()) {
            Biography bio = new Biography()
                    .setValue(contactDto.getNotes())
                    .setContentType("TEXT_PLAIN");
            contactToCreate.setBiographies(Collections.singletonList(bio));
        }

        // 8. 생성한 동적 peopleService 인스턴스를 통해 API 호출 및 실제 저장 실행
        peopleService.people().createContact(contactToCreate)
                .execute();

        System.out.println("구글 주소록에 명함 저장 완료: " + contactDto.getName());
    }
}