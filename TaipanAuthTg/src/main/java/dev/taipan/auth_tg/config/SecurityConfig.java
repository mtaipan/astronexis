package dev.taipan.auth_tg.config;

public record SecurityConfig(
        int codeTtlSeconds,
        boolean allowBypassPermission,
        String bypassPermissionNode,

        int trustTtlSeconds,
        String trustBind,      // none | ip | ip-subnet
        int trustSubnetV4      // only for ip-subnet; typical 24
) {}