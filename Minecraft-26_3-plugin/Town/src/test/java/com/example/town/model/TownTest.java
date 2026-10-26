package com.example.town.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TownTest {

    @Test
    void foundingGrantIsCappedAndCannotBeRefilledByReplacingHallOrChangingConfig() {
        Town town=new Town("town","小镇",UUID.randomUUID(),0);
        town.claims().add(new ChunkKey("world",0,0));
        assertEquals(3,town.grantFoundingClaims(9,4));
        town.freeClaimsRemaining(1);
        town.clearHall();
        town.hall("world",1,70,1,0,0);
        assertEquals(0,town.grantFoundingClaims(100,256));
        assertEquals(1,town.freeClaimsRemaining());
    }

    @Test
    void transferKeepsExactlyOneMayorAndDemotesPreviousMayor() {
        UUID oldMayor = UUID.randomUUID();
        UUID newMayor = UUID.randomUUID();
        Town town = new Town("town-a", "松风镇", oldMayor, 0L);
        town.members().put(newMayor, Role.MEMBER);

        town.mayor(newMayor);

        assertEquals(newMayor, town.mayor());
        assertEquals(Role.MAYOR, town.members().get(newMayor));
        assertEquals(Role.OFFICER, town.members().get(oldMayor));
        assertEquals(1, town.members().values().stream().filter(role -> role == Role.MAYOR).count());

        town.mayor(newMayor);
        assertEquals(Role.MAYOR, town.members().get(newMayor));
    }
}
