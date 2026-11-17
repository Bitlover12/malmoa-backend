package com.aac.ieojwo.auth.handler;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2FailureHandlerTest {

    @Test
    void redirectsSocialLoginFailureToWelcomePage() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler("https://frontend.example/");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/login/oauth2/code/kakao");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token_response"))
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://frontend.example/welcome?error=oauth_failed");
    }
}
