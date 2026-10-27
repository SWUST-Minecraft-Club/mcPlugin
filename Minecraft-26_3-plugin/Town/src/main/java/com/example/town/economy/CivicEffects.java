package com.example.town.economy;

import com.example.town.model.TownSnapshot;
import java.util.UUID;

public final class CivicEffects {
    public enum Skill { FARMING, FISHING, MINING }
    private CivicEffects() { }
    public static boolean benefits(CivicCatalog.Scope scope,TownSnapshot town,UUID actor,long now) {
        if(scope==CivicCatalog.Scope.ALL) return true;
        if(town.members().containsKey(actor)) return true;
        return scope==CivicCatalog.Scope.RESIDENTS && town.plots().values().stream().anyMatch(p->p.owner().equals(actor)
                || (p.lease()!=null && p.lease().active(now) && p.lease().tenant().equals(actor)));
    }
    public static double moveDiscount(CivicCatalog catalog,TownSnapshot town,UUID actor,long now) {
        double technology=catalog.moveDiscount(new CivicState(town.civic().balances(),town.civic().contributions(),town.civic().technologies(),java.util.Map.of(),town.civic().applied(),town.civic().ledger()),now);
        double policy=catalog.active(town.civic(),now).stream().filter(p->benefits(p.scope(),town,actor,now)).mapToDouble(CivicCatalog.PolicyDef::moveDiscount).sum();
        return Math.min(catalog.discountCap(),technology+policy);
    }
    public static double harvest(CivicCatalog catalog,TownSnapshot town,UUID actor,long now) {
        return Math.min(catalog.harvestCap(),catalog.active(town.civic(),now).stream()
                .filter(p->benefits(p.scope(),town,actor,now)).mapToDouble(CivicCatalog.PolicyDef::harvest).sum());
    }
    /** Caller must first resolve the town containing the validated gameplay action. */
    public static double experience(CivicCatalog catalog,TownSnapshot town,UUID actor,long now,Skill skill) {
        if (town.state()!=com.example.town.model.TownState.ACTIVE) return 0;
        return Math.min(catalog.skillBonusCap(),catalog.active(town.civic(),now).stream()
                .filter(p->benefits(p.scope(),town,actor,now)).mapToDouble(p->switch(skill) {
                    case FARMING->p.farmingXp();case FISHING->p.fishingXp();case MINING->p.miningXp();
                }).sum());
    }
}
