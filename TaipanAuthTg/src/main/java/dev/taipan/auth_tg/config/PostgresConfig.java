package dev.taipan.auth_tg.config;

public record PostgresConfig(
        String host,
        int port,
        String database,
        String user,
        String password,
        int poolSize,
        int cacheTtlSeconds
) {}