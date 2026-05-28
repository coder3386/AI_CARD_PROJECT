package AIcard.cardapp.service;

import AIcard.cardapp.entity.UsersMember;
import AIcard.cardapp.repository.UsersMemberRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UsersMemberRepository usersMemberRepository;

    public CurrentUserService(UsersMemberRepository usersMemberRepository) {
        this.usersMemberRepository = usersMemberRepository;
    }

    public Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        String loginId = authentication.getName();
        return usersMemberRepository.findByLoginId(loginId)
                .map(UsersMember::getId)
                .orElse(null);
    }
}
