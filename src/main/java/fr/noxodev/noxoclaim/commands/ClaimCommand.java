package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
import fr.noxodev.noxoclaim.effects.ClaimEffects;
import fr.noxodev.noxoclaim.effects.ClaimVisualizer;
import fr.noxodev.noxoclaim.gui.ClaimGui;
import fr.noxodev.noxoclaim.models.Claim;
import fr.noxodev.noxoclaim.models.ClaimFlag;
import fr.noxodev.noxoclaim.utils.TeleportTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class ClaimCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("menu","map","voir","list","info","flags","trust","untrust","sethome","home","unclaim","help");
    private final NoxoClaim plugin;
    private final ClaimGui gui;
    public ClaimCommand(NoxoClaim plugin, ClaimGui gui) { this.plugin=plugin; this.gui=gui; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { plugin.messages().send(sender,"player-only"); return true; }
        String name=command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("claim")) {
            if (args.length==0) { claimCurrentChunk(p); return true; }
            handle(p,args); return true;
        }
        if (name.equals("hclaim")||name.equals("map")) { if(args.length==0) gui.openMain(p); else handle(p,args); return true; }
        if (name.equals("uclaim")) { unclaimCurrentChunk(p); return true; }
        if (name.equals("chome")) { if(args.length==0||args[0].equalsIgnoreCase("list")) gui.openClaims(p); else { Claim c=plugin.claims().owned(p.getUniqueId()).stream().filter(x->x.getName().equalsIgnoreCase(String.join(" ",args))).findFirst().orElse(null); if(c!=null) teleportToClaim(p,c); else plugin.messages().send(p,"home-not-found"); } return true; }
        return true;
    }
    public void handle(Player p,String[] a) {
        switch(a[0].toLowerCase(Locale.ROOT)) {
            case "menu","gui" -> gui.openMain(p); case "map" -> gui.openMap(p); case "voir","show","visual" -> showClaims(p,a);
            case "list" -> gui.openClaims(p); case "info" -> info(p); case "flags" -> flags(p,a); case "trust" -> trust(p,a,true); case "untrust" -> trust(p,a,false);
            case "sethome" -> setHome(p); case "home","tp" -> home(p); case "unclaim","delete","remove","abandon" -> unclaimCurrentChunk(p); case "help" -> sendUsage(p); default -> sendUsage(p);
        }
    }
    private void sendUsage(Player p){ p.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"); p.sendMessage("§b§lNoxoClaim §8• §fCommandes"); p.sendMessage("§e/claim §7→ Claim le chunk actuel"); for(String s:SUBCOMMANDS)p.sendMessage("§e/claim "+s); p.sendMessage("§7/claim voir [1-64|off]"); p.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"); }
    public boolean claimCurrentChunk(Player p){ return claimChunk(p,p.getWorld(),p.getChunk().getX(),p.getChunk().getZ()); }
    public boolean claimChunk(Player p,World world,int cx,int cz){
        if(!p.hasPermission("noxoclaim.claim")){plugin.messages().send(p,"no-permission");return false;}
        if(world==null)return false;
        Claim old=plugin.claims().atChunk(world.getName(),cx,cz);
        if(old!=null){p.sendMessage(old.getOwner().equals(p.getUniqueId())?"§e§lNoxoClaim §8» §fCe chunk est déjà à toi.":"§c§lNoxoClaim §8» §fCe chunk est déjà claimé.");return false;}
        if(plugin.claims().owned(p.getUniqueId()).size()>=plugin.getConfig().getInt("claim.max-per-player",10)){plugin.messages().send(p,"limit-reached");return false;}
        double cost=plugin.chunkPrice();
        if(plugin.economy()!=null&&cost>0&&!plugin.charge(p,cost)){p.sendMessage(plugin.messages().get("prefix")+plugin.messages().format("not-enough-money",Map.of("cost",plugin.formatMoney(cost),"chunks","1")));return false;}
        Claim c=new Claim(UUID.randomUUID(),p.getUniqueId(),world.getName(),cx*16,cz*16,cx*16+15,cz*16+15,"Chunk "+cx+", "+cz);
        plugin.claims().add(c); p.sendMessage("§a§l✓ Chunk claimé ! §7"+world.getName()+" §8• §f"+cx+", "+cz); ClaimEffects.onClaim(plugin,p); return true;
    }
    public void teleportToClaim(Player p,Claim c){ if(c==null)return; Location t=c.getHome(); if(t==null){World w=Bukkit.getWorld(c.getWorld()); if(w==null){plugin.messages().send(p,"home-not-found");return;} int x=c.getMinX()+8,z=c.getMinZ()+8; t=new Location(w,x+.5,w.getHighestBlockYAt(x,z)+1,z+.5);} new TeleportTask(plugin,p,t,plugin.getConfig().getInt("teleport.delay-seconds",3),plugin.getConfig().getBoolean("teleport.cancel-on-move",true)); }
    public boolean unclaimCurrentChunk(Player p){ Claim c=plugin.claims().atChunk(p.getWorld().getName(),p.getChunk().getX(),p.getChunk().getZ()); if(c==null){p.sendMessage("§c§lNoxoClaim §8» §fCe chunk n'est pas claimé.");return false;} if(!c.getOwner().equals(p.getUniqueId())&&!p.hasPermission("noxoclaim.admin")){plugin.messages().send(p,"not-owner");return false;} plugin.claims().remove(c);p.sendMessage("§a§l✓ Chunk unclaimé !");return true; }
    private void showClaims(Player p,String[] args){ if(args.length>=2&&args[1].equalsIgnoreCase("off")){ClaimVisualizer.stop(p);p.sendMessage("§b§lNoxoClaim §8» §fAffichage désactivé.");return;} int radius=plugin.getConfig().getInt("map.visual-radius",16); if(args.length>=2)try{radius=Integer.parseInt(args[1]);}catch(NumberFormatException e){p.sendMessage("§cUsage : /claim voir [rayon 1-64|off]");return;} radius=Math.max(1,Math.min(64,radius));String world=p.getWorld().getName();int pcx=p.getChunk().getX(),pcz=p.getChunk().getZ();List<Claim> nearby=new ArrayList<>();for(Claim c:plugin.claims().all()){if(!world.equals(c.getWorld()))continue;int minX=Math.floorDiv(c.getMinX(),16),maxX=Math.floorDiv(c.getMaxX(),16),minZ=Math.floorDiv(c.getMinZ(),16),maxZ=Math.floorDiv(c.getMaxZ(),16);if(maxX>=pcx-radius&&minX<=pcx+radius&&maxZ>=pcz-radius&&minZ<=pcz+radius)nearby.add(c);}ClaimVisualizer.showMany(plugin,p,nearby,plugin.getConfig().getInt("map.visual-duration-ticks",100));p.sendMessage("§b§lNoxoClaim §8» §f"+nearby.size()+" claim(s) affiché(s) dans un rayon de §e"+radius+" chunks§f."); }
    private void info(Player p){Claim c=current(p);if(c==null){p.sendMessage("§e§lNoxoClaim §8» §fCe chunk est libre.");return;}p.sendMessage("§b§lNoxoClaim §8• §fInformations");p.sendMessage("§7Nom : §f"+c.getName());p.sendMessage("§7Propriétaire : §f"+owner(c));p.sendMessage("§7Monde : §f"+c.getWorld());p.sendMessage("§7Zone : §f"+c.getMinX()+", "+c.getMinZ()+" §7→ §f"+c.getMaxX()+", "+c.getMaxZ());p.sendMessage("§7Membres : §f"+c.getMembers().size());}
    private void trust(Player p,String[] a,boolean add){Claim c=current(p);if(c==null)return;if(!c.getOwner().equals(p.getUniqueId())){plugin.messages().send(p,"not-owner");return;}if(a.length<2){p.sendMessage("§cUsage : /claim "+(add?"trust":"untrust")+" <joueur>");return;}UUID u=Bukkit.getOfflinePlayer(a[1]).getUniqueId();if(add)c.addMember(u);else c.removeMember(u);plugin.claims().save();p.sendMessage("§a✓ Joueur "+(add?"ajouté":"retiré")+".");}
    private void flags(Player p,String[] a){Claim c=current(p);if(c==null)return;if(a.length<2){gui.openFlags(p,c);return;}if(!c.getOwner().equals(p.getUniqueId())){plugin.messages().send(p,"not-owner");return;}try{ClaimFlag f=ClaimFlag.valueOf(a[1].toUpperCase(Locale.ROOT));c.setFlag(f,a.length<3||Boolean.parseBoolean(a[2]));plugin.claims().save();}catch(IllegalArgumentException e){p.sendMessage("§cFlag inconnue.");}}
    private void setHome(Player p){Claim c=current(p);if(c==null)return;if(!c.getOwner().equals(p.getUniqueId())){plugin.messages().send(p,"not-owner");return;}c.setHome(p.getLocation());plugin.claims().save();p.sendMessage("§a✓ Point de téléportation enregistré.");}
    private void home(Player p){Claim c=current(p);if(c!=null)teleportToClaim(p,c);}
    private Claim current(Player p){Claim c=plugin.claims().at(p.getLocation());if(c==null)p.sendMessage("§e§lNoxoClaim §8» §fCe chunk n'est pas claimé.");return c;}
    private String owner(Claim c){String n=Bukkit.getOfflinePlayer(c.getOwner()).getName();return n==null?"Inconnu":n;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(command.getName().equalsIgnoreCase("claim")||command.getName().equalsIgnoreCase("hclaim")){if(args.length==1)return SUBCOMMANDS.stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();if(args.length==2&&args[0].equalsIgnoreCase("voir"))return List.of("8","16","24","32","48","64","off");if(args.length==2&&(args[0].equalsIgnoreCase("trust")||args[0].equalsIgnoreCase("untrust")))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase().startsWith(args[1].toLowerCase())).toList();if(args.length==2&&args[0].equalsIgnoreCase("flags"))return Arrays.stream(ClaimFlag.values()).map(Enum::name).map(String::toLowerCase).toList();}return Collections.emptyList();}
}
