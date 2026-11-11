package com.aac.ieojwo.auth.security;

import com.aac.ieojwo.auth.handler.OAuth2FailureHandler;
import com.aac.ieojwo.auth.handler.OAuth2SuccessHandler;
import com.aac.ieojwo.auth.service.CustomOidcUserService;
import com.aac.ieojwo.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CustomOidcUserService customOidcUserService,
                                            OAuth2SuccessHandler successHandler,
                                            OAuth2FailureHandler failureHandler,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler,
                                            LoadTestHeaderAuthenticationFilter loadTestAuthenticationFilter,
                                            ObjectMapper objectMapper,
                                            @Value("${app.observability.public-metrics:false}")
                                            boolean publicMetrics,
                                            @Value("${app.load-test.enabled:false}")
                                            boolean loadTestEnabled) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(csrfRepository)
                            .ignoringRequestMatchers("/h2-console/**", "/api/v1/device-pairings/claim/**", "/api/v1/device/**");
                    if (loadTestEnabled) {
                        csrf.ignoringRequestMatchers("/api/v1/me/**");
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(
                                "/api/v1/health",
                                "/api/v1/auth/csrf",
                                "/api/v1/device-pairings/claim/**",
                                "/api/v1/device/**",
                                "/h2-console/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/error"
                        ).permitAll();
                    if (publicMetrics) {
                        auth.requestMatchers("/actuator/metrics/**", "/actuator/prometheus").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService::loadUser))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), ApiResponse.ok(null, "로그아웃되었습니다."));
                        })
                )
                .addFilterBefore(loadTestAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }
}
