package com.ecommerce.exception;

/**
 * An application-level authentication failure raised inside a controller/service
 * (bad login credentials, an invalid/expired/revoked refresh token) -> 401. Distinct
 * from Spring Security's own {@code AuthenticationException}, which is handled by
 * {@code RestAuthenticationEntryPoint} for requests that never reach a controller
 * because they lack a valid bearer token in the first place.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
