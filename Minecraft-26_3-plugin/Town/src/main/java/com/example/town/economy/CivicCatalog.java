package com.example.town.economy;

import java.util.*;
import org.bukkit.configuration.file.FileConfiguration;

/** 经济平衡与受益范围集中配置；价格统一为分。 */
public record CivicCatalog(int baseSlots, int maxSlots, double discountCap, double harvestCap,
                           double marketFee, Map<String, Technology> technologies, Map<String, PolicyDef> policies,
                           long researchBaseMillis, double researchSpeedCap, double skillBonusCap) {
    public enum TechnologyEffect { LAND_CAPACITY, POLICY_SLOTS, MOVE_DISCOUNT }
    public enum Scope { MEMBERS, RESIDENTS, ALL }
    public record Technology(String id, String name, long baseCents, int maxLevel, TechnologyEffect effect, double value, long researchMillis) {
        public Technology(String id, String name, long baseCents, int maxLevel, TechnologyEffect effect, double value) {
            this(id, name, baseCents, maxLevel, effect, value, -1);
        }
        public long cost(int current) { return Math.multiplyExact(baseCents, (long)current + 1); }
    }
    public record PolicyDef(String id, String name, long cents, long durationMillis, Scope scope,
                            String potion, int amplifier, double harvest, double tradeDiscount, double moveDiscount,
                            double researchSpeed, double farmingXp, double fishingXp, double miningXp) {
        public PolicyDef(String id, String name, long cents, long durationMillis, Scope scope, String potion,
                         int amplifier, double harvest, double tradeDiscount, double moveDiscount) {
            this(id, name, cents, durationMillis, scope, potion, amplifier, harvest, tradeDiscount, moveDiscount, 0, 0, 0, 0);
        }
        public boolean needsStardew() { return farmingXp > 0 || fishingXp > 0 || miningXp > 0; }
        public String summary() {
            List<String> effects=new ArrayList<>();
            if(!potion.isBlank()) effects.add("药水 "+potion+" "+(amplifier+1));
            if(harvest>0) effects.add("收获 +"+Math.round(harvest*100)+"%");
            if(tradeDiscount>0) effects.add("手续费 −"+Math.round(tradeDiscount*100)+"%");
            if(moveDiscount>0) effects.add("迁移费 −"+Math.round(moveDiscount*100)+"%");
            if(researchSpeed>0) effects.add("科研速度 +"+Math.round(researchSpeed*100)+"%");
            if(farmingXp>0) effects.add("耕种经验 +"+Math.round(farmingXp*100)+"%");
            if(fishingXp>0) effects.add("钓鱼经验 +"+Math.round(fishingXp*100)+"%");
            if(miningXp>0) effects.add("采矿经验 +"+Math.round(miningXp*100)+"%");
            return String.join(" · ",effects);
        }
    }
    public CivicCatalog(int baseSlots, int maxSlots, double discountCap, double harvestCap, double marketFee,
                        Map<String, Technology> technologies, Map<String, PolicyDef> policies) {
        this(baseSlots, maxSlots, discountCap, harvestCap, marketFee, technologies, policies, 1_800_000, 1, 1);
    }
    public CivicCatalog {
        technologies = Map.copyOf(technologies); policies = Map.copyOf(policies);
        if (baseSlots < 1 || maxSlots < baseSlots || maxSlots > 32 || discountCap < 0 || discountCap > 0.9
                || !Double.isFinite(discountCap) || !Double.isFinite(harvestCap) || !Double.isFinite(marketFee)
                || harvestCap < 0 || harvestCap > 1 || marketFee < 0 || marketFee > 0.5
                || researchBaseMillis < 1000 || researchBaseMillis > 365L*86400_000 || !fraction(researchSpeedCap) || !fraction(skillBonusCap))
            throw new IllegalArgumentException("civic 配置无效");
    }
    public static CivicCatalog load(FileConfiguration config) {
        Map<String, Technology> technologies = new LinkedHashMap<>();
        for (Map<?, ?> raw : config.getMapList("civic.technologies")) {
            String id = text(raw, "id", "");
            Technology def = new Technology(id, text(raw,"name",id), Money.cents(text(raw,"cost","500")),
                    integer(raw,"max-level",5), TechnologyEffect.valueOf(text(raw,"effect","LAND_CAPACITY")),
                    decimal(raw,"value",16), raw.containsKey("research-seconds") ? Math.multiplyExact(Long.parseLong(text(raw,"research-seconds","0")),1000) : -1);
            if (!id.matches("[a-z0-9_-]+") || def.maxLevel() < 1 || def.maxLevel() > 100 || !Double.isFinite(def.value())
                    || def.value() < 0 || def.value() > 65536 || (def.researchMillis()!=-1 && (def.researchMillis()<1000 || def.researchMillis()>365L*86400_000))
                    || technologies.putIfAbsent(id,def) != null)
                throw new IllegalArgumentException("科技配置无效："+id);
        }
        Map<String, PolicyDef> policies = new LinkedHashMap<>();
        for (Map<?, ?> raw : config.getMapList("civic.policies")) {
            String id = text(raw,"id","");
            PolicyDef def = new PolicyDef(id,text(raw,"name",id),Money.cents(text(raw,"cost","200")),
                    Math.multiplyExact(Long.parseLong(text(raw,"duration-seconds","86400")),1000),
                    Scope.valueOf(text(raw,"scope","RESIDENTS")),text(raw,"potion",""),integer(raw,"amplifier",0),
                    decimal(raw,"harvest-percent",0)/100,decimal(raw,"trade-discount-percent",0)/100,
                    decimal(raw,"move-discount-percent",0)/100,decimal(raw,"research-speed-percent",0)/100,
                    decimal(raw,"farming-xp-percent",0)/100,decimal(raw,"fishing-xp-percent",0)/100,decimal(raw,"mining-xp-percent",0)/100);
            if (!id.matches("[a-z0-9_-]+") || def.durationMillis()<1000 || def.durationMillis()>365L*86400_000
                    || def.amplifier()<0 || def.amplifier()>2 || !fraction(def.harvest()) || !fraction(def.tradeDiscount())
                    || !fraction(def.moveDiscount()) || !fraction(def.researchSpeed()) || !fraction(def.farmingXp())
                    || !fraction(def.fishingXp()) || !fraction(def.miningXp()) || policies.putIfAbsent(id,def)!=null)
                throw new IllegalArgumentException("政策配置无效："+id);
        }
        return new CivicCatalog(config.getInt("civic.policy-slots",2),config.getInt("civic.max-policy-slots",6),
                config.getDouble("civic.discount-cap-percent",70)/100,config.getDouble("civic.harvest-cap-percent",50)/100,
                config.getDouble("civic.market-fee-percent",5)/100,technologies,policies,
                Math.multiplyExact(config.getLong("civic.research-base-seconds",1800),1000),
                config.getDouble("civic.research-speed-cap-percent",100)/100,config.getDouble("civic.skill-xp-cap-percent",100)/100);
    }
    public long researchMillis(Technology technology, int currentLevel) {
        return Math.multiplyExact(technology.researchMillis() < 0 ? researchBaseMillis : technology.researchMillis(), (long)currentLevel + 1);
    }
    public String researchStatus(CivicState state,long now) {
        Research r=state.research();
        if(r==null) return "研究空闲 · 每镇同时研究一项";
        String name=technologies.containsKey(r.technology())?technologies.get(r.technology()).name():r.technology();
        return name+" Lv."+r.targetLevel()+" · "+r.percent(now)+"% · 剩余 "+researchTime(r.remainingMillis(now))
                +" · 科研速度 +"+Math.round(r.speedBonus(now)*100)+"%";
    }
    public static String researchTime(long millis) {
        long seconds=(long)Math.ceil(millis/1000d);
        if(seconds<60) return seconds+"秒";
        long minutes=(long)Math.ceil(seconds/60d);
        return minutes<60?minutes+"分":minutes/60+"时"+(minutes%60)+"分";
    }
    public List<Research.Boost> researchBoosts(CivicState state, long now) {
        return active(state,now).stream().filter(p -> p.researchSpeed()>0)
                .map(p -> new Research.Boost(p.researchSpeed(),state.policies().get(p.id()).expiresAt())).toList();
    }
    /** Append the four new defaults once; preserve existing and deliberately removed custom entries. */
    public static void migrateResearchPolicies(FileConfiguration config) {
        if (config.getInt("civic.research-catalog-version",0) >= 1) return;
        List<Map<?,?>> entries = new ArrayList<>(config.getMapList("civic.policies"));
        Set<String> ids = new HashSet<>(); entries.forEach(e -> ids.add(text(e,"id","")));
        for (Map<?,?> entry : config.getDefaults().getMapList("civic.policies")) {
            String id = text(entry,"id","");
            if (Set.of("research","farming","fishing","mining").contains(id) && ids.add(id)) entries.add(entry);
        }
        config.set("civic.policies",entries); config.set("civic.research-catalog-version",1);
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
