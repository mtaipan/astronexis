package dev.taipan.auth_tg.store;

import java.time.Instant;
import java.util.UUID;

public record TrustSession(UUID uuid, String fingerprint, Instant expiresAt) {}