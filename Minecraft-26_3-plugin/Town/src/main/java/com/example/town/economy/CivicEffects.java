package com.example.town.economy;

import com.example.town.model.TownSnapshot;
import java.util.UUID;

public final class CivicEffects {
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
}
