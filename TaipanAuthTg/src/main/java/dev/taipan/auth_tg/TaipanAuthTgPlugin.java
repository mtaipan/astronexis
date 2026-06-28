package dev.taipan.auth_tg;

import dev.taipan.auth_tg.auth.AuthMePoller;
import dev.taipan.auth_tg.bedrock.BedrockAuthForms;
import dev.taipan.auth_tg.bedrock.BedrockUiManager;
import dev.taipan.auth_tg.cmd.AuthCommand;
import dev.taipan.auth_tg.cmd.TgCodeCommand;
import dev.taipan.auth_tg.cmd.admin.AuthTgCommand;
import dev.taipan.auth_tg.config.PluginConfig;
import dev.taipan.auth_tg.gate.GateListener;
import dev.taipan.auth_tg.store.BindingsStore;
import dev.taipan.auth_tg.store.PgBindingsStore;
import dev.taipan.auth_tg.store.PgTrustStore;
import dev.taipan.auth_tg.store.TrustStore;
import dev.taipan.auth_tg.store.YamlBindingsStore;
import dev.taipan.auth_tg.store.YamlTrustStore;
import dev.taipan.auth_tg.tg.TelegramApi;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TaipanAuthTgPlugin extends JavaPlugin {

    private PluginConfig cfg;
    private BindingsStore store;
    private TrustStore trustStore;
    private TelegramApi telegram;
    private GateListener gate;
    private AuthMePoller poller;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.cfg = PluginConfig.fromBukkit(getConfig());

        // storage
        this.store = switch (cfg.storageType()) {
            case "postgres" -> new PgBindingsStore(cfg.postgres());
            case "yaml" -> new YamlBindingsStore(this);
            default -> throw new IllegalArgumentException("Unknown storage.type: " + cfg.storageType());
        };

        // trust storage (same selector)
        this.trustStore = switch (cfg.storageType()) {
            case "postgres" -> new PgTrustStore(cfg.postgres());
            case "yaml" -> new YamlTrustStore(this);
            default -> new YamlTrustStore(this);
        };

        this.telegram = new TelegramApi(cfg.telegram().botToken(), cfg.telegram().messagePrefix());
        this.gate = new GateListener(this, cfg, store, trustStore, telegram);

        // Bedrock forms
        BedrockAuthForms bedrockForms = new BedrockAuthForms(this, gate);
        BedrockUiManager bedrockUi = new BedrockUiManager(this, gate, bedrockForms);

        getServer().getPluginManager().registerEvents(gate, this);
        getServer().getPluginManager().registerEvents(bedrockUi, this);

        PluginCommand tgcode = getCommand("tgcode");
        if (tgcode != null) tgcode.setExecutor(new TgCodeCommand(gate));

        PluginCommand authtg = getCommand("authtg");
        if (authtg != null) authtg.setExecutor(new AuthTgCommand(this, cfg));

        PluginCommand auth = getCommand("auth");
        if (auth != null) auth.setExecutor(new AuthCommand(bedrockUi));

        this.poller = new AuthMePoller(this, gate);
        this.poller.start();

        getLogger().info("TaipanAuthTg enabled (storage=" + cfg.storageType() + ", trustTtl=" + cfg.security().trustTtlSeconds() + "s)");
    }

    @Override
    public void onDisable() {
        if (poller != null) poller.stop();

        if (trustStore != null) {
            try { trustStore.close(); } catch (Exception ignored) {}
        }

        if (store instanceof AutoCloseable c) {
            try { c.close(); } catch (Exception ignored) {}
        }

        getLogger().info("TaipanAuthTg disabled");
    }
}