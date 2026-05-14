package AIcard.cardapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

    // 2. HTTP 보안 설정 (핵심)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // A. CSRF 보호 비활성화
                // 개발 단계에서는 POST 요청(회원가입 등)이 차단되지 않도록 일단 꺼둡니다.
                .csrf(csrf -> csrf.disable())

                // B. URL별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/card/join",
                                "/card/login",
                                "/cards/**",
                                "/public/**",
                                "/css/**",
                                "/js/**"
                        ).permitAll() // 누구나 접근 가능
                        .anyRequest().authenticated() // 그 외 모든 요청은 로그인해야함
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
                        .logoutSuccessUrl("/card/login")
                        .invalidateHttpSession(true)
                );

        return http.build();
    }
}
