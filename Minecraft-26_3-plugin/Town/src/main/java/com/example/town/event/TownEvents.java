package com.example.town.event;

import com.example.town.api.event.TownBuffActivateEvent;
import com.example.town.api.event.TownClaimEvent;
import com.example.town.api.event.TownDisbandedEvent;
import com.example.town.api.event.TownFoundedEvent;
import com.example.town.api.event.TownUnclaimEvent;
import com.example.town.model.ChunkKey;
import java.util.UUID;
import org.bukkit.Bukkit;

/** 内部事件派发：业务层调用，转发为公开事件。 */
public final class TownEvents {

    public void fireFounded(UUID mayor, String townId, String townName) {
        Bukkit.getPluginManager().callEvent(new TownFoundedEvent(mayor, townId, townName));
    }

    public void fireDisbanded(String townId, String townName) {
        Bukkit.getPluginManager().callEvent(new TownDisbandedEvent(townId, townName));
    }

    public void fireClaim(String townId, String townName, UUID actor, ChunkKey chunk) {
        Bukkit.getPluginManager().callEvent(new TownClaimEvent(townId, townName, actor, chunk));
    }

    public void fireUnclaim(String townId, String townName, UUID actor, ChunkKey chunk) {
        Bukkit.getPluginManager().callEvent(new TownUnclaimEvent(townId, townName, actor, chunk));
    }

    public void fireBuffActivate(String townId, String buffId) {
        // 购买路径只有 townId；事件里的 townName 允许为 null（订阅方按需用 TownApi 查）
        Bukkit.getPluginManager().callEvent(new TownBuffActivateEvent(townId, null, buffId));
    }
}
