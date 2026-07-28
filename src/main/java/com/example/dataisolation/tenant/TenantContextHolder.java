package com.example.dataisolation.tenant;

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();
    private TenantContextHolder() { }
    public static TenantContext require() {
        TenantContext value = CURRENT.get();
        if (value == null) throw new IllegalStateException("Tenant context is not available");
        return value;
    }
    static void set(TenantContext value) { CURRENT.set(value); }
    static void clear() { CURRENT.remove(); }
}
