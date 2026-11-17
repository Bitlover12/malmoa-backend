package com.aac.ieojwo;

import com.aac.ieojwo.account.domain.Account;
import com.aac.ieojwo.account.domain.AccountType;
import com.aac.ieojwo.account.domain.OAuthProvider;
import com.aac.ieojwo.account.repository.AccountRepository;
import com.aac.ieojwo.account.repository.OAuthAccountRepository;
import com.aac.ieojwo.auth.dto.OnboardingRequest;
import com.aac.ieojwo.auth.service.AuthService;
import com.aac.ieojwo.guardian.repository.GuardianRepository;
import com.aac.ieojwo.guardian.repository.UserGuardianRepository;
import com.aac.ieojwo.symbol.domain.Symbol;
import com.aac.ieojwo.symbol.repository.SymbolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthService authService;
    @Autowired AccountRepository accountRepository;
    @Autowired OAuthAccountRepository oauthAccountRepository;
    @Autowired GuardianRepository guardianRepository;
    @Autowired UserGuardianRepository userGuardianRepository;
    @Autowired SymbolRepository symbolRepository;

    @Test
    void publicAndProtectedEndpointsHaveExpectedSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "malmoa_sse_connections_active")));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/me/aac-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("미인증")))
                .andExpect(status().isForbidden());
    }

    @Test
    void googleAccountUsesSubjectAndUpdatesProfileWithoutDuplicates() {
        authService.upsertGoogleAccount("subject-1", "first@example.com", "첫 이름", null);
        authService.upsertGoogleAccount("subject-1", "updated@example.com", "새 이름", "https://example.com/p.png");

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(oauthAccountRepository.count()).isEqualTo(1);
        Account account = accountRepository.findAll().get(0);
        assertThat(account.getEmail()).isEqualTo("updated@example.com");
        assertThat(account.getName()).isEqualTo("새 이름");
        assertThat(account.isOnboardingCompleted()).isFalse();
    }

    @Test
    void kakaoAccountUsesStableInternalEmailWhenProviderOmitsEmail() {
        long accountCount = accountRepository.count();
        long oauthAccountCount = oauthAccountRepository.count();

        Account first = authService.upsertKakaoAccount("kakao-without-email", null, null, null);
        Account second = authService.upsertKakaoAccount(
                "kakao-without-email", null, "카카오 사용자 새 이름", "https://example.com/kakao.png");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getEmail()).matches("kakao-[0-9a-f-]{36}@oauth\\.malmoa\\.local");
        assertThat(second.getName()).isEqualTo("카카오 사용자 새 이름");
        assertThat(accountRepository.count()).isEqualTo(accountCount + 1);
        assertThat(oauthAccountRepository.count()).isEqualTo(oauthAccountCount + 1);
        assertThat(oauthAccountRepository.findByProviderAndProviderSubject(
                OAuthProvider.KAKAO, "kakao-without-email")).isPresent();
    }

    @Test
    void currentAccountReturnsAuthenticatedAccount() throws Exception {
        createAccount("subject-me", "me@example.com");

        mockMvc.perform(get("/api/v1/auth/me").with(oidc("subject-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@example.com"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    @Test
    void onboardingValidatesRequiredTermsAndRejectsRepeat() throws Exception {
        createAccount("subject-onboarding", "onboarding@example.com");

        mockMvc.perform(post("/api/v1/auth/onboarding")
                        .with(oidc("subject-onboarding"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(onboardingBody(false, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(post("/api/v1/auth/onboarding")
                        .with(oidc("subject-onboarding"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(onboardingBody(true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        Account account = accountRepository.findByEmailIgnoreCase("onboarding@example.com").orElseThrow();
        assertThat(guardianRepository.findByAccountId(account.getId())).isPresent();

        mockMvc.perform(post("/api/v1/auth/onboarding")
                        .with(oidc("subject-onboarding"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(onboardingBody(true, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void creatingAacUserLinksPrimaryGuardianAndListsOnlyOwnedUsers() throws Exception {
        onboardGuardian("guardian-a", "a@example.com");
        onboardGuardian("guardian-b", "b@example.com");

        long userA = createUser("guardian-a", "사용자 A");
        long userB = createUser("guardian-b", "사용자 B");

        assertThat(userGuardianRepository.findAllByUserIdOrderByPrimaryGuardianDescIdAsc(userA))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.isPrimaryGuardian()).isTrue();
                    assertThat(link.getGuardian().getAccount().getEmail()).isEqualTo("a@example.com");
                });

        mockMvc.perform(get("/api/v1/me/aac-users").with(oidc("guardian-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(userA));

        mockMvc.perform(get("/api/v1/me/aac-users/{id}", userA).with(oidc("guardian-a")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/aac-users/{id}", userB).with(oidc("guardian-a")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/me/aac-users/{id}", 999999L).with(oidc("guardian-a")))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyUserApiUsesTheSameOwnershipChecks() throws Exception {
        onboardGuardian("legacy-a", "legacy-a@example.com");
        onboardGuardian("legacy-b", "legacy-b@example.com");
        long userB = createUser("legacy-b", "레거시 B");

        mockMvc.perform(get("/api/v1/users/{id}", userB).with(oidc("legacy-a")))
                .andExpect(status().isForbidden());
    }

    @Test
    void favoritesAndUsageLogsRequireOwnership() throws Exception {
        onboardGuardian("usage-a", "usage-a@example.com");
        onboardGuardian("usage-b", "usage-b@example.com");
        long userA = createUser("usage-a", "기록 A");
        Symbol symbol = symbolRepository.findAll().get(0);

        mockMvc.perform(post("/api/v1/me/aac-users/{id}/favorites", userA)
                        .with(oidc("usage-a")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("symbolId", symbol.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/me/aac-users/{id}/favorites", userA)
                        .with(oidc("usage-a")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("symbolId", symbol.getId()))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/me/aac-users/{id}/favorites", userA).with(oidc("usage-b")))
                .andExpect(status().isForbidden());

        for (String action : List.of("SELECT", "CANCEL", "SPEAK")) {
            mockMvc.perform(post("/api/v1/me/aac-users/{id}/usage-logs", userA)
                            .with(oidc("usage-a")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("symbolId", symbol.getId(), "action", action))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/me/aac-users/{id}/recent-symbols", userA).with(oidc("usage-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(symbol.getId()));
        mockMvc.perform(post("/api/v1/me/aac-users/{id}/usage-logs", userA)
                        .with(oidc("usage-b")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("symbolId", symbol.getId(), "action", "SELECT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutatingRequestsRequireCsrfAndLogoutSucceedsWithIt() throws Exception {
        onboardGuardian("csrf-user", "csrf@example.com");

        mockMvc.perform(post("/api/v1/me/aac-users")
                        .with(oidc("csrf-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("CSRF")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(oidc("csrf-user"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."));
    }

    private void createAccount(String subject, String email) {
        authService.upsertGoogleAccount(subject, email, "테스트 보호자", null);
    }

    private void onboardGuardian(String subject, String email) {
        createAccount(subject, email);
        authService.completeOnboarding(principal(subject, email),
                new OnboardingRequest(AccountType.GUARDIAN, true, true, false, "010-1234-5678"));
    }

    private long createUser(String subject, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/me/aac-users")
                        .with(oidc(subject)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("id").asLong();
    }

    private String onboardingBody(boolean terms, boolean privacy) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "accountType", "GUARDIAN",
                "termsOfServiceAgreed", terms,
                "privacyPolicyAgreed", privacy,
                "marketingAgreed", false,
                "phoneNumber", "010-1234-5678"
        ));
    }

    private String userBody(String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "birthDate", "2012-01-15",
                "relationshipType", "PARENT",
                "emergencyContact", "01012345678",
                "mode", "SIMPLE",
                "gridSize", "GRID_2X2"
        ));
    }

    private OidcUser principal(String subject, String email) {
        Instant now = Instant.now();
        OidcIdToken token = new OidcIdToken("token-" + subject, now, now.plusSeconds(300),
                Map.of("sub", subject, "email", email));
        return new DefaultOidcUser(List.of(), token, OidcUserInfo.builder().subject(subject).email(email).build());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor oidc(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }
}
