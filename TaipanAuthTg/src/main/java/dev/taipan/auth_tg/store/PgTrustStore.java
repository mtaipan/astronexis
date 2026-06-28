package dev.taipan.auth_tg.store;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.taipan.auth_tg.config.PostgresConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class PgTrustStore implements TrustStore, AutoCloseable {

    private final HikariDataSource ds;

    public PgTrustStore(PostgresConfig cfg) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL driver not found in classpath", e);
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:postgresql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database());
        hc.setUsername(cfg.user());
        hc.setPassword(cfg.password());
        hc.setMaximumPoolSize(cfg.poolSize());
        hc.setPoolName("TaipanAuthTg-PG-Trust");
        hc.setAutoCommit(true);
        hc.setDriverClassName("org.postgresql.Driver");

        this.ds = new HikariDataSource(hc);

        ensureTable();
    }

    private void ensureTable() {
        String sql = """
                create table if not exists auth_trust_sessions (
                    player_uuid uuid primary key,
                    fingerprint text not null,
                    expires_at timestamptz not null,
                    last_seen_at timestamptz not null default now()
                )
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException ignored) {
            // fail-closed: if DB is broken, trust sessions just won't work
        }
    }

    @Override
    public TrustSession getValid(UUID uuid) {
        String sql = "select fingerprint, expires_at from auth_trust_sessions where player_uuid=? and expires_at > now()";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String fp = rs.getString(1);
                Instant exp = rs.getTimestamp(2).toInstant();

                touch(uuid);

                return new TrustSession(uuid, fp, exp);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private void touch(UUID uuid) {
        String sql = "update auth_trust_sessions set last_seen_at=now() where player_uuid=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void upsert(UUID uuid, String fingerprint, Instant expiresAt) {
        String sql = """
                insert into auth_trust_sessions(player_uuid, fingerprint, expires_at, last_seen_at)
                values (?, ?, ?, now())
                on conflict (player_uuid) do update set
                    fingerprint = excluded.fingerprint,
                    expires_at = excluded.expires_at,
                    last_seen_at = now()
                """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.setString(2, fingerprint);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void delete(UUID uuid) {
        String sql = "delete from auth_trust_sessions where player_uuid=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void purgeExpired() {
        String sql = "delete from auth_trust_sessions where expires_at <= now()";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void close() {
        ds.close();
    }
}