package fr.noxodev.noxoclaim.gui;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.commands.ClaimCommand;
import fr.noxodev.noxoclaim.effects.ClaimVisualizer;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Central GUI controller. */
public final class ClaimGui {
    private static final String MAIN="main", CLAIMS="claims", FLAGS="flags", HELP="help", MAP="map";
    private final NoxoClaim plugin;
    private ClaimCommand command;
    private final Map<UUID, Claim> flagClaims = new HashMap<>();
    private final Set<UUID> mapViewers = new HashSet<>();

    public ClaimGui(NoxoClaim plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(new InventoryListener(), plugin);
        long refresh = Math.max(10L, plugin.getConfig().getLong("map.refresh.interval-ticks", 40L));
        if (plugin.getConfig().getBoolean("map.refresh.enabled", true)) {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (UUID id : new HashSet<>(mapViewers)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !p.isOnline()) { mapViewers.remove(id); continue; }
                    Inventory top = p.getOpenInventory().getTopInventory();
                    if (!(top.getHolder() instanceof MenuHolder h) || !MAP.equals(h.type())) {
                        mapViewers.remove(id);
                        continue;
                    }
                    refreshMap(p);
                }
            }, refresh, refresh);
        }
    }

    public void setCommand(ClaimCommand command) { this.command = command; }

    private Inventory inv(Player p, int size, String type) {
        String title = switch (type) {
            case MAIN -> "Menu";
            case CLAIMS -> "Mes claims";
            case FLAGS -> "Protection";
            case HELP -> "Aide";
            default -> "Carte";
        };
        return Bukkit.createInventory(new MenuHolder(type), size, "§8§lNoxoClaim §7• " + title);
    }

    private void fill(Inventory i, Material m) {
        ItemStack x = item(m, " ");
        for (int s = 0; s < i.getSize(); s++) if (i.getItem(s) == null) i.setItem(s, x.clone());
    }

    private ItemStack item(Material m, String name, String... lore) {
        ItemStack x = new ItemStack(m);
        ItemMeta meta = x.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            x.setItemMeta(meta);
        }
        return x;
    }

    private void button(Inventory i, int slot, Material m, String name, String... lore) {
        if (slot >= 0 && slot < i.getSize()) i.setItem(slot, item(m, name, lore));
    }

    private String owner(Claim c) {
        String n = Bukkit.getOfflinePlayer(c.getOwner()).getName();
        return n == null ? "Inconnu" : n;
    }

    private String state(boolean b) { return b ? "§aActivé" : "§cDésactivé"; }

    public void openMain(Player p) {
        Inventory i = inv(p, 45, MAIN); fill(i, Material.GRAY_STAINED_GLASS_PANE);
        Claim c = plugin.claims().atChunk(p.getWorld().getName(), p.getChunk().getX(), p.getChunk().getZ());
        button(i,10,Material.BOOK,"§b§lMes claims","§7Voir et gérer tes claims.");
        button(i,13,c==null?Material.DIRT:c.getOwner().equals(p.getUniqueId())?Material.LIME_CONCRETE:Material.RED_CONCRETE,c==null?"§a§lClaim ce chunk":c.getOwner().equals(p.getUniqueId())?"§a§lGérer mon claim":"§c§lChunk protégé","§7Action sur le chunk actuel.");
        button(i,16,Material.SHIELD,"§6§lProtections","§7Gérer les flags du claim actuel.");
        button(i,22,Material.MAP,"§d§lCarte des claims","§7Cliquer un chunk pour agir.");
        button(i,31,Material.COMPASS,"§e§lInfos du chunk","§7Afficher les informations.");
        button(i,33,Material.PAPER,"§f§lAide","§7Commandes et fonctionnement.");
        button(i,40,Material.BARRIER,"§c§lFermer","§7Fermer le menu.");
        p.openInventory(i);
    }

    public void openClaims(Player p) {
        Inventory i=inv(p,54,CLAIMS); fill(i,Material.GRAY_STAINED_GLASS_PANE);
        List<Claim> cs=plugin.claims().owned(p.getUniqueId()); int slot=10;
        for(Claim c:cs){
            if(slot>=45) break;
            int cx=Math.floorDiv(c.getMinX(),16),cz=Math.floorDiv(c.getMinZ(),16);
            button(i,slot++,Material.LIME_CONCRETE,"§a§lChunk "+cx+" / "+cz,"§7Monde : §f"+c.getWorld(),"§7Membres : §f"+c.getMembers().size(),"","§e▶ Cliquer pour se téléporter");
        }
        if(cs.isEmpty()) button(i,22,Material.DIRT,"§e§lAucun claim","§7Utilise §f/claim§7 dans un chunk libre.");
        button(i,49,Material.ARROW,"§fRetour"); button(i,53,Material.BARRIER,"§cFermer"); p.openInventory(i);
    }

    public void openFlags(Player p, Claim c) {
        if(c==null) return;
        flagClaims.put(p.getUniqueId(),c);
        Inventory i=inv(p,45,FLAGS); fill(i,Material.GRAY_STAINED_GLASS_PANE);
        button(i,10,c.getFlag(ClaimFlag.PVP)?Material.LIME_DYE:Material.GRAY_DYE,"§c⚔ PvP",state(c.getFlag(ClaimFlag.PVP)),"§7Cliquer pour inverser");
        button(i,12,c.getFlag(ClaimFlag.FIRE)?Material.LIME_DYE:Material.GRAY_DYE,"§6🔥 Feu",state(c.getFlag(ClaimFlag.FIRE)),"§7Cliquer pour inverser");
        button(i,14,c.getFlag(ClaimFlag.EXPLOSIONS)?Material.LIME_DYE:Material.GRAY_DYE,"§e💥 Explosions",state(c.getFlag(ClaimFlag.EXPLOSIONS)),"§7Cliquer pour inverser");
        button(i,16,c.getFlag(ClaimFlag.MOB_GRIEFING)?Material.LIME_DYE:Material.GRAY_DYE,"§5👹 Grief des mobs",state(c.getFlag(ClaimFlag.MOB_GRIEFING)),"§7Cliquer pour inverser");
        button(i,22,c.getFlag(ClaimFlag.ENTRY)?Material.LIME_DYE:Material.GRAY_DYE,"§b🚪 Entrée",state(c.getFlag(ClaimFlag.ENTRY)),"§7Cliquer pour inverser");
        button(i,40,Material.ARROW,"§fRetour"); button(i,44,Material.BARRIER,"§cFermer"); p.openInventory(i);
    }

    public void openHelp(Player p) {
        Inventory i=inv(p,54,HELP); fill(i,Material.BLACK_STAINED_GLASS_PANE);
        button(i,10,Material.DIRT,"§a§l/claim","§7Claim le chunk actuel."); button(i,12,Material.CHEST,"§b§l/claim menu","§7Ouvre le menu principal."); button(i,14,Material.MAP,"§d§l/claim map","§7Carte interactive."); button(i,16,Material.END_ROD,"§e§l/claim voir","§7Affiche les contours."); button(i,28,Material.BOOK,"§f§l/claim list","§7Liste tes claims."); button(i,30,Material.COMPASS,"§f§l/claim info","§7Informations du chunk."); button(i,32,Material.SHIELD,"§6§l/claim flags","§7Gère les protections."); button(i,34,Material.PLAYER_HEAD,"§d§l/claim trust <joueur>","§7Ajoute un membre."); button(i,40,Material.ARROW,"§fRetour"); button(i,53,Material.BARRIER,"§cFermer"); p.openInventory(i);
    }

    public void openMap(Player p) {
        mapViewers.add(p.getUniqueId());
        Inventory current = p.getOpenInventory().getTopInventory();
        if (current.getHolder() instanceof MenuHolder h && MAP.equals(h.type()) && current.getSize() == 54) {
            renderMap(p, current);
            return;
        }
        Inventory i = inv(p,54,MAP);
        renderMap(p,i);
        p.openInventory(i);
    }

    private void refreshMap(Player p) {
        if(!p.isOnline()) return;
        Inventory i = p.getOpenInventory().getTopInventory();
        if (!(i.getHolder() instanceof MenuHolder h) || !MAP.equals(h.type())) { mapViewers.remove(p.getUniqueId()); return; }
        renderMap(p,i);
    }

    private void renderMap(Player p, Inventory i) {
        int pcx=p.getChunk().getX(), pcz=p.getChunk().getZ();
        fill(i,Material.BLACK_STAINED_GLASS_PANE);
        for(int dz=-2;dz<=2;dz++) for(int dx=-4;dx<=4;dx++) {
            int cx=pcx+dx,cz=pcz+dz;
            Claim c=plugin.claims().atChunk(p.getWorld().getName(),cx,cz);
            boolean own=c!=null&&c.getOwner().equals(p.getUniqueId());
            Material m=c==null?Material.GRAY_CONCRETE:own?Material.LIME_CONCRETE:Material.RED_CONCRETE;
            String name=c==null?"§7§lChunk libre":own?"§a§lTon claim":"§c§lClaim d'un autre joueur";
            button(i,(dz+2)*9+(dx+4),m,name,"§7Chunk : §f"+cx+" / "+cz,c==null?"§a▶ Cliquer pour claim":"§fPropriétaire : §e"+owner(c),"§e▶ Cliquer pour agir");
        }
        button(i,45,Material.GRAY_DYE,"§7Libre","§7Zone disponible.");
        button(i,46,Material.LIME_DYE,"§aTon claim","§7Tes zones.");
        button(i,47,Material.RED_DYE,"§cAutre claim","§7Zones des autres joueurs.");
        button(i,48,Material.CLOCK,"§b§lAuto-refresh","§7Carte actualisée automatiquement.");
        button(i,49,Material.COMPASS,"§ePosition actuelle","§7Centre : §f"+pcx+" / "+pcz);
        button(i,50,Material.SUNFLOWER,"§6§lRafraîchir","§7Actualiser maintenant.");
        button(i,53,Material.BARRIER,"§cFermer");
    }

    private final class InventoryListener implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void click(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player p)) return;
            Inventory top = e.getView().getTopInventory();
            if (!(top.getHolder() instanceof MenuHolder holder)) return;
            if (e.getClickedInventory() != top) return;
            int slot = e.getSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            e.setCancelled(true);
            String type=holder.type();
            if(type.equals(MAIN)){handleMain(p,slot);return;}
            if(type.equals(CLAIMS)){handleClaims(p,slot);return;}
            if(type.equals(FLAGS)){handleFlags(p,slot);return;}
            if(type.equals(HELP)){if(slot==40)openMain(p);else if(slot==53)p.closeInventory();return;}
            if(type.equals(MAP))handleMap(p,slot);
        }

        private void handleMain(Player p,int s){
            if(s==10) openClaims(p);
            else if(s==13){Claim c=plugin.claims().atChunk(p.getWorld().getName(),p.getChunk().getX(),p.getChunk().getZ());if(c==null){if(command!=null)command.claimCurrentChunk(p);else p.sendMessage("§cNoxoClaim : action indisponible.");}else if(c.getOwner().equals(p.getUniqueId()))openFlags(p,c);else p.sendMessage("§c§lNoxoClaim §8» §fCe chunk appartient à §e"+owner(c)+"§f.");}
            else if(s==16){Claim c=plugin.claims().at(p.getLocation());if(c!=null&&c.getOwner().equals(p.getUniqueId()))openFlags(p,c);else p.sendMessage("§e§lNoxoClaim §8» §fTu n'es pas dans ton claim.");}
            else if(s==22)openMap(p); else if(s==31)p.performCommand("claim info"); else if(s==33)openHelp(p); else if(s==40)p.closeInventory();
        }

        private void handleClaims(Player p,int s){
            if(s==49){openMain(p);return;} if(s==53){p.closeInventory();return;} if(s<10||s>44)return;
            List<Claim> cs=plugin.claims().owned(p.getUniqueId()); int index=s-10;
            if(index>=0&&index<cs.size()&&command!=null){p.closeInventory();command.teleportToClaim(p,cs.get(index));}
        }

        private void handleFlags(Player p,int s){
            if(s==40){openMain(p);return;} if(s==44){p.closeInventory();return;}
            Claim c=flagClaims.get(p.getUniqueId()); if(c==null||!c.getOwner().equals(p.getUniqueId()))return;
            ClaimFlag f=s==10?ClaimFlag.PVP:s==12?ClaimFlag.FIRE:s==14?ClaimFlag.EXPLOSIONS:s==16?ClaimFlag.MOB_GRIEFING:s==22?ClaimFlag.ENTRY:null;
            if(f==null)return; c.setFlag(f,!c.getFlag(f)); plugin.claims().save(); openFlags(p,c);
        }

        private void handleMap(Player p,int s){
            if(s==53){mapViewers.remove(p.getUniqueId());p.closeInventory();return;}
            if(s==50){refreshMap(p);return;}
            if(s>=45)return;
            int row=s/9,col=s%9;
            int cx=p.getChunk().getX()+col-4,cz=p.getChunk().getZ()+row-2;
            Claim c=plugin.claims().atChunk(p.getWorld().getName(),cx,cz);
            if(c==null){
                if(command==null){p.sendMessage("§c§lNoxoClaim §8» §fLe système de claim n'est pas disponible.");return;}
                boolean claimed=command.claimChunk(p,p.getWorld(),cx,cz);
                if(claimed){
                    p.sendMessage("§a§l✓ Claim créé depuis la carte ! §7Chunk §f"+cx+" / "+cz);
                    refreshMap(p);
                }
            } else if(c.getOwner().equals(p.getUniqueId())) {
                openFlags(p,c);
            } else {
                p.sendMessage("§c§lNoxoClaim §8» §fCe claim appartient à §e"+owner(c)+"§f.");
                ClaimVisualizer.show(plugin,p,c,plugin.getConfig().getInt("map.visual-duration-ticks",100));
            }
        }

        @EventHandler
        public void close(InventoryCloseEvent e){
            if(e.getPlayer() instanceof Player p&&e.getView().getTopInventory().getHolder() instanceof MenuHolder h&&MAP.equals(h.type())) mapViewers.remove(p.getUniqueId());
        }
    }

    private record MenuHolder(String type) implements InventoryHolder { @Override public Inventory getInventory(){return null;} }
}
