package com.ecommerce.security;

import com.ecommerce.entity.Role;

/**
 * The authenticated caller, extracted from a validated JWT access token and set as the
 * principal of the {@code Authentication} in the security context by
 * {@link JwtAuthenticationFilter}. Controllers pull it out via
 * {@code @AuthenticationPrincipal UserPrincipal}.
 */
public record UserPrincipal(Long id, String email, Role role) {
}
