package AIcard.cardapp.controller;

import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.repository.UsersMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final UsersMemberRepository memberRepository;

    @ModelAttribute("loginUser")
    public UsersMember addLoginUserToModel(Principal principal) {
        if (principal != null) {
            // 현재 로그인한 사람의 아이디(LoginId)로 DB에서 전체 회원 정보를 조회
            return memberRepository.findByLoginId(principal.getName()).orElse(null);
        }
        return null; // 로그인하지 않은 상태면 null 반환
    }
}
