package com.example.town.model;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.store.PlotCodec;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlotTest {
    private final UUID owner=UUID.randomUUID(),tenant=UUID.randomUUID(),friend=UUID.randomUUID();
    private Plot leased() {
        return new Plot("home-a","住宅",owner,PlotKind.RESIDENTIAL,new PlotBounds("world",-17,-17,0,0),
                Set.of(friend),new PlotLease(tenant,10_000,1250,"coin",Set.of()),2,false,0,0);
    }

    @Test void negativeCoordinatesAndInclusiveEdgesUseCorrectChunksAndArea() {
        PlotBounds bounds=leased().bounds();
        assertEquals(324,bounds.area());
        assertEquals(9,bounds.chunks(9).size());
        assertTrue(bounds.chunks(9).contains(new ChunkKey("world",-2,-2)));
        assertTrue(bounds.contains("world",0,0));
        assertFalse(bounds.contains("other",0,0));
        assertThrows(IllegalArgumentException.class,()->bounds.chunks(8));
        assertTrue(bounds.overlaps(new PlotBounds("world",0,0,8,8)));
        assertFalse(bounds.overlaps(new PlotBounds("world",1,1,8,8)));
    }

    @Test void activeTenantControlsPrivateAccessAndBoundaryChangesPreserveIdentityAndContract() {
        Plot plot=leased();
        assertTrue(plot.canUse(tenant,1000));
        assertFalse(plot.canUse(owner,1000));
        assertFalse(plot.canUse(friend,1000));
        assertTrue(plot.canUse(owner,10_001));
        assertTrue(plot.canUse(friend,10_001));
        Plot edited=plot.withBounds(new PlotBounds("world",-20,-20,0,0));
        assertEquals(plot.id(),edited.id());
        assertEquals(plot.lease(),edited.lease());
        assertEquals(3,edited.revision());
        assertFalse(plot.requiresTenantConsent(edited.bounds(),1000));
        assertTrue(plot.requiresTenantConsent(plot.bounds().translate(1,0),1000));
        assertFalse(plot.requiresTenantConsent(plot.bounds().translate(1,0),10_001));
    }

    @Test void persistenceRoundTripRetainsLeaseAndRejectsFractionalMoney() {
        Plot original=leased();
        assertEquals(original,PlotCodec.decode(PlotCodec.encode(original)));
        var invalid=new HashMap<String,Object>(PlotCodec.encode(original));
        invalid.put("offered-rent-cents",12.5);
        assertThrows(IllegalArgumentException.class,()->PlotCodec.decode(invalid));
    }

    @Test void snapshotCannotMutateTownOrChangeWhenTownIsEdited() {
        Town town=new Town("town","小镇",owner,0);
        town.plots().put(leased().id(),leased());
        TownSnapshot snapshot=TownSnapshot.of(town);
        town.plots().clear();
        assertEquals(1,snapshot.plots().size());
        assertThrows(UnsupportedOperationException.class,()->snapshot.members().clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.plots().clear());
    }

    @Test void veryLargeRectanglesUseLongAreaAndBoundEnumerationBeforeAllocating() {
        PlotBounds bounds=new PlotBounds("world",-30_000_000,-30_000_000,30_000_000,30_000_000);
        assertTrue(bounds.area()>Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->bounds.chunks(65536));
    }
}
