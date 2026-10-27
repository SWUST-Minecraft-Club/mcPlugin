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
    private record Click(Point point,int index,long at) { }
    private final Map<UUID,Click> clicks=new ConcurrentHashMap<>();
    private final Map<UUID,Object> grants=new ConcurrentHashMap<>();
    private static final Particle.DustOptions SELECTED=new Particle.DustOptions(Color.fromRGB(48,245,168),1.65f);
    private static final Particle.DustOptions ORIGINAL=new Particle.DustOptions(Color.fromRGB(255,188,56),1.5f);

    public SelectionService(TownPlugin plugin) {
        this.plugin=plugin;
        toolKey=new NamespacedKey(plugin,"surveyor");
    }

    public void start() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,task->{
            for(Player player:Bukkit.getOnlinePlayers()) {
                if(selections.containsKey(player.getUniqueId()) || edits.containsKey(player.getUniqueId())
                        || (plugin.ui()!=null && plugin.ui().hasBoundary(player.getUniqueId())))
                    Threads.forPlayer(plugin,player,()->{
                        if(plugin.ui()!=null && plugin.ui().hasBoundary(player.getUniqueId())) plugin.ui().drawChunkPreview(player);
                        else if(plugin.ui()==null || !plugin.ui().isViewing(player)) render(player);
                    });
            }
        },8,8);
    }

    public void giveTool(Player player) {
        if(plugin.market()!=null && plugin.market().frozen(player.getUniqueId())) {
            player.sendPlainMessage("[小镇] 物品交付处理中，请完成后再领取工具。");return;
        }
        ItemStack tool=plugin.craftEngine()==null?null:plugin.craftEngine().buildItem("town:surveyor");
        if(tool==null) tool=new ItemStack(Material.FLINT);
        tool.editMeta(meta->{
            meta.displayName(plugin.messages().render("selection-tool-name"));
            meta.lore(java.util.List.of(plugin.messages().render("selection-tool-lore")));
            meta.getPersistentDataContainer().set(toolKey,PersistentDataType.BYTE,(byte)1);
        });
        if(!player.getInventory().addItem(tool).isEmpty()) {
            plugin.messages().send(player,"selection-inventory-full");
            return;
        }
        // 领取按钮与选点可能共用同一次交互，下一 tick 才接受新工具的点击。
        UUID actor=player.getUniqueId();
        Object grant=new Object();
        grants.put(actor,grant);
        player.getScheduler().run(plugin,task->grants.remove(actor,grant),()->grants.remove(actor,grant));
        if(plugin.ui()!=null) plugin.ui().closeForSurveying(player);
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
        player.sendActionBar(plugin.messages().renderPlain("selection-point",
                Placeholder.unparsed("index",String.valueOf(index)),Placeholder.unparsed("world",point.world()),
                Placeholder.unparsed("position",point.x()+", "+point.y()+", "+point.z())));
        player.playSound(player.getLocation(),org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,0.35f,index==1?1.2f:1.6f);
        render(player);
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
    public void clear(UUID player) { selections.remove(player); edits.remove(player); clicks.remove(player); grants.remove(player); }

    public boolean isTool(ItemStack item) {
        return item!=null && (item.getPersistentDataContainer().has(toolKey,PersistentDataType.BYTE)
                || (plugin.craftEngine()!=null && plugin.craftEngine().isItem(item,"town:surveyor")));
    }

    /** CE 与 Bukkit 交互共用；一击可能经过两条事件路径，只消费一次。 */
    public boolean click(Player player,ItemStack item,int index,Block block) {
        if(!isTool(item) || !player.hasPermission("town.use")
                || (plugin.ui()!=null && plugin.ui().isViewing(player))) return false;
        if(grants.containsKey(player.getUniqueId())) return true;
        Block target=block==null?player.getTargetBlockExact(64):block;
        Point point=target==null?null:Point.of(target.getLocation());
        long now=System.currentTimeMillis();
        Click old=clicks.put(player.getUniqueId(),new Click(point,index,now));
        if(old!=null && old.index()==index && java.util.Objects.equals(old.point(),point) && now-old.at()<200) return true;
        if(point==null) plugin.messages().send(player,"selection-target-missing");
        else point(player,index,point);
        return true;
    }

    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
    public void onTool(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND) return;
        int index=switch(event.getAction()) {
            case LEFT_CLICK_BLOCK,LEFT_CLICK_AIR -> 1;
            case RIGHT_CLICK_BLOCK,RIGHT_CLICK_AIR -> 2;
            default -> 0;
        };
        if(index==0 || !click(event.getPlayer(),event.getItem(),index,event.getClickedBlock())) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer().getUniqueId()); }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void protectToolTarget(BlockBreakEvent event) {
        if(isTool(event.getPlayer().getInventory().getItemInMainHand())) event.setCancelled(true);
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
            // XZ 地块选择不应被山坡或点击方块遮住；另保留建筑选区的真实上下沿。
            outline(player,box.footprint(),player.getLocation().getY()+0.12,SELECTED);
            if(Math.abs(box.minY()+1.05-player.getLocation().getY())>0.5)
                outline(player,box.footprint(),box.minY()+1.05,SELECTED);
            if(box.maxY()!=box.minY()) outline(player,box.footprint(),box.maxY()+1.05,SELECTED);
        }
        marker(player,selected.first(),SELECTED);
        marker(player,selected.second(),ORIGINAL);
    }

    private void marker(Player player,Point point,Particle.DustOptions color) {
        if(point==null || !player.getWorld().getName().equals(point.world())) return;
        double y=Math.max(point.y()+1.06,player.getLocation().getY()+0.1);
        BoundaryParticles.edge(player,point.x()+0.5,y,point.z()+0.5,point.x()+0.5,y+0.75,point.z()+0.5,color);
    }

    private void outline(Player player,PlotBounds bounds,double y,Particle.DustOptions color) {
        BoundaryParticles.outline(player,bounds,y,color);
    }
}
