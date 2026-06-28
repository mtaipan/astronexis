package dev.taipan.auth_tg.store;

import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class YamlBindingsStore implements BindingsStore {

    private final Path file;
    private final Yaml yaml = new Yaml();

    public YamlBindingsStore(JavaPlugin plugin) {
        this.file = plugin.getDataFolder().toPath().resolve("bindings.yml");
    }

    @Override
    public Long getChatIdByNick(String nick) {
        if (!Files.exists(file)) return null;

        try {
            String text = Files.readString(file);
            Object rootObj = yaml.load(text);
            if (!(rootObj instanceof Map<?, ?> root)) return null;

            Object bindingsObj = root.get("bindings");
            if (!(bindingsObj instanceof Map<?, ?> bindings)) return null;

            Object entryObj = bindings.get(nick);
            if (entryObj == null) return null;

            Map<?, ?> entry;
            if (entryObj instanceof Map<?, ?> m) entry = m;
            else return null;

            Object chatIdObj = entry.get("chatId");
            if (chatIdObj == null) return null;

            if (chatIdObj instanceof Number n) return n.longValue();
            try { return Long.parseLong(chatIdObj.toString()); } catch (Exception ignored) { return null; }

        } catch (IOException e) {
            return null;
        }
    }
}