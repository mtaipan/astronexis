package dev.taipan.auth_tg.store;

import java.time.Instant;
import java.util.UUID;

public interface TrustStore extends AutoCloseable {

    TrustSession getValid(UUID uuid);

    void upsert(UUID uuid, String fingerprint, Instant expiresAt);

    void delete(UUID uuid);

    default void purgeExpired() {}

    @Override
    default void close() throws Exception {}
}