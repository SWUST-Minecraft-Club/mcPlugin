package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.economy.*;
import com.example.town.model.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.*;
import java.util.*;
import org.bukkit.entity.Player;

public final class CivicCommands {
    private CivicCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> create(TownPlugin plugin) {
        return Commands.literal("civic").executes(ctx->{if(ctx.getSource().getSender() instanceof Player p) execute(plugin,p,"info");return 1;})
                .then(Commands.argument("args",StringArgumentType.greedyString()).executes(ctx->{
                    if(ctx.getSource().getSender() instanceof Player p) execute(plugin,p,StringArgumentType.getString(ctx,"args"));return 1;
                }));
    }
    public static void execute(TownPlugin plugin,Player player,String input) {
        String[] args=input.trim().split("\\s+");
        var feedback=(java.util.function.Consumer<com.example.town.service.TownService.Result>)(r->plugin.service().deliver(player,r));
        try {
            switch(args[0]) {
                case "donate","withdraw" -> {
                    if(args.length!=2) throw new IllegalArgumentException();
                    long cents=Money.cents(args[1]);
                    if(args[0].equals("donate")) plugin.civic().donate(player,cents,feedback);else plugin.civic().withdraw(player,cents,feedback);
                }
                case "technology","policy" -> {
                    if(args.length!=2) throw new IllegalArgumentException();
                    plugin.civic().purchase(player,args[1],args[0].equals("technology"),feedback);
                }
                case "payments" -> {
                    plugin.civic().payments().all().stream().filter(p->p.payer().equals(player.getUniqueId())
                                    || player.getUniqueId().equals(p.beneficiary())).sorted(Comparator.comparingLong(Payment::createdAt).reversed()).limit(15)
                            .forEach(p->player.sendPlainMessage(p.id()+" · "+p.kind()+" · "+Money.format(p.cents())+" "+p.currency()+" · "+p.stage()));
                }
                case "ledger" -> {
                    var town=town(plugin,player);
                    List<CivicState.Entry> ledger=town.civic().ledger();
                    for(int i=ledger.size()-1;i>=Math.max(0,ledger.size()-15);i--) {
                        var e=ledger.get(i);player.sendPlainMessage(e.reason()+" · "+Money.format(e.delta())+" "+e.currency()+" · "+e.actor());
                    }
                }
                case "contributions" -> town(plugin,player).civic().contributions().entrySet().stream()
                        .sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(20)
                        .forEach(e->player.sendPlainMessage(e.getKey()+" · "+Money.format(e.getValue())));
                default -> {
                    TownSnapshot town=town(plugin,player);var catalog=plugin.civic().catalog();
                    town.civic().balances().forEach((code,cents)->player.sendPlainMessage("镇库 · "+Money.format(cents)+" "+code));
                    player.sendPlainMessage("政策槽位 "+catalog.active(town.civic(),System.currentTimeMillis()).size()+" / "+catalog.slots(town.civic()));
                    catalog.technologies().values().forEach(t->player.sendPlainMessage(t.id()+" · "+t.name()+" Lv."+town.civic().level(t.id())+" / "+t.maxLevel()+" · "+Money.format(t.cost(town.civic().level(t.id())))));
                    catalog.policies().values().forEach(p->player.sendPlainMessage(p.id()+" · "+p.name()+" · "+Money.format(p.cents())+" · "+p.durationMillis()/3600000+" 小时"));
                    player.sendPlainMessage("/town civic donate|withdraw <金额>；technology|policy <ID>；ledger；contributions；payments");
                }
            }
        }catch(IllegalArgumentException|ArithmeticException error) {
            String key=error.getMessage();
            if(key!=null && key.equals("not-in-town")) plugin.messages().send(player,key);
            else player.sendPlainMessage("用法：/town civic [donate|withdraw 金额 / technology|policy ID / ledger / contributions / payments]");
        }
    }
    private static TownSnapshot town(TownPlugin plugin,Player player) {
        var view=plugin.service().townOf(player.getUniqueId());
        if(view==null) throw new IllegalArgumentException("not-in-town");return view.raw();
    }
}
