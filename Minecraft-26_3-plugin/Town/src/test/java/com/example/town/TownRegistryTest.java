package com.example.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.town.model.ChunkKey;
import com.example.town.model.Town;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TownRegistryTest {

    @Test
    void rejectsMemberAttachedToTwoTowns() {
        TownRegistry registry = new TownRegistry();
        UUID shared = UUID.randomUUID();
        registry.register(new Town("a", "甲镇", shared, 0L));
        Town other = new Town("b", "乙镇", shared, 0L);

        assertThrows(IllegalArgumentException.class, () -> registry.register(other));
        assertEquals("a", registry.snapshotOfMember(shared).id());
    }

    @Test
    void rejectsDuplicateGlobalInvitation() {
        TownRegistry registry = new TownRegistry();
        UUID invited = UUID.randomUUID();
        Town first = new Town("a", "甲镇", UUID.randomUUID(), 0L);
        first.invites().put(invited, Long.MAX_VALUE);
        registry.register(first);
        Town second = new Town("b", "乙镇", UUID.randomUUID(), 0L);
        second.invites().put(invited, Long.MAX_VALUE);

        assertThrows(IllegalArgumentException.class, () -> registry.register(second));
        assertEquals("a", registry.snapshotOfInvited(invited).id());
    }

    @Test
    void rejectsClaimOwnedByAnotherTown() {
        TownRegistry registry = new TownRegistry();
        ChunkKey key = new ChunkKey("world", 1, 2);
        Town first = new Town("a", "甲镇", UUID.randomUUID(), 0L);
        first.claims().add(key);
        registry.register(first);
        Town second = new Town("b", "乙镇", UUID.randomUUID(), 0L);
        second.claims().add(key);

        assertThrows(IllegalArgumentException.class, () -> registry.register(second));
        assertEquals("a", registry.snapshotOfChunk(key).id());
    }
}
