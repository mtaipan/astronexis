package dev.taipan.astronexis.core;

import dev.taipan.astronexis.core.bootstrap.ModuleManager;
import dev.taipan.astronexis.core.bootstrap.ServiceRegistry;
import dev.taipan.astronexis.core.module.menu.MenuModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class AstronexisCorePlugin extends JavaPlugin {

    private ServiceRegistry services;
    private ModuleManager modules;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.services = new ServiceRegistry(this);
        this.services.init();

        this.modules = new ModuleManager(this);

        if (getConfig().getBoolean("modules.menu", true)) {
            this.modules.register(new MenuModule(this, services));
        }

        this.modules.enableAll();
        getLogger().info("AstronexisCore enabled");
    }

    @Override
    public void onDisable() {
        if (modules != null) {
            modules.disableAll();
        }
        getLogger().info("AstronexisCore disabled");
    }

    public ServiceRegistry services() {
        return services;
    }

    public ModuleManager modules() {
        return modules;
    }
}