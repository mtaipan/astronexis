package dev.taipan.astronexis.core.bootstrap;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import dev.taipan.astronexis.core.shared.i18n.MessageService;
import dev.taipan.astronexis.core.shared.platform.PlatformService;

public final class ServiceRegistry {

    private final AstronexisCorePlugin plugin;

    private MessageService messages;
    private PlatformService platformService;

    public ServiceRegistry(AstronexisCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.messages = new MessageService(plugin);
        this.messages.load();

        this.platformService = new PlatformService(plugin);
        this.platformService.init();
    }

    public MessageService messages() {
        return messages;
    }

    public PlatformService platformService() {
        return platformService;
    }
}