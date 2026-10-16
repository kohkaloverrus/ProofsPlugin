package dev.proofs.plugin;

import dev.proofs.plugin.check.CheckManager;
import dev.proofs.plugin.command.ProofsCommand;
import dev.proofs.plugin.command.ProofsMenus;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProofsPlugin extends JavaPlugin {

    private static ProofsPlugin instance;
    private CheckManager checkManager;

    public static ProofsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        checkManager = new CheckManager(this);
        ProofsMenus menus = new ProofsMenus(this, checkManager);

        ProofsCommand proofsCommand = new ProofsCommand(checkManager, menus);
        getCommand("proofs").setExecutor(proofsCommand);
        getCommand("proofs").setTabCompleter(proofsCommand);

        getServer().getPluginManager().registerEvents(checkManager, this);
        getServer().getPluginManager().registerEvents(menus, this);

        getLogger().info("Proofs plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (checkManager != null) {
            checkManager.endAllChecks(false);
        }
        getLogger().info("Proofs plugin disabled.");
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }
}
