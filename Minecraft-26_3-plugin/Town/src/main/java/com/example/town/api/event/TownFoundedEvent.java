package com.example.town.api.event;

import java.util.UUID;

/** 小镇正式成立（扣费完成、状态转 ACTIVE 之后）。 */
public final class TownFoundedEvent extends TownEvent {

    private final UUID mayor;

    public TownFoundedEvent(UUID mayor, String townId, String townName) {
        super(townId, townName);
        this.mayor = mayor;
    }

    public UUID mayor() {
        return mayor;
    }
}
