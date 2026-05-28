package AIcard.cardapp.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
                        .requestMatchers("/card/join", "/card/login").permitAll()
                        .requestMatchers("/card/edit").authenticated()
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

                // D. 로그아웃 설정
                .logout(logout -> logout
                        .logoutUrl("/card/logout")
                        .logoutSuccessUrl("/main")
                        .invalidateHttpSession(true)
                );

        return http.build();
    }
}
