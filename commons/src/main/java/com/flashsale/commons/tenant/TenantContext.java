package com.flashsale.commons.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) { TENANT.set(tenantId); }

    public static String get() { return TENANT.get(); }

    public static String require() {
        String t = TENANT.get();
        if (t == null || t.isBlank()) {
            throw new IllegalStateException("No tenant in context");
        }
        return t;
    }

    public static void clear() { TENANT.remove(); }
}
