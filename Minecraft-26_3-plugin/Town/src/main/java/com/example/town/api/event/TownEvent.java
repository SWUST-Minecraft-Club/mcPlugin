package com.example.town.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** 小镇事件基类（在触发它的区域线程同步回调，监听器内不要跨区域操作）。 */
public abstract class TownEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String townId;
    private final String townName;

    protected TownEvent(String townId, String townName) {
        this.townId = townId;
        this.townName = townName;
    }

    public String townId() {
        return townId;
    }

    public String townName() {
        return townName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
