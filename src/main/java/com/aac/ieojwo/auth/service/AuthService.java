package com.aac.ieojwo.auth.service;

import com.aac.ieojwo.account.domain.*;
import com.aac.ieojwo.account.repository.AccountRepository;
import com.aac.ieojwo.account.repository.OAuthAccountRepository;
import com.aac.ieojwo.account.repository.TermsAgreementRepository;
import com.aac.ieojwo.auth.dto.AccountResponse;
import com.aac.ieojwo.auth.dto.OnboardingRequest;
import com.aac.ieojwo.common.exception.BadRequestException;
import com.aac.ieojwo.common.exception.ConflictException;
import com.aac.ieojwo.guardian.domain.Guardian;
import com.aac.ieojwo.guardian.repository.GuardianRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String CURRENT_TERMS_VERSION = "1.0";

    private final AccountRepository accountRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final GuardianRepository guardianRepository;
    private final CurrentAccountService currentAccountService;

    public AuthService(AccountRepository accountRepository, OAuthAccountRepository oauthAccountRepository,
                       TermsAgreementRepository termsAgreementRepository, GuardianRepository guardianRepository,
                       CurrentAccountService currentAccountService) {
        this.accountRepository = accountRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.termsAgreementRepository = termsAgreementRepository;
        this.guardianRepository = guardianRepository;
        this.currentAccountService = currentAccountService;
    }

    @Transactional
    public Account upsertGoogleAccount(String providerSubject, String email, String name, String profileImageUrl) {
        return upsertSocialAccount(OAuthProvider.GOOGLE, providerSubject, email, name, profileImageUrl);
    }

    @Transactional
    public Account upsertKakaoAccount(String providerSubject, String email, String name, String profileImageUrl) {
        return upsertSocialAccount(OAuthProvider.KAKAO, providerSubject, email, name, profileImageUrl);
    }

    private Account upsertSocialAccount(OAuthProvider provider, String providerSubject,
                                        String email, String name, String profileImageUrl) {
        String normalizedSubject = requireText(providerSubject, "소셜 로그인 사용자 식별값이 없습니다.");
        String normalizedEmail = requireText(email, "소셜 로그인 이메일 정보가 없습니다.").toLowerCase(java.util.Locale.ROOT);
        String normalizedName = name == null || name.isBlank() ? normalizedEmail : name.trim();
        String normalizedImage = normalize(profileImageUrl);

        return oauthAccountRepository
                .findByProviderAndProviderSubject(provider, normalizedSubject)
                .map(oauthAccount -> {
                    Account account = oauthAccount.getAccount();
                    account.updateSocialProfile(normalizedEmail, normalizedName, normalizedImage);
                    oauthAccount.updateProviderEmail(normalizedEmail);
                    return account;
                })
                .orElseGet(() -> createSocialAccount(provider, normalizedSubject, normalizedEmail,
                        normalizedName, normalizedImage));
    }

    public AccountResponse getCurrentAccount(OidcUser principal) {
        return AccountResponse.from(currentAccountService.requireGoogleAccount(principal));
    }

    @Transactional
    public void withdraw(OidcUser principal) { currentAccountService.requireGoogleAccount(principal).withdraw(); }

    @Transactional
    public AccountResponse completeOnboarding(OidcUser principal, OnboardingRequest request) {
        if (!request.termsOfServiceAgreed()) {
            throw new BadRequestException("서비스 이용약관에 동의해야 합니다.");
        }
        if (!request.privacyPolicyAgreed()) {
            throw new BadRequestException("개인정보 처리방침에 동의해야 합니다.");
        }

        Account account = currentAccountService.requireGoogleAccount(principal);
        if (account.isOnboardingCompleted()) {
            throw new ConflictException("이미 최초 설정을 완료한 계정입니다.");
        }
        account.completeOnboarding(request.accountType());

        termsAgreementRepository.save(TermsAgreement.create(
                account, TermsType.TERMS_OF_SERVICE, CURRENT_TERMS_VERSION, true));
        termsAgreementRepository.save(TermsAgreement.create(
                account, TermsType.PRIVACY_POLICY, CURRENT_TERMS_VERSION, true));
        termsAgreementRepository.save(TermsAgreement.create(
                account, TermsType.MARKETING, CURRENT_TERMS_VERSION, request.marketingAgreed()));

        if (request.accountType() == AccountType.GUARDIAN || request.accountType() == AccountType.SUPPORTER) {
            guardianRepository.findByAccountId(account.getId())
                    .orElseGet(() -> guardianRepository.findByEmailIgnoreCase(account.getEmail())
                            .map(existing -> {
                                existing.linkAccount(account);
                                return existing;
                            })
                            .orElseGet(() -> guardianRepository.save(Guardian.createForAccount(
                                    account, account.getName(), account.getEmail(),
                                    normalize(request.phoneNumber())))));
        }

        return AccountResponse.from(account);
    }

    private Account createSocialAccount(OAuthProvider provider, String providerSubject,
                                        String email, String name, String profileImageUrl) {
        // Never link two providers by matching email alone: email does not prove account ownership.
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("동일한 이메일의 계정이 이미 다른 로그인 식별자에 연결되어 있습니다.");
        }
        Account account = accountRepository.save(Account.createSocialAccount(email, name, profileImageUrl));
        oauthAccountRepository.save(OAuthAccount.create(account, provider, providerSubject, email));
        return account;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
