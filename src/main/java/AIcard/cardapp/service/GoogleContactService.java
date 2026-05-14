package AIcard.cardapp.service;

import AIcard.cardapp.DTO.GoogleContactDTO;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.Name;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.people.v1.model.PhoneNumber;
import com.google.api.services.people.v1.model.EmailAddress;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleContactService {

    // 구글 API와 통신하기 위한 도구 (이미 설정되었다고 가정)
    private final PeopleService peopleService;

    public GoogleContactService(PeopleService peopleService) {
        this.peopleService = peopleService;
    }

    /**
     * DTO를 받아서 구글 주소록에 저장하는 핵심 메서드
     */
    public void saveContact(GoogleContactDTO contactDto) throws Exception {

        // 1. 구글이 이해할 수 있는 '사람(Person)' 객체 생성
        Person contactToCreate = new Person();

        // 2. 이름 설정
        Name name = new Name().setGivenName(contactDto.getName());
        contactToCreate.setNames(Collections.singletonList(name));

        // 3. 전화번호 설정
        PhoneNumber phone = new PhoneNumber().setValue(contactDto.getPhoneNumber());
        contactToCreate.setPhoneNumbers(Collections.singletonList(phone));

        // 4. 이메일 설정 (값이 있을 경우에만)
        if (contactDto.getEmail() != null) {
            EmailAddress email = new EmailAddress().setValue(contactDto.getEmail());
            contactToCreate.setEmailAddresses(Collections.singletonList(email));
        }

        // 5. 구글 API를 호출하여 실제로 저장 실행
        peopleService.people().createContact(contactToCreate)
                .execute();

        System.out.println("구글 주소록에 명함 저장 완료: " + contactDto.getName());
    }
}