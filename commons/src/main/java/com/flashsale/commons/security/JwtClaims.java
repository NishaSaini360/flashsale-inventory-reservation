package com.flashsale.commons.security;

public final class JwtClaims {
    public static final String TENANT_ID = "tenantId";
    public static final String ROLES     = "roles";

    public static final String ROLE_USER    = "USER";
    public static final String ROLE_ADMIN   = "ADMIN";
    public static final String ROLE_SERVICE = "SERVICE";

    private JwtClaims() {}
}
