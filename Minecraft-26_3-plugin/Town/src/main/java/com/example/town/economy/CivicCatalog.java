package com.example.town.economy;

import java.util.*;
import org.bukkit.configuration.file.FileConfiguration;

/** 经济平衡与受益范围集中配置；价格统一为分。 */
public record CivicCatalog(int baseSlots, int maxSlots, double discountCap, double harvestCap,
                           double marketFee, Map<String, Technology> technologies, Map<String, PolicyDef> policies) {
    public enum TechnologyEffect { LAND_CAPACITY, POLICY_SLOTS, MOVE_DISCOUNT }
    public enum Scope { MEMBERS, RESIDENTS, ALL }
    public record Technology(String id, String name, long baseCents, int maxLevel, TechnologyEffect effect, double value) {
        public long cost(int current) { return Math.multiplyExact(baseCents, (long)current + 1); }
    }
    public record PolicyDef(String id, String name, long cents, long durationMillis, Scope scope,
                            String potion, int amplifier, double harvest, double tradeDiscount, double moveDiscount) { }
    public CivicCatalog {
        technologies = Map.copyOf(technologies); policies = Map.copyOf(policies);
        if (baseSlots < 1 || maxSlots < baseSlots || maxSlots > 32 || discountCap < 0 || discountCap > 0.9
                || harvestCap < 0 || harvestCap > 1 || marketFee < 0 || marketFee > 0.5)
            throw new IllegalArgumentException("civic 配置无效");
    }
    public static CivicCatalog load(FileConfiguration config) {
        Map<String, Technology> technologies = new LinkedHashMap<>();
        for (Map<?, ?> raw : config.getMapList("civic.technologies")) {
            String id = text(raw, "id", "");
            Technology def = new Technology(id, text(raw,"name",id), Money.cents(text(raw,"cost","500")),
                    integer(raw,"max-level",5), TechnologyEffect.valueOf(text(raw,"effect","LAND_CAPACITY")),
                    decimal(raw,"value",16));
            if (!id.matches("[a-z0-9_-]+") || def.maxLevel() < 1 || def.maxLevel() > 100 || !Double.isFinite(def.value())
                    || def.value() < 0 || def.value() > 65536 || technologies.putIfAbsent(id,def) != null)
                throw new IllegalArgumentException("科技配置无效："+id);
        }
        Map<String, PolicyDef> policies = new LinkedHashMap<>();
        for (Map<?, ?> raw : config.getMapList("civic.policies")) {
            String id = text(raw,"id","");
            PolicyDef def = new PolicyDef(id,text(raw,"name",id),Money.cents(text(raw,"cost","200")),
                    Math.multiplyExact(Long.parseLong(text(raw,"duration-seconds","86400")),1000),
                    Scope.valueOf(text(raw,"scope","RESIDENTS")),text(raw,"potion",""),integer(raw,"amplifier",0),
                    decimal(raw,"harvest-percent",0)/100,decimal(raw,"trade-discount-percent",0)/100,
                    decimal(raw,"move-discount-percent",0)/100);
            if (!id.matches("[a-z0-9_-]+") || def.durationMillis()<1000 || def.durationMillis()>365L*86400_000
                    || def.amplifier()<0 || def.amplifier()>2 || !fraction(def.harvest()) || !fraction(def.tradeDiscount())
                    || !fraction(def.moveDiscount()) || policies.putIfAbsent(id,def)!=null)
                throw new IllegalArgumentException("政策配置无效："+id);
        }
        return new CivicCatalog(config.getInt("civic.policy-slots",2),config.getInt("civic.max-policy-slots",6),
                config.getDouble("civic.discount-cap-percent",70)/100,config.getDouble("civic.harvest-cap-percent",50)/100,
                config.getDouble("civic.market-fee-percent",5)/100,technologies,policies);
    }
    public int slots(CivicState state) {
        long slots = baseSlots;
        for (Technology tech:technologies.values()) if(tech.effect()==TechnologyEffect.POLICY_SLOTS)
            slots+= (long)(state.level(tech.id())*tech.value());
        return (int)Math.min(maxSlots,slots);
    }
    public int claimLimit(int base,CivicState state) {
        if(base==0) return 0;
        long limit=base;
        for(Technology tech:technologies.values()) if(tech.effect()==TechnologyEffect.LAND_CAPACITY)
            limit+=(long)(state.level(tech.id())*tech.value());
        return (int)Math.min(Integer.MAX_VALUE,limit);
    }
    public double moveDiscount(CivicState state,long now) {
        double sum=0;
        for(Technology tech:technologies.values()) if(tech.effect()==TechnologyEffect.MOVE_DISCOUNT)
            sum+=state.level(tech.id())*tech.value()/100;
        for(PolicyDef policy:active(state,now)) sum+=policy.moveDiscount();
        return Math.min(discountCap,sum);
    }
    public double tradeFee(CivicState state,long now) {
        double discount=active(state,now).stream().mapToDouble(PolicyDef::tradeDiscount).sum();
        return marketFee*(1-Math.min(discountCap,discount));
    }
    public List<PolicyDef> active(CivicState state,long now) {
        return state.policies().values().stream().filter(p->p.expiresAt()>now).map(p->policies.get(p.id()))
                .filter(Objects::nonNull).toList();
    }
    private static boolean fraction(double d) { return Double.isFinite(d) && d>=0 && d<=1; }
    private static String text(Map<?,?> m,String key,String fallback) { Object v=m.get(key);return v==null?fallback:v.toString(); }
    private static int integer(Map<?,?> m,String key,int fallback) { return Integer.parseInt(text(m,key,String.valueOf(fallback))); }
    private static double decimal(Map<?,?> m,String key,double fallback) { return Double.parseDouble(text(m,key,String.valueOf(fallback))); }
}
