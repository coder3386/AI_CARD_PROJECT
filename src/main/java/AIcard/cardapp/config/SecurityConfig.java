package AIcard.cardapp.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // 1. 비밀번호 암호화 빈 등록
    // 회원가입 시 passwordEncoder.encode()를 사용할 때 이 객체를 주입받아 사용합니다.
    @Bean
    public BCryptPasswordEncoder encode() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico");
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // 2. HTTP 보안 설정 (핵심)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // A. CSRF 보호 비활성화
                // 개발 단계에서는 POST 요청(회원가입 등)이 차단되지 않도록 일단 꺼둡니다.
                .csrf(csrf -> csrf.disable())
                //.csrf(csrf -> csrf.ignoringRequestMatchers("/"))

                // B. URL별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/error", "/error/**").permitAll() // 누구나 접근 가능
                        .requestMatchers("/", "/main", "/viewDemo").permitAll() // 누구나 접근 가능
                        .requestMatchers("/terms", "/privacypolicy").permitAll()
                        .requestMatchers("/manager/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/card/join", "/card/login", "/api/notices/**", "/api/contacts/**", "/card/api/**", "/api/**").permitAll()
                        .requestMatchers("/card/edit").authenticated()
                        .requestMatchers("/oauth/start", "/oauth/callback").permitAll()
                        .requestMatchers("/public/card/**", "/dont").permitAll()
                        .requestMatchers("/cards/select-type").permitAll()
                        .requestMatchers("/cards/*/profile-image").permitAll()
                        .requestMatchers(
                                "/cards/new",
                                "/cards/generate",
                                "/cards/drawing/**",
                                "/cards/my/**",
                                "/cards/*/preview",
                                "/cards/*/update-text",
                                "/cards/*/fix-layout",
                                "/cards/*/delete",
                                "/cards/*/ai-fallback"
                        ).authenticated()
                        //.requestMatchers("manager/").hasRole("MANAGER")
                        .anyRequest().permitAll() // 그 외 모든 요청은 로그인해야함

                )

                // C. 커스텀 로그인 설정
                .formLogin(form -> form
                        .loginPage("/card/login")           // 우리가 만든 로그인 페이지 URL
                        .loginProcessingUrl("/card/login")   // 로그인 폼 action과 일치해야 함 (시큐리티가 낚아채서 처리)
                        .usernameParameter("username") // HTML의 name="username"과 일치 (이미 기본값이긴 함)
                        .defaultSuccessUrl("/main", true) // 로그인 성공 시 갈 곳
                        .permitAll()
                )

                //세션 설정
                .sessionManagement(session -> session
                        .maximumSessions(-1) // 제한 없음 혹은 원하는 제한 수
                        .sessionRegistry(sessionRegistry()) // 세션 레지스트리 등록
                )

                .sessionManagement(session -> {
                    session.maximumSessions(-1)
                            .sessionRegistry(sessionRegistry());
                    // 세션 고정 보호 정책 설정 (구글 연동 시 세션 풀림 방지)
                    session.sessionFixation(sessionFixation -> sessionFixation.none());
                })

                // D. 로그아웃 설정
                .logout(logout -> logout
                        .logoutUrl("/card/logout")
                        .logoutSuccessUrl("/main")
                        .invalidateHttpSession(true)
                )

                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "script-src 'self' https://www.googletagmanager.com https://cdn.jsdelivr.net https://unpkg.com 'unsafe-inline'; " +
                                        "connect-src 'self' http://wooserver76.iptime.org https://unpkg.com https://www.google-analytics.com;")
                        )
                );

        return http.build();
    }
}
