package com.example.town.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SkillPolicyTest {
    private final UUID mayor=UUID.randomUUID(),owner=UUID.randomUUID(),tenant=UUID.randomUUID(),visitor=UUID.randomUUID();
    private Town town() {
        var t=new Town("town","小镇",mayor,0);t.state(TownState.ACTIVE);
        t.plots().put("plot",new Plot("plot","住宅",owner,PlotKind.RESIDENTIAL,new PlotBounds("world",0,0,5,5),Set.of(),
                new PlotLease(tenant,2000,100,"coin",Set.of()),0,false,0,0));
        t.civic(CivicState.empty().policy("training",3000));return t;
    }
    private CivicCatalog catalog(CivicCatalog.Scope scope,double cap) {
        return new CivicCatalog(2,6,.7,.5,.05,Map.of(),Map.of("training",
                new CivicCatalog.PolicyDef("training","研习",100,3000,scope,"",0,0,0,0,0,.25,.4,.6)),1800_000,1,cap);
    }
    @Test void defaultMemberScopeExcludesOwnersTenantsAndVisitors() {
        var t=TownSnapshot.of(town());var c=catalog(CivicCatalog.Scope.MEMBERS,1);
        assertEquals(.25,CivicEffects.experience(c,t,mayor,1000,CivicEffects.Skill.FARMING));
        for(UUID actor:List.of(owner,tenant,visitor)) assertEquals(0,CivicEffects.experience(c,t,actor,1000,CivicEffects.Skill.FARMING));
    }
    @Test void residentScopeRecognizesAnActiveLeaseAndExpiresAtTheBoundary() {
        var t=TownSnapshot.of(town());var c=catalog(CivicCatalog.Scope.RESIDENTS,1);
        assertEquals(.4,CivicEffects.experience(c,t,owner,1000,CivicEffects.Skill.FISHING));
        assertEquals(.4,CivicEffects.experience(c,t,tenant,1999,CivicEffects.Skill.FISHING));
        assertEquals(0,CivicEffects.experience(c,t,tenant,2000,CivicEffects.Skill.FISHING));
        assertEquals(0,CivicEffects.experience(c,t,mayor,3000,CivicEffects.Skill.FISHING));
    }
    @Test void allScopeStillHonorsSkillCapsAndTownState() {
        var town=town();var c=catalog(CivicCatalog.Scope.ALL,.3);
        assertEquals(.3,CivicEffects.experience(c,TownSnapshot.of(town),visitor,1000,CivicEffects.Skill.MINING));
        town.state(TownState.PENDING);
        assertEquals(0,CivicEffects.experience(c,TownSnapshot.of(town),mayor,1000,CivicEffects.Skill.MINING));
    }
}
