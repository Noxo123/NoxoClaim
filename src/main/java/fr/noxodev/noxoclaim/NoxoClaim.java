package fr.noxodev.noxoclaim;

import fr.noxodev.noxoclaim.commands.*;
import fr.noxodev.noxoclaim.gui.ClaimGui;
import fr.noxodev.noxoclaim.listeners.*;
import fr.noxodev.noxoclaim.managers.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class NoxoClaim extends JavaPlugin {
    private ClaimManager claims;
    private SelectionManager selections;
    private MessageManager messages;
    private Economy economy;

    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        claims = new ClaimManager(getDataFolder());
        selections = new SelectionManager();
        messages = new MessageManager(getDataFolder());
        setupEconomy();
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        ClaimGui gui = new ClaimGui(this);
        ClaimCommand c = new ClaimCommand(this, gui);
        getCommand("claim").setExecutor(c); getCommand("claim").setTabCompleter(c);
        getCommand("chome").setExecutor(c); getCommand("chome").setTabCompleter(c);
        getCommand("claimadmin").setExecutor(new ClaimAdminCommand(this));
        getLogger().info("NoxoClaim activé — Paper 26.2/26.1.");
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
        if (economy == null) getLogger().warning("Aucune économie Vault détectée : les achats de chunks seront indisponibles.");
    }

    public boolean charge(org.bukkit.entity.Player player, double amount) {
        if (economy == null) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    public String formatMoney(double amount) { return economy == null ? String.format("%.2f$", amount) : economy.format(amount); }
    public ClaimManager claims() { return claims; }
    public SelectionManager selections() { return selections; }
    public MessageManager messages() { return messages; }
    public Economy economy() { return economy; }
    public double chunkPrice() { return getConfig().getDouble("economy.cost-per-chunk", 500.0); }
    public void onDisable() { if (claims != null) claims.save(); }
}
