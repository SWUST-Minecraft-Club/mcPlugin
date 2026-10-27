package com.example.town.integration;

import com.example.town.TownPlugin;
import com.example.town.economy.*;
import com.example.town.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable servers only: creates a test town, pays through the actual journal, and restarts. */
public final class ResearchIntegration extends JavaPlugin {
    private static final UUID ACTOR=UUID.fromString("940d6fd7-e1d7-4ad7-92dc-95c36c30a2c1");
    private static final String ID="research-fixture";
    private TownPlugin town;
    private final List<String> checks=new ArrayList<>();
    @Override public void onEnable() {
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->Bukkit.getAsyncScheduler().runNow(this,ignored->run()),80);
    }
    private void check(boolean passed,String label) {
        if(!passed) throw new AssertionError(label);
        checks.add(label);getLogger().info("PASS "+label);
    }
    private CivicState state() {return town.service().townOf(ACTOR).raw().civic();}
    private Payment buy(String kind,String id) throws Exception {
        var c=town.civic().catalog();int level=state().level(id);
        long price=kind.equals("technology")?c.technologies().get(id).cost(level):c.policies().get(id).cents();
        var p=Payment.create(kind,ACTOR,"ResearchTester","coin",0,null,"",0,
                Map.of("town",ID,"id",id,"price",String.valueOf(price),"level",String.valueOf(level),
                        "research-millis",kind.equals("technology")?String.valueOf(c.researchMillis(c.technologies().get(id),level)):"0"));
        var done=new CompletableFuture<Payment>();town.civic().payments().submit(p,done::complete);
        return done.get(15,TimeUnit.SECONDS);
    }
    private void run() {
        String phase=Files.exists(Path.of("research-first.passed.txt"))?"restart":"first";
        try {
            town=(TownPlugin)Bukkit.getPluginManager().getPlugin("Town");
            check(town!=null&&town.isEnabled(),"Town enabled on Folia");
            boolean linked=Bukkit.getPluginManager().isPluginEnabled("RptStardew");
            check(town.stardewAvailable()==linked,"optional Stardew linkage state");
            if(phase.equals("first")) first(linked);else restart();
            Files.writeString(Path.of("research-"+phase+".passed.txt"),String.join("\n",checks));
        }catch(Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE,"Research integration failed",failure);
            try {Files.writeString(Path.of("research-"+phase+".failed.txt"),failure.toString());}catch(Exception ignored) { }
        }
    }
    private void first(boolean linked) throws Exception {
        try(var guard=town.registry().guard()) {
            var t=new Town(ID,"科研测试镇",ACTOR,System.currentTimeMillis());t.state(TownState.ACTIVE);
            t.claims().add(new ChunkKey(Bukkit.getWorlds().getFirst().getName(),0,0));
            t.civic(CivicState.empty().transact("fixture-fund",ACTOR,"coin",1_000_000,"测试镇库",System.currentTimeMillis(),true).technology("archives",4));
            town.registry().register(t);town.store().saveChecked(town.registry());
        }
        long start=state().balance("coin");
        var purchase=buy("technology","planning");
        check(purchase.stage()==Payment.Stage.DONE,"research paid through durable journal");
        check(state().level("planning")==0&&state().research()!=null,"research does not grant technology early");
        long paid=state().balance("coin");
        check(paid==start-town.civic().catalog().technologies().get("planning").cost(0),"treasury charged exactly once");
        var busy=buy("technology","logistics");
        check(busy.stage()==Payment.Stage.REFUNDED&&state().balance("coin")==paid,"second research rejected without treasury debit");
        town.civic().payments().kick(purchase.id());
        check(state().balance("coin")==paid,"journal replay does not double charge");
        check(buy("policy","research").stage()==Payment.Stage.DONE,"research policy purchased");
        check(state().research().speedBonus(System.currentTimeMillis())==.25,"research policy accelerates active research");
        if(linked) {
            for(String id:List.of("farming","fishing","mining")) check(buy("policy",id).stage()==Payment.Stage.DONE,"purchase "+id+" skill policy");
            StardewChecks.run(town,ACTOR,this::check);
        }else {
            long before=state().balance("coin");
            check(buy("policy","farming").stage()==Payment.Stage.REFUNDED&&state().balance("coin")==before,
                    "missing Stardew rejects skill policy without treasury debit");
        }
        Thread.sleep(4200);
        check(state().research()!=null&&state().research().speedBonus(System.currentTimeMillis())==0,"expired research policy restores normal speed");
        if(linked) StardewChecks.expired(ACTOR,this::check);
        Files.writeString(Path.of("research-balance.txt"),String.valueOf(state().balance("coin")));
        check(state().research()!=null,"pending research saved for restart");
        town.store().saveChecked(town.registry());
    }
    private void restart() throws Exception {
        long until=System.currentTimeMillis()+20_000;
        while(state().research()!=null&&System.currentTimeMillis()<until) Thread.sleep(200);
        check(state().research()==null&&state().level("planning")==1,"offline research completes after restart");
        check(state().balance("coin")==Long.parseLong(Files.readString(Path.of("research-balance.txt"))),"restart and completion preserve treasury balance");
        check(state().level("archives")==4,"previous technology levels preserved");
        town.civic().completeResearch(System.currentTimeMillis());
        check(state().level("planning")==1,"completion replay cannot grant another level");
    }
}
