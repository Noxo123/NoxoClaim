package fr.noxodev.noxoclaim;

import fr.noxodev.noxoclaim.commands.*;
import fr.noxodev.noxoclaim.gui.ClaimGui;
import fr.noxodev.noxoclaim.listeners.ClaimProtectionListener;
import fr.noxodev.noxoclaim.managers.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class NoxoClaim extends JavaPlugin {
    private ClaimManager claims;
    private MessageManager messages;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        claims = new ClaimManager(getDataFolder());
        messages = new MessageManager(getDataFolder());
        setupEconomy();
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);

        ClaimGui gui = new ClaimGui(this);
        ClaimCommand command = new ClaimCommand(this, gui);
        register("hclaim", command);
        register("uclaim", command);
        register("map", command);
        register("claim", command);
        register("chome", command);
        if (getCommand("claimadmin") != null) getCommand("claimadmin").setExecutor(new ClaimAdminCommand(this));

        getLogger().info("NoxoClaim activé — claims par chunks, sans outil de sélection.");
    }

    private void register(String name, ClaimCommand executor) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(executor);
            getCommand(name).setTabCompleter(executor);
        }
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
        if (economy == null) getLogger().warning("Aucune économie Vault détectée : les claims payants sont indisponibles.");
    }

    public boolean charge(org.bukkit.entity.Player player, double amount) {
        if (economy == null) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public String formatMoney(double amount) {
        return economy == null ? String.format("%.2f$", amount) : economy.format(amount);
    }

    public ClaimManager claims() { return claims; }
    public MessageManager messages() { return messages; }
    public Economy economy() { return economy; }
    public double chunkPrice() { return getConfig().getDouble("economy.cost-per-chunk", 500.0); }

    @Override
    public void onDisable() {
        if (claims != null) claims.save();
    }
}
