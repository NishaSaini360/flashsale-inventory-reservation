package com.flashsale.commons.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Not a @Bean on purpose: a Converter bean gets picked up by Spring MVC's
 * conversion service, and as a lambda it has no resolvable generics.
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtClaims.ROLES);
        List<GrantedAuthority> authorities = roles == null
                ? List.of()
                : roles.stream()
                       .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                       .toList();
        return new JwtAuthenticationToken(jwt, authorities);
    }
}
