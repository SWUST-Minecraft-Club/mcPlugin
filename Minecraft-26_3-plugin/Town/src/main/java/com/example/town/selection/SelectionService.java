package com.example.town.selection;

import com.example.town.TownPlugin;
import com.example.town.model.BlockBox;
import com.example.town.model.PlotBounds;
import com.example.town.service.PlotService;
import com.example.town.util.Threads;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** 选区与边界预览只向查看者发粒子，不写入世界，也不把工具点击交给方块交互。 */
public final class SelectionService implements Listener {
    public record Point(String world,int x,int y,int z) {
        public static Point of(Location location) {
            return new Point(location.getWorld().getName(),location.getBlockX(),location.getBlockY(),location.getBlockZ());
        }
    }
    private record Selection(Point first,Point second) { }
    private final TownPlugin plugin;
    private final NamespacedKey toolKey;
    private final Map<UUID,Selection> selections=new ConcurrentHashMap<>();
    private final Map<UUID,PlotService.EditProposal> edits=new ConcurrentHashMap<>();
    private static final Particle.DustOptions SELECTED=new Particle.DustOptions(Color.fromRGB(110,174,140),1.1f);
    private static final Particle.DustOptions ORIGINAL=new Particle.DustOptions(Color.fromRGB(211,180,122),1.0f);

    public SelectionService(TownPlugin plugin) {
        this.plugin=plugin;
        toolKey=new NamespacedKey(plugin,"surveyor");
    }

    public void start() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,task->{
            for(Player player:Bukkit.getOnlinePlayers()) {
                if(selections.containsKey(player.getUniqueId()) || edits.containsKey(player.getUniqueId()))
                    Threads.forPlayer(plugin,player,()->render(player));
            }
        },10,10);
    }

    public void giveTool(Player player) {
        if(plugin.market()!=null && plugin.market().frozen(player.getUniqueId())) {
            player.sendPlainMessage("[小镇] 物品交付处理中，请完成后再领取工具。");return;
        }
        ItemStack tool=plugin.craftEngine()==null?null:plugin.craftEngine().buildItem("town:surveyor");
        if(tool==null) tool=new ItemStack(Material.STICK);
        tool.editMeta(meta->{
            meta.displayName(plugin.messages().render("selection-tool-name"));
            meta.lore(java.util.List.of(plugin.messages().render("selection-tool-lore")));
            meta.getPersistentDataContainer().set(toolKey,PersistentDataType.BYTE,(byte)1);
        });
        if(!player.getInventory().addItem(tool).isEmpty()) {
            plugin.messages().send(player,"selection-inventory-full");
            return;
        }
        plugin.messages().send(player,"selection-help");
    }

    public void point(Player player,int index,Point point) {
        UUID actor=player.getUniqueId();
        selections.compute(actor,(id,old)->{
            Point first=old==null?null:old.first(),second=old==null?null:old.second();
            if(index==1) first=point; else if(index==2) second=point; else throw new IllegalArgumentException("selection-invalid");
            if(first!=null && second!=null && !first.world().equals(second.world())) {
                if(index==1) second=null; else first=null;
            }
            return new Selection(first,second);
        });
        edits.remove(actor);
        plugin.messages().send(player,"selection-point",Placeholder.unparsed("index",String.valueOf(index)),
                Placeholder.unparsed("world",point.world()),Placeholder.unparsed("position",point.x()+", "+point.y()+", "+point.z()));
        Selection current=selections.get(actor);
        if(current.first()!=null && current.second()!=null) {
            BlockBox box=box(actor);
            plugin.messages().send(player,"selection-ready",Placeholder.unparsed("area",String.valueOf(box.footprint().area())),
                    Placeholder.unparsed("size",box.width()+" × "+box.height()+" × "+box.depth()));
        }
    }

    public void selectTarget(Player player,int index) {
        Block block=player.getTargetBlockExact(64);
        if(block==null) throw new IllegalArgumentException("selection-target-missing");
        point(player,index,Point.of(block.getLocation()));
    }

    public BlockBox box(UUID player) {
        Selection selection=selections.get(player);
        if(selection==null || selection.first()==null || selection.second()==null) throw new IllegalArgumentException("selection-incomplete");
        Point a=selection.first(),b=selection.second();
        return new BlockBox(a.world(),Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()),
                Math.max(a.x(),b.x()),Math.max(a.y(),b.y()),Math.max(a.z(),b.z()));
    }

    public void previewEdit(Player player,PlotService.EditProposal proposal) { edits.put(player.getUniqueId(),proposal); render(player); }
    public void clear(UUID player) { selections.remove(player); edits.remove(player); }

    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
    public void onTool(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND || event.getItem()==null ||
                !event.getItem().getPersistentDataContainer().has(toolKey,PersistentDataType.BYTE)) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if(event.getClickedBlock()==null) return;
        if(event.getAction()==Action.LEFT_CLICK_BLOCK) point(event.getPlayer(),1,Point.of(event.getClickedBlock().getLocation()));
        else if(event.getAction()==Action.RIGHT_CLICK_BLOCK) point(event.getPlayer(),2,Point.of(event.getClickedBlock().getLocation()));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer().getUniqueId()); }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void protectToolTarget(BlockBreakEvent event) {
        if(event.getPlayer().getInventory().getItemInMainHand().getPersistentDataContainer()
                .has(toolKey,PersistentDataType.BYTE)) event.setCancelled(true);
    }

    private void render(Player player) {
        var edit=edits.get(player.getUniqueId());
        if(edit!=null) {
            if(edit.expiresAt()<=System.currentTimeMillis()) { edits.remove(player.getUniqueId(),edit); return; }
            outline(player,edit.previous(),player.getLocation().getBlockY()+0.1,ORIGINAL);
            outline(player,edit.next(),player.getLocation().getBlockY()+0.15,SELECTED);
            return;
        }
        Selection selected=selections.get(player.getUniqueId());
        if(selected==null) return;
        if(selected.first()!=null && selected.second()!=null) {
            BlockBox box=box(player.getUniqueId());
            outline(player,box.footprint(),box.minY()+0.05,SELECTED);
            if(box.maxY()!=box.minY()) outline(player,box.footprint(),box.maxY()+1.05,SELECTED);
        }
    }

    private void outline(Player player,PlotBounds bounds,double y,Particle.DustOptions color) {
        if(!player.getWorld().getName().equals(bounds.world())) return;
        double x1=bounds.minX(),x2=(double)bounds.maxX()+1,z1=bounds.minZ(),z2=(double)bounds.maxZ()+1;
        edge(player,x1,y,z1,x2,y,z1,color); edge(player,x2,y,z1,x2,y,z2,color);
        edge(player,x2,y,z2,x1,y,z2,color); edge(player,x1,y,z2,x1,y,z1,color);
    }

    private void edge(Player player,double ax,double ay,double az,double bx,double by,double bz,Particle.DustOptions color) {
        double length=Math.sqrt((bx-ax)*(bx-ax)+(by-ay)*(by-ay)+(bz-az)*(bz-az));
        int samples=(int)Math.clamp(Math.ceil(length),1,24);
        Location viewer=player.getLocation();
        for(int i=0;i<=samples;i++) {
            double t=(double)i/samples,x=ax+(bx-ax)*t,y=ay+(by-ay)*t,z=az+(bz-az)*t;
            if(viewer.distanceSquared(new Location(player.getWorld(),x,y,z))>160*160) continue;
            player.spawnParticle(Particle.DUST,x,y,z,1,0,0,0,0,color);
        }
    }
}
