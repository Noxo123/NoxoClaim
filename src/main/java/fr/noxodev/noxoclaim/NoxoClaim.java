package fr.noxodev.noxoclaim;

import fr.noxodev.noxoclaim.commands.*;
import fr.noxodev.noxoclaim.gui.ClaimGui;
import fr.noxodev.noxoclaim.listeners.ClaimProtectionListener;
import fr.noxodev.noxoclaim.managers.*;
import fr.noxodev.noxoclaim.map.ClaimMapIntegration;
import fr.noxodev.noxoclaim.update.UpdateChecker;
import fr.noxodev.noxoclaim.update.UpdateInfo;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class NoxoClaim extends JavaPlugin {
    private static final String PLUGMAN_X = "PlugManX";
    private ClaimManager claims;
    private MessageManager messages;
    private Economy economy;
    private UpdateInfo updateInfo;
    private ClaimMapIntegration mapIntegration;
    private UpdateChecker updateChecker;
    private boolean plugManX;

    @Override public void onEnable() {
        saveDefaultConfig();
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.isFile()) saveResource("messages.yml", false);
        claims = new ClaimManager(getDataFolder());
        messages = new MessageManager(getDataFolder());
        setupEconomy();
        detectPlugManX();
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);
        mapIntegration = new ClaimMapIntegration(this);

        ClaimGui gui = new ClaimGui(this);
        ClaimCommand command = new ClaimCommand(this, gui);
        gui.setCommand(command);
        register("hclaim", command);
        register("uclaim", command);
        register("map", command);
        register("claim", command);
        register("chome", command);

        if (getCommand("claimadmin") != null) {
            ClaimAdminCommand admin = new ClaimAdminCommand(this);
            getCommand("claimadmin").setExecutor(admin);
            getCommand("claimadmin").setTabCompleter(admin);
        }
        startUpdateChecker();
        getLogger().info("NoxoClaim " + getDescription().getVersion() + " activé.");
        getLogger().info("Modules: protection=OK, map=OK, economy=" + (economy != null ? "OK" : "indisponible") + ", PlugManX=" + (plugManX ? "OK" : "absent") + ".");
    }

    private void detectPlugManX() {
        plugManX = getServer().getPluginManager().getPlugin(PLUGMAN_X) != null;
        if (!plugManX) return;
        getServer().getScheduler().runTask(this, () -> { if (isEnabled()) getLogger().fine("PlugManX est actif."); });
    }
    public boolean isPlugManXAvailable() { return plugManX && getServer().getPluginManager().getPlugin(PLUGMAN_X) != null; }
    private void startUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        if (!getConfig().getBoolean("updates.enabled", true)) return;
        if (getConfig().getBoolean("updates.check-on-startup", true)) Bukkit.getScheduler().runTaskLater(this, () -> updateChecker.check(getConfig().getBoolean("updates.notify-console", true)), 40L);
        long hours = Math.max(1L, getConfig().getLong("updates.check-interval-hours", 6L));
        long ticks = Math.max(20L * 60L * 5L, hours * 60L * 60L * 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> updateChecker.check(false), ticks, ticks);
    }
    public void notifyAdmins() {
        if (updateInfo == null || !updateInfo.available() || !getConfig().getBoolean("updates.notify-admins", true)) return;
        for (Player player : Bukkit.getOnlinePlayers()) if (player.hasPermission("noxoclaim.admin")) player.sendMessage("§8[§bNoxoClaim§8] §eUne nouvelle build est disponible : §a" + updateInfo.latestVersion());
    }
    public void setUpdateInfo(UpdateInfo info) { updateInfo = info; Bukkit.getScheduler().runTask(this, this::notifyAdmins); }
    public UpdateInfo updateInfo() { return updateInfo; }
    public UpdateChecker updateChecker() { return updateChecker; }
    private void register(String name, ClaimCommand executor) { if (getCommand(name) != null) { getCommand(name).setExecutor(executor); getCommand(name).setTabCompleter(executor); } }
    private void setupEconomy() {
        economy = null;
        if (!getConfig().getBoolean("economy.enabled", true)) return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
        if (economy == null) getLogger().warning("Aucun fournisseur d'économie Vault détecté : les claims payants sont indisponibles.");
        else getLogger().info("Économie Vault détectée : " + economy.getName());
    }
    public boolean charge(Player player, double amount) { if (amount <= 0) return true; if (economy == null || !economy.has(player, amount)) return false; return economy.withdrawPlayer(player, amount).transactionSuccess(); }
    public String formatMoney(double amount) { return economy == null ? String.format("%.2f", amount) : economy.format(amount); }
    public ClaimManager claims() { return claims; }
    public MessageManager messages() { return messages; }
    public Economy economy() { return economy; }
    public double chunkPrice() { return getConfig().getDouble("economy.cost-per-chunk", 500.0); }
    public ClaimMapIntegration mapIntegration() { return mapIntegration; }
    @Override public void onDisable() { if (claims != null) claims.save(); getLogger().info("NoxoClaim désactivé proprement."); }
}
