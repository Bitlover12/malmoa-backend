package com.aac.ieojwo.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class LoadTestHeaderAuthenticationFilter extends OncePerRequestFilter {
    public static final String SUBJECT_HEADER = "X-Load-Test-Subject";

    private final boolean enabled;

    public LoadTestHeaderAuthenticationFilter(
            @Value("${app.load-test.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String subject = request.getHeader(SUBJECT_HEADER);
        if (enabled && subject != null && !subject.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Instant now = Instant.now();
            OidcIdToken idToken = new OidcIdToken(
                    "load-test-token", now, now.plusSeconds(3600), Map.of(
                    "sub", subject,
                    "email", subject + "@load.test",
                    "email_verified", true,
                    "name", "Load Test Guardian"));
            DefaultOidcUser principal = new DefaultOidcUser(
                    java.util.List.of(new OidcUserAuthority(idToken)), idToken, "sub");
            SecurityContextHolder.getContext().setAuthentication(
                    new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "load-test"));
        }
        filterChain.doFilter(request, response);
    }
}
