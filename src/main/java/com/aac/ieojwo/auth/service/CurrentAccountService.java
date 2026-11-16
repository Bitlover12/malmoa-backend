package com.aac.ieojwo.auth.service;

import com.aac.ieojwo.account.domain.Account;
import com.aac.ieojwo.account.domain.OAuthProvider;
import com.aac.ieojwo.account.domain.AccountStatus;
import com.aac.ieojwo.account.repository.OAuthAccountRepository;
import com.aac.ieojwo.common.exception.UnauthorizedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CurrentAccountService {

    private final OAuthAccountRepository oauthAccountRepository;

    public CurrentAccountService(OAuthAccountRepository oauthAccountRepository) {
        this.oauthAccountRepository = oauthAccountRepository;
    }

    public Account requireSocialAccount(OidcUser principal) {
        if (principal == null || principal.getSubject() == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }

        // Spring Security validates the OIDC ID token before creating this principal.
        String issuer = principal.getClaimAsString("iss");
        OAuthProvider provider;
        if ("https://kauth.kakao.com".equals(issuer)) {
            provider = OAuthProvider.KAKAO;
        } else if (issuer == null || "https://accounts.google.com".equals(issuer)
                || "accounts.google.com".equals(issuer)) {
            // A missing issuer is only used by the existing synthetic load-test principal.
            provider = OAuthProvider.GOOGLE;
        } else {
            throw new UnauthorizedException("지원하지 않는 로그인 제공자입니다.");
        }

        Account account = oauthAccountRepository
                .findByProviderAndProviderSubject(provider, principal.getSubject())
                .map(oauthAccount -> oauthAccount.getAccount())
                .orElseThrow(() -> new UnauthorizedException("로그인 계정을 찾을 수 없습니다."));
        if (account.getStatus() != AccountStatus.ACTIVE) throw new UnauthorizedException("사용할 수 없는 계정입니다.");
        return account;
    }

    /** Compatibility alias for existing guardian controllers. Supports both OIDC providers. */
    public Account requireGoogleAccount(OidcUser principal) {
        return requireSocialAccount(principal);
    }
}
