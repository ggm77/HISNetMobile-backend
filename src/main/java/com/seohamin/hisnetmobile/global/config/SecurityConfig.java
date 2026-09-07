package com.seohamin.hisnetmobile.global.config;

import com.seohamin.hisnetmobile.global.security.HisnetAuthenticationProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 세션 로그인 기반 시큐리티 설정.
 * <p>
 * - 인증은 로컬 DB 가 아니라 {@link HisnetAuthenticationProvider}(원본 로그인 중계)로 판정
 * - 로그인 성공 시 원본 세션(PHPSESSID)을 담은 principal 이 서버 세션(JSESSIONID)에 저장됨 →
 *   JSESSIONID 가 곧 설계 문서의 "프록시 세션 ID", 서버 세션이 곧 "프록시세션→PHPSESSID 매핑"
 * - 프론트가 별도 오리진(SPA)이라 CORS 허용 + 자격증명(쿠키) 전송 활성화
 * - 세션 만료/미인증은 리다이렉트 없이 401 로 응답
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HisnetAuthenticationProvider hisnetAuthenticationProvider;

    @Value("${app.security.login-url:/api/v1/auth/login}")
    private String loginUrl;

    @Value("${app.security.logout-url:/api/v1/auth/logout}")
    private String logoutUrl;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {

        http
                .authenticationProvider(hisnetAuthenticationProvider)

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 토큰 없는 크로스오리진 SPA + 세션쿠키 구조 → CSRF 토큰 미사용 (동일 사이트 배포 시 재검토)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(form -> form
                        .loginProcessingUrl(loginUrl)
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                )
                .logout(logout -> logout
                        .logoutUrl(logoutUrl)
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )

                .sessionManagement(session -> session
                        // 로그인 시에만 세션 생성
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // 로그인 성공 시 세션 ID 재발급 (세션 고정 공격 방어)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        // 다중 기기 동시 로그인 시 학교 서버 세션 처리 방식이 미검증이라, 우리 쪽에서 제한하지 않는다
                )

                .authorizeHttpRequests(authorize -> authorize
                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 스웨거
                        .requestMatchers("/api/swagger", "/api/swagger-ui/**").permitAll()

                        // 로그인/로그아웃 (시큐리티 필터가 가로챔)
                        .requestMatchers(loginUrl, logoutUrl).permitAll()

                        // 식단표는 원본 로그인 페이지에 공개로 실려오는 데이터라 미인증 허용
                        .requestMatchers(HttpMethod.GET, "/api/v1/meals").permitAll()

                        // 그 외 모든 API 는 인증 필요
                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().permitAll()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // 세션 쿠키(JSESSIONID) 를 주고받아야 하므로 필수
        configuration.setAllowCredentials(true);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}