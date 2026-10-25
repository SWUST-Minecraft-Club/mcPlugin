package com.example.town.api.event;

import com.example.town.model.ChunkKey;
import java.util.UUID;

/** 小镇圈下一块地（含成立时免费赠送的地块）。 */
public final class TownClaimEvent extends TownEvent {

    private final UUID actor;
    private final ChunkKey chunk;

    public TownClaimEvent(String townId, String townName, UUID actor, ChunkKey chunk) {
        super(townId, townName);
        this.actor = actor;
        this.chunk = chunk;
    }

    public UUID actor() {
        return actor;
    }

    public ChunkKey chunk() {
        return chunk;
    }
}
