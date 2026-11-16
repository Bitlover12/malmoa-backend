package com.aac.ieojwo.auth.service;

import com.aac.ieojwo.account.domain.Account;
import com.aac.ieojwo.account.domain.OAuthAccount;
import com.aac.ieojwo.account.domain.OAuthProvider;
import com.aac.ieojwo.account.repository.OAuthAccountRepository;
import com.aac.ieojwo.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CurrentAccountServiceTest {
    private OAuthAccountRepository repository;
    private CurrentAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(OAuthAccountRepository.class);
        service = new CurrentAccountService(repository);
    }

    @Test
    void findsKakaoAccountByIssuerAndSubject() {
        Account account = Account.createSocialAccount("user@example.com", "테스트", null);
        OAuthAccount identity = OAuthAccount.create(account, OAuthProvider.KAKAO, "kakao-123", "user@example.com");
        when(repository.findByProviderAndProviderSubject(OAuthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.of(identity));

        OidcUser principal = mock(OidcUser.class);
        when(principal.getSubject()).thenReturn("kakao-123");
        when(principal.getClaimAsString("iss")).thenReturn("https://kauth.kakao.com");

        assertSame(account, service.requireSocialAccount(principal));
        verify(repository).findByProviderAndProviderSubject(OAuthProvider.KAKAO, "kakao-123");
    }

    @Test
    void retainsGoogleAccountLookup() {
        Account account = Account.createSocialAccount("google@example.com", "Google user", null);
        OAuthAccount identity = OAuthAccount.create(account, OAuthProvider.GOOGLE, "google-123", "google@example.com");
        when(repository.findByProviderAndProviderSubject(OAuthProvider.GOOGLE, "google-123"))
                .thenReturn(Optional.of(identity));

        OidcUser principal = mock(OidcUser.class);
        when(principal.getSubject()).thenReturn("google-123");
        when(principal.getClaimAsString("iss")).thenReturn("https://accounts.google.com");

        assertSame(account, service.requireGoogleAccount(principal));
    }

    @Test
    void refusesUnknownOidcIssuer() {
        OidcUser principal = mock(OidcUser.class);
        when(principal.getSubject()).thenReturn("attacker");
        when(principal.getClaimAsString("iss")).thenReturn("https://unknown.example");

        assertThrows(UnauthorizedException.class, () -> service.requireSocialAccount(principal));
        verify(repository, never()).findByProviderAndProviderSubject(any(), any());
    }

    @Test
    void refusesMissingPrincipal() {
        assertThrows(UnauthorizedException.class, () -> service.requireSocialAccount(null));
    }
}
