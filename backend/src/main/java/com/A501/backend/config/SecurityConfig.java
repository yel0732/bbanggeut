package com.A501.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.A501.backend.domain.auth.security.JwtAuthenticationEntryPoint;
import com.A501.backend.domain.auth.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATTERNS = {
            "/api/auth/**",
            "/h2-console/**",
            "/actuator/health"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // H2 콘솔이 iframe으로 렌더링되므로 same-origin 허용
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 순서 중요: logout·password 변경을 먼저 authenticated()로 선언해야 아래 permit-all 패턴에 묻히지 않는다.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/auth/password").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/me").authenticated()
                        .requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/game-sessions")
                        .authenticated()
                        .requestMatchers("/actuator/metrics/**").authenticated()
                        // Prometheus 스크레이프 경로. nginx가 라우팅하지 않아 외부에서 도달할 수
                        // 없고, edge 네트워크의 Prometheus 컨테이너만 접근한다(스크레이퍼는 인증
                        // 헤더가 없다). 아래 anyRequest().permitAll()에 묻혀 있던 것을 명시한다.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // /api/children/**는 report 도메인의 /api/children/{childId}/reports/**와 경로가
                        // 겹치므로 와일드카드 대신 이 도메인이 실제로 쓰는 경로만 정확히 지정한다.
                        .requestMatchers("/api/profile/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/children/name/check").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/children").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/children").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/children/{childId}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/children/{childId}").authenticated()
                        // 아이 실물 사진을 올리는 경로다. 아래 anyRequest().permitAll() 때문에 여기서
                        // 빠뜨리면 인증 없이 열리므로 반드시 명시한다.
                        .requestMatchers(HttpMethod.POST, "/api/children/{childId}/image").authenticated()
                        // TODO: game/report/conversation 도메인이 실제 인증으로 전환되면 .authenticated()로 좁힐 것
                        // report·conversation은 서비스 계층에서 SecurityContext 기반으로 인증/인가를
                        // 강제한다(미인증 시 401). game 도메인은 아직 mock 기반이라 여기서 좁히지 않는다.
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
