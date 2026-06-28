package dev.taipan.auth_tg.store;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.taipan.auth_tg.config.PostgresConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class PgBindingsStore implements BindingsStore, AutoCloseable {

    private final HikariDataSource ds;
    private final long ttlMillis;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public PgBindingsStore(PostgresConfig cfg) {
        try {
            // ВАЖНО: Paper/plugin classloader может не зарегистрировать драйвер сам
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL driver not found in classpath", e);
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:postgresql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(cfg.poolSize());
        hc.setPoolName("TaipanAuthTg-PG");
        hc.setAutoCommit(true);

        // добиваем проблему "No suitable driver"
        hc.setDriverClassName("org.postgresql.Driver");

        this.ds = new HikariDataSource(hc);
        this.ttlMillis = cfg.cacheTtlSeconds() * 1000L;
    }

    @Override
    public Long getChatIdByNick(String nick) {
        String key = nick.toLowerCase(Locale.ROOT);

        CacheEntry ce = cache.get(key);
        long now = System.currentTimeMillis();
        if (ce != null && (now - ce.ts) < ttlMillis) return ce.chatId;

        Long chatId = queryChatId(key);
        cache.put(key, new CacheEntry(chatId, now));
        return chatId;
    }

    private Long queryChatId(String nickLower) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("select tg_id from bindings where lower(nick)=?")) {
            ps.setString(1, nickLower);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public void close() {
        ds.close();
    }

    private static final class CacheEntry {
        final Long chatId;
        final long ts;
        CacheEntry(Long chatId, long ts) { this.chatId = chatId; this.ts = ts; }
    }
}