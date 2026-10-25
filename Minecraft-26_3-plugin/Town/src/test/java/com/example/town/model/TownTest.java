package com.example.town.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TownTest {

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
