package dev.taipan.auth_tg.store;

import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class YamlTrustStore implements TrustStore {

    private final Path file;
    private final Yaml yaml = new Yaml();

    public YamlTrustStore(JavaPlugin plugin) {
        this.file = plugin.getDataFolder().toPath().resolve("trust.yml");
    }

    @Override
    public synchronized TrustSession getValid(UUID uuid) {
        Map<String, Object> root = loadRoot();
        Object sessionsObj = root.get("sessions");
        if (!(sessionsObj instanceof Map<?, ?> sessions)) return null;

        Object entryObj = sessions.get(uuid.toString());
        if (!(entryObj instanceof Map<?, ?> entry)) return null;

        String fp = toStr(entry.get("fingerprint"));
        Long exp = toLong(entry.get("expiresAt"));
        if (fp == null || exp == null) return null;

        Instant expiresAt = Instant.ofEpochSecond(exp);
        if (Instant.now().isAfter(expiresAt)) {
            // cleanup expired
            ((Map<?, ?>) sessions).remove(uuid.toString());
            saveRoot(root);
            return null;
        }

        return new TrustSession(uuid, fp, expiresAt);
    }

    @Override
    public synchronized void upsert(UUID uuid, String fingerprint, Instant expiresAt) {
        Map<String, Object> root = loadRoot();
        Map<String, Object> sessions = getOrCreateMap(root, "sessions");

        Map<String, Object> entry = new HashMap<>();
        entry.put("fingerprint", fingerprint);
        entry.put("expiresAt", expiresAt.getEpochSecond());

        sessions.put(uuid.toString(), entry);
        saveRoot(root);
    }

    @Override
    public synchronized void delete(UUID uuid) {
        Map<String, Object> root = loadRoot();
        Object sessionsObj = root.get("sessions");
        if (!(sessionsObj instanceof Map<?, ?> sessions)) return;

        ((Map<?, ?>) sessions).remove(uuid.toString());
        saveRoot(root);
    }

    @Override
    public synchronized void purgeExpired() {
        Map<String, Object> root = loadRoot();
        Object sessionsObj = root.get("sessions");
        if (!(sessionsObj instanceof Map<?, ?> sessions)) return;

        Instant now = Instant.now();
        boolean changed = false;

        for (Object k : sessions.keySet().toArray()) {
            Object entryObj = sessions.get(k);
            if (!(entryObj instanceof Map<?, ?> entry)) continue;

            Long exp = toLong(entry.get("expiresAt"));
            if (exp == null) continue;

            if (now.isAfter(Instant.ofEpochSecond(exp))) {
                ((Map<?, ?>) sessions).remove(k);
                changed = true;
            }
        }

        if (changed) saveRoot(root);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(Map<String, Object> root, String key) {
        Object obj = root.get(key);
        if (obj instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> created = new HashMap<>();
        root.put(key, created);
        return created;
    }

    private Map<String, Object> loadRoot() {
        if (!Files.exists(file)) return new HashMap<>();

        try {
            String text = Files.readString(file);
            Object rootObj = yaml.load(text);
            if (rootObj instanceof Map<?, ?> m) {
                //noinspection unchecked
                return (Map<String, Object>) m;
            }
            return new HashMap<>();
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private void saveRoot(Map<String, Object> root) {
        try {
            Files.createDirectories(file.getParent());
            String out = yaml.dump(root);
            Files.writeString(file, out);
        } catch (IOException ignored) {}
    }

    private static String toStr(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception ignored) { return null; }
    }
}