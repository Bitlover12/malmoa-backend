package com.aac.ieojwo.auth.service;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class CustomOidcUserService {

    private final OidcUserService delegate = new OidcUserService();
    private final AuthService authService;

    public CustomOidcUserService(AuthService authService) {
        this.authService = authService;
    }

    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!Boolean.TRUE.equals(user.getClaimAsBoolean("email_verified"))
                || user.getClaimAsString("email") == null
                || user.getClaimAsString("email").isBlank()) {
            // Account.email and OAuthAccount.providerEmail are non-null and unique.
            // Require a verified consented email rather than fabricating one or
            // silently linking to a different social identity.
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unverified_email"),
                    "인증된 이메일 제공 동의가 필요합니다."
            );
        }

        if ("google".equals(registrationId)) {
            authService.upsertGoogleAccount(
                    user.getSubject(),
                    user.getClaimAsString("email"),
                    user.getClaimAsString("name"),
                    user.getClaimAsString("picture")
            );
        } else if ("kakao".equals(registrationId)) {
            authService.upsertKakaoAccount(
                    user.getSubject(),
                    user.getClaimAsString("email"),
                    user.getClaimAsString("nickname"),
                    user.getClaimAsString("picture")
            );
        } else {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "지원하지 않는 로그인 제공자입니다."
            );
        }
        return user;
    }
}
