package fr.noxodev.noxoclaim.commands;

import fr.noxodev.noxoclaim.NoxoClaim;
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
    private final NoxoClaim plugin; private final ClaimGui gui;
    public ClaimCommand(NoxoClaim plugin, ClaimGui gui) { this.plugin=plugin; this.gui=gui; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { plugin.messages().send(sender,"player-only"); return true; }
        String name=command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("claim")) {
            if (args.length == 0) return claimCurrentChunk(player);
            if (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) { gui.openMain(player); return true; }
            return handle(player,args);
        }
        if (name.equals("hclaim") || name.equals("map")) { if(args.length==0) gui.openMain(player); else handle(player,args); return true; }
        if(name.equals("uclaim")) { unclaimCurrentChunk(player); return true; }
        if(name.equals("chome")) { if(args.length==0||args[0].equalsIgnoreCase("list")) gui.openClaims(player); else teleportToNamedClaim(player,args[0]); return true; }
        return true;
    }

    private boolean handle(Player p,String[] a){ switch(a[0].toLowerCase(Locale.ROOT)){ case "menu","gui","map"->gui.openMain(p); case "list"->gui.openClaims(p); case "info"->info(p); case "flags"->flags(p,a); case "trust"->trust(p,a,true); case "untrust"->trust(p,a,false); case "sethome"->setHome(p); case "home","tp"->home(p); case "unclaim","delete","remove","abandon"->unclaimCurrentChunk(p); case "help"->gui.openHelp(p); default->p.sendMessage("§cUsage : /claim [menu|list|info|flags|trust|untrust|sethome|home|unclaim]"); } return true; }

    public boolean claimCurrentChunk(Player p){
        if(!p.hasPermission("noxoclaim.claim")){ plugin.messages().send(p,"no-permission"); return false; }
        String world=p.getWorld().getName(); int cx=p.getChunk().getX(), cz=p.getChunk().getZ(); Claim existing=plugin.claims().atChunk(world,cx,cz);
        if(existing!=null){ p.sendMessage(existing.getOwner().equals(p.getUniqueId())?"§e§lNoxoClaim §8» §fCe chunk est déjà à toi.":"§c§lNoxoClaim §8» §fCe chunk est déjà claimé."); return false; }
        if(plugin.claims().owned(p.getUniqueId()).size()>=plugin.getConfig().getInt("claim.max-per-player",10)){ plugin.messages().send(p,"limit-reached"); return false; }
        double cost=plugin.chunkPrice(); if(plugin.economy()!=null && cost>0 && !plugin.charge(p,cost)){ p.sendMessage(plugin.messages().get("prefix")+plugin.messages().format("not-enough-money",Map.of("cost",plugin.formatMoney(cost),"chunks","1"))); return false; }
        Claim c=new Claim(UUID.randomUUID(),p.getUniqueId(),world,cx*16,cz*16,cx*16+15,cz*16+15,"Chunk "+cx+", "+cz); plugin.claims().add(c);
        p.sendMessage("§a§l✓ Chunk claimé ! §7"+world+" §8• §f"+cx+", "+cz); return true;
    }

    public boolean unclaimCurrentChunk(Player p){ Claim c=plugin.claims().atChunk(p.getWorld().getName(),p.getChunk().getX(),p.getChunk().getZ()); if(c==null){p.sendMessage("§c§lNoxoClaim §8» §fCe chunk n'est pas claimé.");return false;} if(!c.getOwner().equals(p.getUniqueId())&&!p.hasPermission("noxoclaim.admin")){plugin.messages().send(p,"not-owner");return false;} plugin.claims().remove(c);p.sendMessage("§a§l✓ Chunk unclaimé !");return true; }
    private void info(Player p){Claim c=current(p);if(c==null){p.sendMessage("§e§lNoxoClaim §8» §fCe chunk est libre.");return;}p.sendMessage("§b§lNoxoClaim §8• §fInformations");p.sendMessage("§7Propriétaire : §f"+owner(c));p.sendMessage("§7Membres : §f"+c.getMembers().size());}
    private void trust(Player p,String[] a,boolean add){Claim c=current(p);if(c==null)return;if(!c.getOwner().equals(p.getUniqueId())){plugin.messages().send(p,"not-owner");return;}if(a.length<2){p.sendMessage("§cUsage : /claim "+(add?"trust":"untrust")+" <joueur>");return;}UUID u=Bukkit.getOfflinePlayer(a[1]).getUniqueId();if(add)c.addMember(u);else c.removeMember(u);plugin.claims().save();p.sendMessage("§a✓ Joueur "+(add?"ajouté":"retiré")+".");}
    private void flags(Player p,String[] a){Claim c=current(p);if(c==null)return;if(a.length<2){gui.openFlags(p,c);return;}if(!c.getOwner().equals(p.getUniqueId())){plugin.messages().send(p,"not-owner");return;}try{ClaimFlag f=ClaimFlag.valueOf(a[1].toUpperCase(Locale.ROOT));c.setFlag(f,a.length<3||Boolean.parseBoolean(a[2]));plugin.claims().save();}catch(IllegalArgumentException e){p.sendMessage("§cFlag inconnue.");}}
    private void setHome(Player p){Claim c=current(p);if(c==null)return;if(!c.getOwner().equals(p.getUniqueId())){plugin.messages().send(p,"not-owner");return;}c.setHome(p.getLocation());plugin.claims().save();p.sendMessage("§a✓ Point de téléportation enregistré.");}
    private void home(Player p){Claim c=current(p);if(c!=null)teleport(p,c);}
    private void teleportToNamedClaim(Player p,String n){Claim c=plugin.claims().owned(p.getUniqueId()).stream().filter(x->x.getName().equalsIgnoreCase(n)).findFirst().orElse(null);if(c==null){plugin.messages().send(p,"home-not-found");return;}teleport(p,c);}
    private void teleport(Player p,Claim c){Location t=c.getHome();if(t==null){World w=Bukkit.getWorld(c.getWorld());if(w==null){plugin.messages().send(p,"home-not-found");return;}int x=c.getMinX()+8,z=c.getMinZ()+8;t=new Location(w,x+.5,w.getHighestBlockYAt(x,z)+1,z+.5);}new TeleportTask(plugin,p,t,plugin.getConfig().getInt("teleport.delay-seconds",3),plugin.getConfig().getBoolean("teleport.cancel-on-move",true));}
    private Claim current(Player p){Claim c=plugin.claims().atChunk(p.getWorld().getName(),p.getChunk().getX(),p.getChunk().getZ());if(c==null)p.sendMessage("§e§lNoxoClaim §8» §fCe chunk n'est pas claimé.");return c;}
    private String owner(Claim c){String n=Bukkit.getOfflinePlayer(c.getOwner()).getName();return n==null?"Inconnu":n;}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(c.getName().equalsIgnoreCase("claim")&&args.length==1)return List.of("menu","list","info","flags","trust","untrust","sethome","home","unclaim").stream().filter(x->x.startsWith(args[0].toLowerCase())).toList();if(c.getName().equalsIgnoreCase("hclaim")&&args.length==1)return List.of("menu","list","info","flags","trust","untrust","sethome","home","unclaim");return List.of();}
}
