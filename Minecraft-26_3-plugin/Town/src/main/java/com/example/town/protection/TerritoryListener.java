package com.example.town.protection;

import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.util.Threads;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** 领土按区块判定，与镇籍无关；步行和传送共用状态，镇内跨区块不重复提示。 */
public final class TerritoryListener implements Listener {
    private record Presence(String townId) { }
    private final TownPlugin plugin;
    private final Map<UUID,Presence> presence=new ConcurrentHashMap<>();

    public TerritoryListener(TownPlugin plugin) { this.plugin=plugin; }

    public void start() {
        // 兼顾入服、复活、世界切换以及站立时圈地/弃地；实体操作仍在玩家线程。
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,task->{
            for(Player player:Bukkit.getOnlinePlayers())
                Threads.forPlayer(plugin,player,()->update(player,player.getLocation()));
        },20,20);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        if(!ChunkKey.of(event.getFrom()).equals(ChunkKey.of(event.getTo())))
            update(event.getPlayer(),event.getTo());
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) {
        // 等传送完成再读取位置，避免异步传送失败后误报进入。
        event.getPlayer().getScheduler().runDelayed(plugin,
                task->update(event.getPlayer(),event.getPlayer().getLocation()),null,2);
    }

    @EventHandler public void join(PlayerJoinEvent event) { presence.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void quit(PlayerQuitEvent event) { presence.remove(event.getPlayer().getUniqueId()); }

    private void update(Player player,Location location) {
        var town=plugin.service().townAt(ChunkKey.of(location));
        String id=town!=null && town.active()?town.id():null;
        Presence next=new Presence(id),old=presence.put(player.getUniqueId(),next);
        if(next.equals(old) || id==null || !plugin.getConfig().getBoolean("territory-notice.enabled",true)) return;
        String name=town.name().endsWith("镇")?town.name():town.name()+"镇";
        var tag=Placeholder.unparsed("town",name);
        if(plugin.getConfig().getBoolean("territory-notice.chat",true)) plugin.messages().send(player,"territory-enter",tag);
        if(plugin.getConfig().getBoolean("territory-notice.title",true))
            player.showTitle(Title.title(plugin.messages().renderPlain("territory-title",tag),
                    plugin.messages().renderPlain("territory-subtitle",tag),
                    Title.Times.times(Duration.ofMillis(250),Duration.ofMillis(1800),Duration.ofMillis(500))));
    }
}
