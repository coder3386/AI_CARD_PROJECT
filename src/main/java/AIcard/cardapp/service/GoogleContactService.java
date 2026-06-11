package AIcard.cardapp.service;

import AIcard.cardapp.DTO.GoogleContactDTO;
import com.google.api.services.people.v1.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import AIcard.cardapp.repository.BusinessCardRepository;
import AIcard.cardapp.entity.BusinessCard;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class GoogleContactService {

    private final GoogleAuthService authService; // 토큰 관리 (기존 사용 중인 서비스)
    private final GooglePeopleClient peopleClient; // 주소록 통신 전담
    private final BusinessCardRepository businessCardRepository;

    public void saveContact(String accessToken, GoogleContactDTO contactDto) throws Exception {
        // DTO 데이터를 구글 API가 이해할 수 있는 Person 객체로 변환
        Person person = convertToPerson(contactDto);

        // 주소록 통신 전담 클라이언트(peopleClient)에게 토큰과 데이터 전달하여 저장 수행
        peopleClient.createContact(accessToken, person);

        System.out.println("✅ 구글 주소록 직접 토큰 저장 성공: " + contactDto.getName());
    }


    // 사용자의 userId를 통해 세션/DB에 저장된 구글 토큰을 연동하여 저장
    public void saveContactByUserId(Long userId, GoogleContactDTO contactDto) throws Exception {
        // 유효한 토큰 확보 (만료 시 자동 갱신 로직 포함)
        String accessToken = authService.getValidAccessToken(userId);
        // 2. 주소록 저장 수행
        saveContact(accessToken, contactDto);
    }

    public void saveCardToGoogleContacts(Long cardId, Long userId) throws Exception {
        BusinessCard card = businessCardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("명함을 찾을 수 없습니다."));

        GoogleContactDTO contactDto = new GoogleContactDTO();
        contactDto.setName(card.getDisplayName());
        contactDto.setPhoneNumber(card.getPhone());
        contactDto.setEmail(card.getEmail());
        contactDto.setOrganization(card.getCompany());
        contactDto.setJobTitle(card.getJobTitle());
        contactDto.setNotes("명함 URL: http://wooserver76.iptime.org/card/public/card/" + card.getPublicUrl());

        saveContactByUserId(userId, contactDto);
    }

    private Person convertToPerson(GoogleContactDTO dto) {
        Person person = new Person();

        Name name = new Name().setGivenName(dto.getName());
        person.setNames(Collections.singletonList(name));
        if (dto.getPhoneNumber() != null) {
            PhoneNumber phone = new PhoneNumber().setValue(dto.getPhoneNumber());
            person.setPhoneNumbers(Collections.singletonList(phone));
        }
        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            EmailAddress email = new EmailAddress().setValue(dto.getEmail());
            person.setEmailAddresses(Collections.singletonList(email));
        }
        if ((dto.getOrganization() != null && !dto.getOrganization().isEmpty()) ||
                (dto.getJobTitle() != null && !dto.getJobTitle().isEmpty())) {
            Organization org = new Organization()
                    .setName(dto.getOrganization())
                    .setTitle(dto.getJobTitle());
            person.setOrganizations(Collections.singletonList(org));
        }
        if (dto.getNotes() != null && !dto.getNotes().isEmpty()) {
            Biography bio = new Biography().setValue(dto.getNotes()).setContentType("TEXT_PLAIN");
            person.setBiographies(Collections.singletonList(bio));
        }

        return person;
    }
}