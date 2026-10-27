package com.example.town.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.store.CivicCodec;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ResearchTest {
    private CivicCatalog catalog() {
        return new CivicCatalog(2,6,.7,.5,.05,Map.of("planning",new CivicCatalog.Technology("planning","规划",100,5,CivicCatalog.TechnologyEffect.LAND_CAPACITY,16)),
                Map.of("research",new CivicCatalog.PolicyDef("research","科研",100,1000,CivicCatalog.Scope.MEMBERS,"",0,0,0,0,.25,0,0,0)));
    }
    @Test void paymentStartsResearchWithoutGrantingTheLevelAndCompletionDoesNotChargeAgain() {
        UUID actor=UUID.randomUUID();var catalog=catalog();
        var state=CivicState.empty().transact("fund",actor,"coin",1000,"捐献",1,true)
                .transact("study",actor,"coin",-100,"研究",1000,false).startResearch("planning",1800,1000,catalog);
        assertEquals(0,state.level("planning"));assertEquals(900,state.balance("coin"));
        assertEquals(0,state.advanceResearch(2799).level("planning"));
        var done=state.advanceResearch(2800);
        assertEquals(1,done.level("planning"));assertNull(done.research());assertEquals(900,done.balance("coin"));
        assertSame(done,done.advanceResearch(99999));
        assertSame(done,done.transact("study",actor,"coin",-100,"研究",10000,false));
        assertThrows(IllegalArgumentException.class,()->state.startResearch("archives",1000,1100,catalog));
    }
    @Test void startingAPolicyMidResearchDoesNotAccelerateWorkAlreadyDone() {
        var c=catalog();var study=CivicState.empty().startResearch("planning",2000,1000,c);
        var boosted=study.advanceResearch(2000).policy("research",4000).researchBonuses(c,2000);
        assertEquals(1000,boosted.research().completedMillis());
        assertEquals(800,boosted.research().remainingMillis(2000));
        assertEquals(0,boosted.advanceResearch(2799).level("planning"));
        assertEquals(1,boosted.advanceResearch(2800).level("planning"));
    }
    @Test void offlineCatchupSplitsAtEachExpiryAndCapsOverlappingBoosts() {
        var r=new Research("planning",1,10_000,0,1000,.5,List.of(new Research.Boost(.4,3000),new Research.Boost(.4,5000)));
        assertEquals(.5,r.speedBonus(1000),1e-8);
        assertEquals(3000,r.advance(3000).completedMillis(),1e-8);
        assertEquals(5800,r.advance(5000).completedMillis(),1e-8);
        assertEquals(8200,r.remainingMillis(1000));
        assertFalse(r.advance(9199).complete());assertTrue(r.advance(9200).complete());
        assertEquals(0,r.speedBonus(5000));
    }
    @Test void renewalIntegratesTheOldWindowBeforeExtendingIt() {
        var c=catalog();var state=CivicState.empty().policy("research",2000).startResearch("planning",5000,1000,c);
        var renewed=state.advanceResearch(2500).policy("research",5000).researchBonuses(c,2500);
        assertEquals(1750,renewed.research().completedMillis());
        assertEquals(2625,renewed.research().remainingMillis(2500));
        assertEquals(1,renewed.advanceResearch(5125).level("planning"));
    }
    @Test void researchRoundTripPreservesBoostScheduleAcrossRestartAndClockRollback() throws Exception {
        var c=catalog();var state=CivicState.empty().technology("planning",2).policy("research",2000).startResearch("planning",5000,1000,c);
        var yaml=new YamlConfiguration();yaml.set("civic",CivicCodec.encode(state));
        var loaded=new YamlConfiguration();loaded.loadFromString(yaml.saveToString());
        var restored=CivicCodec.decode(loaded.getConfigurationSection("civic"));
        assertEquals(state,restored);assertSame(restored.research(),restored.research().advance(900));
        assertEquals(1250,restored.research().advance(2000).completedMillis());
        assertEquals(3,restored.advanceResearch(5750).level("planning"));
    }
    @Test void oldSavesKeepTheirLevelsAndHaveNoPendingResearch() {
        var yaml=new YamlConfiguration();yaml.set("civic.technologies.planning",3);
        var state=CivicCodec.decode(yaml.getConfigurationSection("civic"));
        assertEquals(3,state.level("planning"));assertNull(state.research());
        assertEquals(7_200_000,catalog().researchMillis(catalog().technologies().get("planning"),3));
    }
    @Test void defaultPolicyMigrationIsOnceOnlyAndPreservesCustomPolicies() {
        var defaults=new YamlConfiguration();defaults.set("civic.policies",List.of(Map.of("id","research"),Map.of("id","farming"),Map.of("id","fishing"),Map.of("id","mining")));
        var old=new YamlConfiguration();old.setDefaults(defaults);
        old.set("civic.policies",List.of(Map.of("id","custom","cost",999),Map.of("id","research","research-speed-percent",10)));
        CivicCatalog.migrateResearchPolicies(old);
        assertEquals(5,old.getMapList("civic.policies").size());
        assertEquals(10,old.getMapList("civic.policies").get(1).get("research-speed-percent"));
        old.set("civic.policies",List.of(Map.of("id","custom")));
        CivicCatalog.migrateResearchPolicies(old);assertEquals(1,old.getMapList("civic.policies").size());
    }
    @Test void invalidTimesAndBonusesFailBeforeAnyMutation() {
        assertThrows(IllegalArgumentException.class,()->new Research("planning",1,0,0,0,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new Research("planning",1,1000,Double.NaN,0,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new Research.Boost(Double.POSITIVE_INFINITY,1000));
        var yaml=new YamlConfiguration();yaml.set("civic.research-base-seconds",0);
        assertThrows(IllegalArgumentException.class,()->CivicCatalog.load(yaml));
        yaml.set("civic.research-base-seconds",1800);yaml.set("civic.skill-xp-cap-percent",Double.NaN);
        assertThrows(IllegalArgumentException.class,()->CivicCatalog.load(yaml));
    }
}
