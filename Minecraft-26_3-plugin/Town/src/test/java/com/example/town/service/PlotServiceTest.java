package com.example.town.service;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.TownRegistry;
import com.example.town.model.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlotServiceTest {
    private final UUID mayor=UUID.randomUUID(),owner=UUID.randomUUID(),tenant=UUID.randomUUID(),stranger=UUID.randomUUID();
    private final TownRegistry registry=new TownRegistry();
    private final AtomicInteger saves=new AtomicInteger();
    private final PlotService service=new PlotService(registry,saves::incrementAndGet,new PlotService.Limits(1024,10,60_000));
    private final Town town;

    PlotServiceTest() {
        town=new Town("town","小镇",mayor,0);
        town.state(TownState.ACTIVE);
        town.hall("world",0,70,0,0,0);
        town.claims().add(new ChunkKey("world",0,0));
        town.members().put(owner,Role.MEMBER);
        registry.register(town);
    }
    private Plot create() { return service.create(mayor,"住宅",PlotKind.RESIDENTIAL,new PlotBounds("world",2,2,7,7),owner); }
    private void leased(Plot p) {
        town.plots().put(p.id(),new Plot(p.id(),p.name(),p.owner(),p.kind(),p.bounds(),Set.of(),
                new PlotLease(tenant,100_000,1000,"coin",Set.of()),p.revision(),false,0,0));
    }
    private void code(String code,Runnable action) {
        assertEquals(code,assertThrows(IllegalArgumentException.class,action::run).getMessage());
    }

    @Test void tenantMustApproveShrinkAndTheOwnerMustThenConfirm() {
        Plot p=create(); leased(p);
        var next=new PlotBounds("world",3,3,7,7);
        var proposal=service.propose(owner,town.id(),p.id(),next,1000);
        assertEquals(Set.of(tenant),proposal.awaiting());
        assertEquals(p.bounds(),registry.snapshot(town.id()).plots().get(p.id()).bounds());
        code("plot-consent-pending",()->service.confirm(owner,proposal.token(),1001));
        code("plot-consent-denied",()->service.approve(stranger,proposal.token(),1001));
        service.approve(tenant,proposal.token(),1002);
        code("plot-consent-denied",()->service.confirm(tenant,proposal.token(),1003));
        Plot edited=service.confirm(owner,proposal.token(),1003);
        assertEquals(next,edited.bounds());
        assertEquals(tenant,edited.lease().tenant());
        assertEquals(p.id(),edited.id());
        code("plot-proposal-expired",()->service.confirm(owner,proposal.token(),1004));
    }

    @Test void administrativeEditsNeedOwnerConsentAndConcurrentChangesInvalidateTheProposal() {
        Plot p=create();
        var proposal=service.propose(mayor,town.id(),p.id(),new PlotBounds("world",1,1,7,7),1000);
        assertEquals(Set.of(owner),proposal.awaiting());
        service.approve(owner,proposal.token(),1001);
        service.trust(owner,town.id(),p.id(),stranger,true,1002);
        code("action-state-changed",()->service.confirm(mayor,proposal.token(),1003));
        assertEquals(p.bounds(),registry.snapshot(town.id()).plots().get(p.id()).bounds());
    }

    @Test void privateAccessIsProtectedFromTownMembersAndMayorAndSurvivesKickingTheOwner() {
        Plot p=create();
        for (UUID actor:Set.of(mayor,stranger)) assertFalse(PlotRules.permits(registry.snapshot(town.id()),actor,"world",4,4,PlotRules.Action.CONTAINER,1000));
        town.members().remove(owner);
        registry.unindexMember(owner);
        assertTrue(PlotRules.permits(registry.snapshot(town.id()),owner,"world",4,4,PlotRules.Action.BUILD,1000));
        service.trust(owner,town.id(),p.id(),stranger,true,1000);
        assertTrue(PlotRules.permits(registry.snapshot(town.id()),stranger,"world",4,4,PlotRules.Action.CONTAINER,1000));
    }

    @Test void tenantAndPublicPermissionsDoNotOpenContainersToVisitorsOrFormerOwner() {
        Plot p=create(); leased(p);
        assertFalse(PlotRules.permits(registry.snapshot(town.id()),owner,"world",4,4,PlotRules.Action.CONTAINER,1000));
        assertTrue(PlotRules.permits(registry.snapshot(town.id()),tenant,"world",4,4,PlotRules.Action.CONTAINER,1000));
        service.create(mayor,"广场",PlotKind.PUBLIC,new PlotBounds("world",8,8,10,10),mayor);
        assertTrue(PlotRules.permits(registry.snapshot(town.id()),stranger,"world",9,9,PlotRules.Action.INTERACT,1000));
        assertFalse(PlotRules.permits(registry.snapshot(town.id()),stranger,"world",9,9,PlotRules.Action.CONTAINER,1000));
    }

    @Test void overlapForeignChunksAndUnboundedSelectionsAreRejectedWithoutMutation() {
        create();
        code("plot-overlap",()->service.create(mayor,"重叠",PlotKind.RESIDENTIAL,new PlotBounds("world",7,7,9,9),owner));
        code("plot-outside-town",()->service.create(mayor,"越界",PlotKind.RESIDENTIAL,new PlotBounds("world",15,15,16,16),owner));
        code("plot-too-large",()->service.create(mayor,"大选区",PlotKind.RESIDENTIAL,new PlotBounds("world",0,0,100_000,100_000),owner));
        code("plot-manager-required",()->service.create(owner,"越权",PlotKind.RESIDENTIAL,new PlotBounds("world",8,8,9,9),owner));
        assertEquals(1,registry.snapshot(town.id()).plots().size());
        assertEquals(1,saves.get());
    }
    @Test void tenantControlsTheirVisitorSettingAndItExpiresWithTheLease() {
        Plot p=create();long now=System.currentTimeMillis();
        town.plots().put(p.id(),new Plot(p.id(),p.name(),p.owner(),p.kind(),p.bounds(),Set.of(),
                new PlotLease(tenant,now+60_000,1000,"coin",Set.of()),p.revision(),false,0,0));
        code("plot-owner-required",()->service.update(owner,town.id(),p.id(),null,null,true));
        Plot next=service.update(tenant,town.id(),p.id(),null,null,true);
        assertTrue(next.publicInteractionAt(now));assertFalse(next.publicInteractionAt(now+60_001));
        assertFalse(next.publicInteraction());
        Plot trusted=service.trust(tenant,town.id(),p.id(),stranger,true,now);
        assertTrue(trusted.lease().publicInteraction());
        assertEquals(trusted,com.example.town.store.PlotCodec.decode(com.example.town.store.PlotCodec.encode(trusted)));
    }
    @Test void rentCurrencySurvivesUnrelatedEditsAndSerialization() {
        Plot p=create();Plot offered=service.offerRent(owner,town.id(),p.id(),1000,60_000,0,"COIN_A");
        service.trust(owner,town.id(),p.id(),stranger,true,0);
        Plot renamed=service.update(owner,town.id(),p.id(),"新住宅",null,null);
        assertEquals("COIN_A",renamed.offeredCurrency());
        assertEquals("COIN_A",renamed.withBounds(renamed.bounds()).offeredCurrency());
        assertEquals(renamed,com.example.town.store.PlotCodec.decode(com.example.town.store.PlotCodec.encode(renamed)));
    }
    @Test void constructionPreventsCreatingAndEditingOwnershipInsideTheReservation() {
        Plot p=create();service.constructionLock(bounds->bounds.overlaps(new PlotBounds("world",5,5,12,12)));
        code("move-area-locked",()->service.transfer(owner,town.id(),p.id(),stranger));
        code("move-area-locked",()->service.create(mayor,"施工",PlotKind.PUBLIC,new PlotBounds("world",8,8,10,10),mayor));
        assertEquals(owner,town.plots().get(p.id()).owner());
    }
    @Test void openingAShopAfterABoundaryPreviewPreventsCommittingThatStalePlan() {
        Plot p=create();
        var proposal=service.propose(owner,town.id(),p.id(),new PlotBounds("world",3,3,7,7),1000);
        service.commercialAssets(id->id.equals(p.id()));
        code("plot-commercial-assets",()->service.confirm(owner,proposal.token(),1001));
        assertEquals(p.bounds(),town.plots().get(p.id()).bounds());
    }
}
