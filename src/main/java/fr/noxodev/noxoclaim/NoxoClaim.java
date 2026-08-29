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

public final class NoxoClaim extends JavaPlugin {
    private ClaimManager claims;
    private MessageManager messages;
    private Economy economy;
    private UpdateInfo updateInfo;
    private ClaimMapIntegration mapIntegration;
    private UpdateChecker updateChecker;

    @Override public void onEnable() {
        saveDefaultConfig(); saveResource("messages.yml", false);
        claims = new ClaimManager(getDataFolder()); messages = new MessageManager(getDataFolder()); setupEconomy();
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);
        mapIntegration = new ClaimMapIntegration(this);
        ClaimGui gui = new ClaimGui(this); ClaimCommand command = new ClaimCommand(this, gui);
        register("hclaim", command); register("uclaim", command); register("map", command); register("claim", command); register("chome", command);
        if (getCommand("claimadmin") != null) { ClaimAdminCommand admin = new ClaimAdminCommand(this); getCommand("claimadmin").setExecutor(admin); getCommand("claimadmin").setTabCompleter(admin); }
        startUpdateChecker(); getLogger().info("NoxoClaim " + getDescription().getVersion() + " activé.");
    }

    private void startUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        if (!getConfig().getBoolean("updates.enabled", true)) return;
        if (getConfig().getBoolean("updates.check-on-startup", true)) {
            Bukkit.getScheduler().runTaskLater(this, () -> updateChecker.check(getConfig().getBoolean("updates.notify-console", true)), 40L);
        }
        long hours = Math.max(1L, getConfig().getLong("updates.check-interval-hours", 6L));
        long ticks = hours * 60L * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> updateChecker.check(false), ticks, ticks);
    }

    public void notifyAdmins() { if (updateInfo == null || !updateInfo.available() || !getConfig().getBoolean("updates.notify-admins", true)) return; for (Player player : Bukkit.getOnlinePlayers()) if (player.hasPermission("noxoclaim.admin")) { player.sendMessage("§8[§bNoxoClaim§8] §eUne nouvelle version est disponible : §a" + updateInfo.latestVersion()); if (updateInfo.releaseUrl() != null) player.sendMessage("§7Release : §f" + updateInfo.releaseUrl()); } }
    public void setUpdateInfo(UpdateInfo info) { updateInfo = info; Bukkit.getScheduler().runTask(this, this::notifyAdmins); }
    public UpdateInfo updateInfo() { return updateInfo; }
    public UpdateChecker updateChecker() { return updateChecker; }
    private void register(String name, ClaimCommand executor) { if (getCommand(name) != null) { getCommand(name).setExecutor(executor); getCommand(name).setTabCompleter(executor); } }
    private void setupEconomy() { RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class); if (rsp != null) economy = rsp.getProvider(); if (economy == null) getLogger().warning("Aucune économie Vault détectée : les claims payants sont indisponibles."); }
    public boolean charge(Player player, double amount) { if (economy == null) return false; if (!economy.has(player, amount)) return false; return economy.withdrawPlayer(player, amount).transactionSuccess(); }
    public String formatMoney(double amount) { return economy == null ? String.format("%.2f$", amount) : economy.format(amount); }
    public ClaimManager claims() { return claims; } public MessageManager messages() { return messages; } public Economy economy() { return economy; } public double chunkPrice() { return getConfig().getDouble("economy.cost-per-chunk", 500.0); } public ClaimMapIntegration mapIntegration() { return mapIntegration; }
    @Override public void onDisable() { if (claims != null) claims.save(); }
}
