package dev.taipan.astronexis.core.shared.i18n;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import dev.taipan.astronexis.core.shared.text.Texts;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MessageService {

    private final AstronexisCorePlugin plugin;
    private final Map<String, FileConfiguration> bundles = new HashMap<>();

    public MessageService(AstronexisCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        bundles.clear();
        loadBundle("ru", "messages_ru.yml");
        loadBundle("en", "messages_en.yml");
    }

    private void loadBundle(String locale, String resourceName) {
        InputStream in = plugin.getResource(resourceName);
        if (in == null) {
            plugin.getLogger().warning("Missing resource: " + resourceName);
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8)
        );
        bundles.put(locale.toLowerCase(Locale.ROOT), cfg);
    }

    public String locale(Player player) {
        String configured = plugin.getConfig().getString("locale.default", "ru");
        if (configured == null || configured.isBlank()) {
            configured = "ru";
        }
        return configured.toLowerCase(Locale.ROOT);
    }

    public String get(Player player, String path) {
        return get(locale(player), path);
    }

    public String get(String locale, String path) {
        FileConfiguration cfg = bundle(locale);
        String raw = cfg.getString(path);
        if (raw == null) {
            return "!" + path + "!";
        }
        return Texts.color(raw);
    }

    public List<String> getList(Player player, String path) {
        return getList(locale(player), path);
    }

    public List<String> getList(String locale, String path) {
        FileConfiguration cfg = bundle(locale);
        List<String> list = cfg.getStringList(path);
        if (list == null) {
            return List.of();
        }

        List<String> out = new ArrayList<>(list.size());
        for (String line : list) {
            out.add(Texts.color(line));
        }
        return out;
    }

    public String format(Player player, String path, Map<String, String> placeholders) {
        String value = get(player, path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", Objects.toString(entry.getValue(), ""));
        }
        return value;
    }

    private FileConfiguration bundle(String locale) {
        FileConfiguration cfg = bundles.get(locale.toLowerCase(Locale.ROOT));
        if (cfg != null) {
            return cfg;
        }

        FileConfiguration fallback = bundles.get("ru");
        if (fallback != null) {
            return fallback;
        }

        return new YamlConfiguration();
    }
}