package com.example.town.api.event;

import com.example.town.model.ChunkKey;
import java.util.UUID;

/** 小镇放弃一块地。 */
public final class TownUnclaimEvent extends TownEvent {

    private final UUID actor;
    private final ChunkKey chunk;

    public TownUnclaimEvent(String townId, String townName, UUID actor, ChunkKey chunk) {
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
