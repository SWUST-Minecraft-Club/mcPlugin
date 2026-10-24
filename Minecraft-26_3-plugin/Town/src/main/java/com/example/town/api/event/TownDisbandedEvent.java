package com.example.town.api.event;

/** 小镇解散（含筹备超时自动解散）。 */
public final class TownDisbandedEvent extends TownEvent {

    public TownDisbandedEvent(String townId, String townName) {
        super(townId, townName);
    }
}
