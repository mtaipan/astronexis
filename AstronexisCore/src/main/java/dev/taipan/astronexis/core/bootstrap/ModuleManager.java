package dev.taipan.astronexis.core.bootstrap;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import dev.taipan.astronexis.core.module.CoreModule;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleManager {

    private final AstronexisCorePlugin plugin;
    private final Map<String, CoreModule> modules = new LinkedHashMap<>();

    public ModuleManager(AstronexisCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void register(CoreModule module) {
        modules.put(module.id(), module);
    }

    public void enableAll() {
        for (CoreModule module : modules.values()) {
            plugin.getLogger().info("Enabling module: " + module.id());
            module.enable();
        }
    }

    public void disableAll() {
        for (CoreModule module : modules.values()) {
            try {
                plugin.getLogger().info("Disabling module: " + module.id());
                module.disable();
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to disable module " + module.id() + ": " + ex.getMessage());
            }
        }
    }
}