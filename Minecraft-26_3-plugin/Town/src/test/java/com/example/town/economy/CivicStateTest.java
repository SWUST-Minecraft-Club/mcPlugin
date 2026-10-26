package com.example.town.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.store.CivicCodec;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CivicStateTest {
    @Test void treasuryContributionIdempotencyOverdraftAndRoundTrip() throws Exception {
        UUID actor=UUID.randomUUID();
        CivicState state=CivicState.empty().transact("a",actor,"coin",5000,"贡献",1,true);
        assertSame(state,state.transact("a",actor,"coin",5000,"贡献",1,true));
        assertEquals(5000,state.balance("coin"));assertEquals(5000,state.contributions().get(actor+"@coin"));
        CivicState before=state;
        assertThrows(IllegalArgumentException.class,()->before.transact("b",actor,"coin",-5001,"科技",2,false));
        state=state.transact("b",actor,"coin",-500,"科技",2,false).technology("planning",2).policy("builders",100);
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("civic",CivicCodec.encode(state));
        YamlConfiguration loaded=new YamlConfiguration();loaded.loadFromString(yaml.saveToString());
        assertEquals(state,CivicCodec.decode(loaded.getConfigurationSection("civic")));
        assertThrows(ArithmeticException.class,()->Money.cents("9999999999999999999999"));
        assertThrows(ArithmeticException.class,()->Money.cents("1.001"));
    }
    @Test void activePolicyExpirationAndTechnologyCapsAffectRealLimits() {
        var catalog=new CivicCatalog(2,4,.7,.5,.05,Map.of(
                "planning",new CivicCatalog.Technology("planning","规划",100,5,CivicCatalog.TechnologyEffect.LAND_CAPACITY,16),
                "slots",new CivicCatalog.Technology("slots","槽位",100,5,CivicCatalog.TechnologyEffect.POLICY_SLOTS,1),
                "logistics",new CivicCatalog.Technology("logistics","物流",100,5,CivicCatalog.TechnologyEffect.MOVE_DISCOUNT,30)),Map.of(
                "trade",new CivicCatalog.PolicyDef("trade","商业",100,10_000,CivicCatalog.Scope.ALL,"",0,0,.2,.2)));
        CivicState state=CivicState.empty().technology("planning",2).technology("slots",5).technology("logistics",5).policy("trade",1000);
        assertEquals(288,catalog.claimLimit(256,state));assertEquals(0,catalog.claimLimit(0,state));
        assertEquals(4,catalog.slots(state));assertEquals(.7,catalog.moveDiscount(state,0),.00001);
        assertEquals(.04,catalog.tradeFee(state,999),.00001);assertEquals(.05,catalog.tradeFee(state,1000),.00001);
        assertEquals(0,catalog.active(state,1000).size());
    }
}
