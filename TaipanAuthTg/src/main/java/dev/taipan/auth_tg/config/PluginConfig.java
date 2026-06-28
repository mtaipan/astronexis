package dev.taipan.auth_tg.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public record PluginConfig(
        String storageType,
        TelegramConfig telegram,
        SecurityConfig security,
        PostgresConfig postgres
) {
    public static PluginConfig fromBukkit(FileConfiguration c) {
        String storageType = c.getString("storage.type", "yaml");

        TelegramConfig tg = new TelegramConfig(
                c.getString("telegram.botToken", ""),
                c.getString("telegram.messagePrefix", "[TaipanAuth]")
        );

        // defaults:
        // - trustTtlSeconds: 6h
        // - trustBind: ip-subnet (/24) so mobile/Wi-Fi hops trigger 2FA, but small IP drift usually doesn't.
        int trustTtlSeconds = c.getInt("security.trustTtlSeconds", 6 * 60 * 60);
        String trustBind = c.getString("security.trustBind", "ip-subnet");
        if (trustBind == null) trustBind = "ip-subnet";
        trustBind = trustBind.toLowerCase(Locale.ROOT).trim();
        if (!trustBind.equals("none") && !trustBind.equals("ip") && !trustBind.equals("ip-subnet")) {
            trustBind = "ip-subnet";
        }
        int trustSubnetV4 = c.getInt("security.trustSubnetV4", 24);
        if (trustSubnetV4 < 8 || trustSubnetV4 > 32) trustSubnetV4 = 24;

        SecurityConfig sec = new SecurityConfig(
                c.getInt("security.codeTtlSeconds", 120),
                c.getBoolean("security.allowBypassPermission", false),
                c.getString("security.bypassPermissionNode", "taipan.authtg.bypass"),

                trustTtlSeconds,
                trustBind,
                trustSubnetV4
        );

        PostgresConfig pg = new PostgresConfig(
                c.getString("postgres.host", "127.0.0.1"),
                c.getInt("postgres.port", 5432),
                c.getString("postgres.database", "tgbot"),
                c.getString("postgres.user", "tgbot"),
                c.getString("postgres.password", "tgbot"),
                c.getInt("postgres.poolSize", 3),
                c.getInt("postgres.cacheTtlSeconds", 10)
        );

        return new PluginConfig(storageType, tg, sec, pg);
    }
}