package dev.taipan.astronexis.core.module.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

    private final MenuPage page;
    private final boolean bedrockLayout;

    public MenuHolder(MenuPage page, boolean bedrockLayout) {
        this.page = page;
        this.bedrockLayout = bedrockLayout;
    }

    public MenuPage page() {
        return page;
    }

    public boolean bedrockLayout() {
        return bedrockLayout;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}