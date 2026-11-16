# Kakao login (experimental branch)

This branch adds Kakao **OpenID Connect (OIDC)** login alongside existing Google login without modifying production until this branch is deployed.

## 1. Kakao Developers

At https://developers.kakao.com/ create/select a Kakao application:

1. Enable **Kakao Login**.
2. Enable **OpenID Connect**. This branch validates OIDC ID tokens through Spring Security.
3. Register the backend callback URL (not a Vercel URL):
   - Production example: `https://malmoa-backend-production.up.railway.app/login/oauth2/code/kakao`
   - Local example: `http://localhost:8080/login/oauth2/code/kakao`
4. In consent items, configure **Kakao Account (email)** (`account_email`) and **nickname** (`profile_nickname`). This backend requires an email verified by Kakao; missing/unverified email will fail login. Kakao may restrict email consent to certain app classifications.
5. Copy the **REST API key** and **Client Secret** (client_secret_post). Do not commit them to Git.

## 2. Configure the backend

Keep Google login configured, and enable the Kakao profile using:

```env
SPRING_PROFILES_ACTIVE=postgres,kakao
KAKAO_REST_API_KEY=<Kakao REST API key>
KAKAO_CLIENT_SECRET=<Kakao Client Secret>
GOOGLE_CLIENT_ID=<existing Google client ID>
GOOGLE_CLIENT_SECRET=<existing Google client secret>
FRONTEND_BASE_URL=https://frontend-pied-five-90.vercel.app
SESSION_COOKIE_SAME_SITE=none
SESSION_COOKIE_SECURE=true
# Existing production DB settings: DB_URL / DB_USERNAME / DB_PASSWORD
```

Use `SPRING_PROFILES_ACTIVE=kakao` only when using the local default H2 database; NEVER replace a production `postgres` profile with `kakao` alone. Deployment requires Railway access to the *actual* Malmoa service, and the code branch must be selected in its source settings. Only changes on this branch will not automatically update existing deployments.

## 3. Endpoints

- Google: `GET https://malmoa-backend-production.up.railway.app/oauth2/authorization/google`
- Kakao: `GET https://malmoa-backend-production.up.railway.app/oauth2/authorization/kakao`
- Kakao backend OAuth callback: `/login/oauth2/code/kakao`
- After successful login: redirect to `<FRONTEND_BASE_URL>/oauth/callback?login=success&onboardingCompleted=...`.
- Then client calls credentialed `GET /api/v1/auth/me` and expects HTTP 200 with a session cookie.

The guardian frontend's Kakao button is currently prototype-only and must be wired to the Kakao authorization endpoint **after** backend deployment and credential configuration.

## 4. Account safety

- Google and Kakao use separate `(provider, provider_subject)` identity keys.
- The database already stores provider as VARCHAR(30), so `KAKAO` does not require a schema migration.
- **Existing Google and Kakao accounts with the same email are not silently linked.** This branch rejects conflicting emails until an authenticated account-linking workflow is designed.
- Login requires verified Kakao email. No synthetic or placeholder email is stored.
- Existing Google OIDC login and login sessions are preserved.
- CSRF remains enabled. Existing cross-site cookie constraints and third-party-cookie browser policies remain deployment considerations.

## 5. Validation

1. Run backend tests: `./mvnw test` (or Windows `mvnw.cmd test`).
2. Deploy this branch to a non-production Railway service with the `kakao` profile and required secrets.
3. Open `/oauth2/authorization/kakao`, complete consent, and observe the redirect to frontend.
4. In browser DevTools → Network, check `/api/v1/auth/me` returns 200 and the account's onboarding flow works.
5. Try Google login again; existing users should remain separate.
6. Confirm an existing Google email is not automatically linked to a new Kakao subject.
7. Test a POST (e.g., onboarding) with session and CSRF token, and also check behavior when third-party cookies are blocked.

Kakao docs: https://developers.kakao.com/docs/ko/kakaologin/rest-api
