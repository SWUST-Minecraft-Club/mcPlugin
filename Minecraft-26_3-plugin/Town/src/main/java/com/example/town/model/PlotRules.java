package com.example.town.model;

import java.util.UUID;

/** 地块权限与几何规则：在注册表锁内使用 Town，或使用不可变快照进行读判定。 */
public final class PlotRules {
    public enum Action { BUILD, CONTAINER, INTERACT }
    private PlotRules() { }

    public static void validate(Town town, Plot candidate, int maxArea, int maxPlots) {
        if (town.state()!=TownState.ACTIVE || !town.isHallSet()) fail("hall-not-set");
        if (candidate.bounds().area()>maxArea) fail("plot-too-large");
        if (!town.plots().containsKey(candidate.id()) && town.plots().size()>=maxPlots) fail("plot-limit");
        if (!town.claims().containsAll(candidate.bounds().chunks(65536))) fail("plot-outside-town");
        for (Plot existing:town.plots().values()) {
            if (!existing.id().equals(candidate.id()) && existing.bounds().overlaps(candidate.bounds())) fail("plot-overlap");
        }
        if (candidate.kind()!=PlotKind.PUBLIC && candidate.bounds().contains(town.hallWorld(),town.hallX(),town.hallZ()))
            fail("plot-hall-conflict");
    }

    public static Plot at(TownSnapshot town,String world,int x,int z) {
        return town.plots().values().stream().filter(plot->plot.bounds().contains(world,x,z)).findFirst().orElse(null);
    }

    public static boolean permits(TownSnapshot town, UUID actor, String world, int x, int z, Action action, long now) {
        Plot plot=at(town,world,x,z);
        Role role=town.members().get(actor);
        if (plot!=null && plot.kind()==PlotKind.PUBLIC && role!=null && role.canManage()) return true;
        if (plot!=null) return plot.canUse(actor,now) || (action==Action.INTERACT && plot.publicInteractionAt(now));
        return role!=null && (action==Action.INTERACT || role.canManage());
    }

    private static void fail(String code) { throw new IllegalArgumentException(code); }
}
