package fr.noxodev.noxoclaim.managers;
import org.bukkit.Location; import java.util.*;
public final class SelectionManager { public record Selection(Location first,Location second){} private final Map<UUID,Selection> map=new HashMap<>(); public void setFirst(UUID u,Location l){Selection s=map.get(u);map.put(u,new Selection(l,s==null?null:s.second()));} public void setSecond(UUID u,Location l){Selection s=map.get(u);map.put(u,new Selection(s==null?null:s.first(),l));} public Selection get(UUID u){return map.get(u);} public void clear(UUID u){map.remove(u);} }
