# Authentication Flow Documentation

This document outlines the hardened authentication flows after security improvements.

## 1. User Registration

1.  **Client:** Sends POST `/auth/register` with validated `RegisterRequest` (username, password, fullName).
2.  **Backend:**
    *   Normalizes username.
    *   Checks username uniqueness.
    *   Hashes password with BCrypt.
    *   Creates `User` entity with `AuthProvider.LOCAL`, `emailVerified=false`.
    *   Saves user.
    *   Issues JWT access and refresh tokens.
    *   Stores refresh token hash in `user_sessions`.
    *   Returns `AuthResponse`.
3.  **Security:** Input validation, secure password hashing, unique username enforcement.

## 2. Local User Login

1.  **Client:** Sends POST `/auth/login` with validated `LoginRequest` (username, password).
2.  **Backend:**
    *   Normalizes username.
    *   Authenticates via `AuthenticationManager` (using `DaoAuthenticationProvider` + `BCryptPasswordEncoder`).
    *   Handles `DisabledException`, `LockedException`, `BadCredentialsException` gracefully.
    *   Loads `User` by username.
    *   Updates `lastLoginAt`.
    *   Issues JWT access and refresh tokens.
    *   Stores refresh token hash.
    *   Returns `AuthResponse`.
3.  **Security:** Secure password validation, handles user account states, input validation.

## 3. Refresh Token Flow

1.  **Client:** Sends POST `/auth/refresh` with validated `RefreshTokenRequest` (refreshToken).
2.  **Backend:**
    *   Finds refresh token hash in `user_sessions`.
    *   Validates token expiry and user status (`ACTIVE`, not deleted).
    *   Revokes used refresh token.
    *   Issues new JWT access and refresh tokens.
    *   Stores new refresh token hash.
    *   Returns `AuthResponse`.
3.  **Security:** Secure token validation, user status check, refresh token rotation.

## 4. Logout

1.  **Client:** Sends POST `/auth/logout` with validated `LogoutRequest` (refreshToken).
2.  **Backend:**
    *   Finds refresh token hash in `user_sessions`.
    *   Marks refresh token as revoked.
    *   JWT access token remains valid until expiry (no client-side revocation).
3.  **Security:** Revokes refresh token to prevent reuse.

## 5. Email Verification

1.  **Client:** Sends POST `/user/email/send-verification` with validated `SendEmailVerificationRequest` (email).
2.  **Backend:**
    *   Validates email uniqueness and user status.
    *   Generates secure, hashed verification token.
    *   Stores pending email and token.
    *   Sends verification email with link: `FRONTEND_URL/verify-email?token=...`
3.  **Client (Email):** Clicks verification link.
4.  **Backend:**
    *   Receives GET `/user/email/verify?token=...`.
    *   Validates token, expiry, pending email.
    *   Checks for email conflicts.
    *   Updates user email, sets verified status.
    *   Clears verification token/pending email.
5.  **Security:** Secure token generation/storage, expiry, rate limiting, input validation.

## 6. Google OAuth2 Login + Exchange Code Flow

1.  **Client:** Initiates OAuth flow (e.g., clicks "Login with Google").
2.  **Spring Security:** Redirects to Google authorization server.
3.  **Google:** Prompts user for consent.
4.  **Google Redirects Backend:** Sends authorization code to `/login/oauth2/code/google`.
5.  **Spring Security OAuth2 Client:** Exchanges code with Google for tokens.
6.  **`OAuth2LoginSuccessHandler`:**
    *   Receives `OAuth2User` principal.
    *   Validates Google `email_verified` flag.
    *   Finds or creates user based on Google `sub` and email.
    *   Prevents linking if email exists with different provider.
    *   Updates user profile (`authProvider=GOOGLE`, `providerId`, `emailVerified`, etc.).
    *   Generates secure, short-lived (60s) exchange code.
    *   Stores minimal identity (`userId`, `issuedAt`, `ipHash`, `userAgentHash`) in Redis with SHA-256 hashed code as key.
    *   Redirects frontend to `/oauth-success?code=<exchange_code>`.
7.  **Frontend:**
    *   Receives code from URL.
    *   Sends POST `/api/auth/oauth/exchange` with `{ "code": "..." }`.
8.  **Backend (`OAuthExchangeController`):**
    *   Validates request DTO.
    *   Hashes submitted code.
    *   Atomically retrieves and deletes code from Redis (`GETDEL`).
    *   Validates code binding (IP/UA hash) if implemented.
    *   Loads user by `userId` from Redis.
    *   Validates user status (active, not deleted).
    *   Issues JWT access and refresh tokens.
    *   Stores refresh token hash.
    *   Returns `AuthResponse`.
9.  **Security:** Tokens never exposed in URL. Secure exchange code. Redis storage of minimal identity. Rate limiting on exchange endpoint. Input validation.