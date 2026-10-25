package com.example.town.api.event;

/** 领地 buff 激活（购买成功后）。 */
public final class TownBuffActivateEvent extends TownEvent {

    private final String buffId;

    public TownBuffActivateEvent(String townId, String townName, String buffId) {
        super(townId, townName);
        this.buffId = buffId;
    }

    public String buffId() {
        return buffId;
    }
}
