package com.example.town.relocation;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MoveRulesTest {
    private Town town() {
        Town t=new Town("test","小镇",UUID.randomUUID(),0);t.state(TownState.ACTIVE);t.hall("world",0,65,0,0,0);
        for(int x=0;x<3;x++) for(int z=0;z<3;z++) t.claims().add(new ChunkKey("world",x,z));return t;
    }
    @Test void linkedPlotNeedsOwnerTenantAndTownApprovalWhileTenantRightsRemainPrivate() {
        Town t=town();UUID owner=UUID.randomUUID(),tenant=UUID.randomUUID();
        Plot p=new Plot("p","住宅",owner,PlotKind.RESIDENTIAL,new PlotBounds("world",5,5,9,9),Set.of(),
                new PlotLease(tenant,1000,100,"coin",Set.of()),0,false,0,0);t.plots().put(p.id(),p);
        MoveTransform move=new MoveTransform(new BlockBox("world",5,60,5,9,70,9),20,60,20,0);
        assertEquals(Set.of(tenant,t.mayor()),MoveRules.validate(TownSnapshot.of(t),owner,move,"p",false,false,1));
        assertEquals(Set.of(owner,t.mayor()),MoveRules.validate(TownSnapshot.of(t),tenant,move,"p",false,false,1));
        assertThrows(IllegalArgumentException.class,()->MoveRules.validate(TownSnapshot.of(t),owner,move,null,false,false,1));
        assertEquals(p.lease(),p.withBounds(move.plot(p.bounds())).lease());
    }
    @Test void coreRequiresDedicatedServiceAndTargetCannotLeaveTown() {
        Town t=town();var source=new BlockBox("world",0,65,0,0,65,0);
        MoveTransform move=new MoveTransform(source,20,65,20,0);
        assertThrows(IllegalArgumentException.class,()->MoveRules.validate(TownSnapshot.of(t),t.mayor(),move,null,false,false,0));
        assertTrue(MoveRules.validate(TownSnapshot.of(t),t.mayor(),move,null,true,false,0).isEmpty());
        assertThrows(IllegalArgumentException.class,()->MoveRules.validate(TownSnapshot.of(t),t.mayor(),move.adjust(100,0,0),null,true,false,0));
        assertFalse(MoveRules.collision(source,0,65,0,true));assertTrue(MoveRules.collision(source,20,65,20,true));
    }
    @Test void overlappingReservationsCannotBeReplacedOrReleasedByAnotherJob() {
        AreaLocks locks=new AreaLocks();BlockBox source=new BlockBox("world",0,0,0,9,9,9),target=source.translate(5,0,0);
        var first=new AreaLocks.Reservation("one","town",source,target);
        assertTrue(locks.acquire(first));assertTrue(locks.acquire(first));
        assertFalse(locks.acquire(new AreaLocks.Reservation("one","town",source,target.translate(50,0,0))));
        assertFalse(locks.acquire(new AreaLocks.Reservation("two","town",source,target)));
        locks.release("two");assertTrue(locks.locked("world",2,2,2));assertTrue(locks.townLocked("town"));
        locks.release("one");assertFalse(locks.townLocked("town"));
    }
}
