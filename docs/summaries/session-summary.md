# Session Management Summary

*   **Authentication Model:** Primarily stateless JWT-based authentication for API interactions.
*   **OAuth2 State:** Spring Security's OAuth2 client may use temporary server-side state for authorization requests. This is not considered application-level session state.
*   **Refresh Tokens:** Secure, opaque, SHA-256 hashed refresh tokens are persisted in the `user_sessions` table.
*   **Redis Exchange Code:** A temporary, short-lived (60s TTL), single-use code (`oauth:exchange:{hash(code)}`) is used for the OAuth exchange flow, storing minimal user identity and expiring automatically.
*   **Session Policy:** Spring Security configured to `SessionCreationPolicy.IF_REQUIRED`. This allows necessary state for OAuth flows while the main application authentication remains stateless via JWTs.
*   **Rate Limiting:** Applied to critical authentication endpoints (login, register, refresh, email verification, OAuth exchange) per IP using Redis fixed-window counters.