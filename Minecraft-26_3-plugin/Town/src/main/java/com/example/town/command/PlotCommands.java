package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.model.*;
import com.example.town.util.Threads;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 地块命令通道；图形页面调用同一服务，不绕过审批和 revision 校验。 */
public final class PlotCommands {
    private PlotCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> create(TownPlugin plugin) {
        return Commands.literal("plot")
                .executes(ctx->{if(ctx.getSource().getSender() instanceof Player player) execute(plugin,player,"help");return 1;})
                .then(Commands.argument("args",StringArgumentType.greedyString()).executes(ctx->{
                    if(ctx.getSource().getSender() instanceof Player player) execute(plugin,player,StringArgumentType.getString(ctx,"args"));
                    return 1;
                }));
    }

    public static void execute(TownPlugin plugin,Player player,String input) {
        String[] args=input.trim().split("\\s+");
        UUID actor=player.getUniqueId();
        try {
            switch(args[0]) {
                case "approvals" -> {
                    var proposals=plugin.plots().awaiting(actor);
                    if(proposals.isEmpty()) plugin.messages().send(player,"plot-approvals-empty");
                    for(var proposal:proposals) player.sendMessage(plugin.messages().render("plot-consent-request",
                                    Placeholder.unparsed("name",proposal.proposer().toString()),
                                    Placeholder.unparsed("old",describe(proposal.previous())),Placeholder.unparsed("next",describe(proposal.next())))
                            .clickEvent(ClickEvent.runCommand("/town plot approve "+proposal.token())));
                }
                case "list" -> {
                    var view=plugin.service().townAt(ChunkKey.of(player.getLocation()));
                    if(view==null) view=plugin.service().townOf(actor);
                    if(view==null) throw new IllegalArgumentException("not-in-town");
                    for(Plot plot:view.raw().plots().values()) info(plugin,player,plot);
                    if(view.raw().plots().isEmpty()) plugin.messages().send(player,"plot-list-empty");
                }
                case "create" -> {
                    if(args.length!=3 && args.length!=4 && args.length!=7 && args.length!=8) throw new IllegalArgumentException("plot-command-invalid");
                    PlotKind kind=PlotKind.valueOf(args[1].toUpperCase(java.util.Locale.ROOT));
                    boolean selected=args.length<7;
                    PlotBounds bounds=selected?plugin.selections().box(actor).footprint():bounds(player,args,3);
                    UUID owner=args.length==8?playerId(args[7]):args.length==4?playerId(args[3]):actor;
                    Plot plot=plugin.plots().create(actor,args[2],kind,bounds,owner);
                    info(plugin,player,plot);
                }
                case "edit" -> {
                    if(args.length!=2 && args.length!=6) throw new IllegalArgumentException("plot-command-invalid");
                    var view=find(plugin,args[1]);
                    PlotBounds next=args.length==2?plugin.selections().box(actor).footprint():bounds(player,args,2);
                    var proposal=plugin.plots().propose(actor,view.id(),args[1],next,System.currentTimeMillis());
                    plugin.selections().previewEdit(player,proposal);
                    plugin.messages().send(player,"plot-edit-preview",
                            Placeholder.unparsed("token",proposal.token()),
                            Placeholder.unparsed("area",String.valueOf(proposal.next().area())),
                            Placeholder.unparsed("count",String.valueOf(proposal.awaiting().size())));
                    for(UUID approver:proposal.awaiting()) {
                        Player target=Bukkit.getPlayer(approver);
                        if(target!=null) Threads.forPlayer(plugin,target,()->target.sendMessage(
                                plugin.messages().render("plot-consent-request",Placeholder.unparsed("name",player.getName()),
                                                Placeholder.unparsed("old",describe(proposal.previous())),Placeholder.unparsed("next",describe(proposal.next())))
                                        .clickEvent(ClickEvent.runCommand("/town plot approve "+proposal.token()))));
                    }
                }
                case "confirm" -> {
                    if(args.length!=2) throw new IllegalArgumentException("plot-command-invalid");
                    info(plugin,player,plugin.plots().confirm(actor,args[1],System.currentTimeMillis()));
                    plugin.selections().clear(actor);
                    plugin.messages().send(player,"plot-edited");
                }
                case "approve" -> {
                    if(args.length!=2) throw new IllegalArgumentException("plot-command-invalid");
                    var proposal=plugin.plots().approve(actor,args[1],System.currentTimeMillis());
                    plugin.messages().send(player,"plot-consent-recorded");
                    Player proposer=Bukkit.getPlayer(proposal.proposer());
                    if(proposer!=null) Threads.forPlayer(plugin,proposer,()->proposer.sendMessage(
                            plugin.messages().render("plot-consent-ready",Placeholder.unparsed("count",String.valueOf(proposal.awaiting().size())))
                                    .clickEvent(ClickEvent.runCommand("/town plot confirm "+proposal.token()))));
                }
                case "trust","untrust" -> {
                    if(args.length!=3) throw new IllegalArgumentException("plot-command-invalid");
                    var view=find(plugin,args[1]);
                    info(plugin,player,plugin.plots().trust(actor,view.id(),args[1],playerId(args[2]),args[0].equals("trust"),System.currentTimeMillis()));
                    plugin.messages().send(player,"plot-trust-updated");
                }
                case "offer" -> {
                    if(args.length!=4) throw new IllegalArgumentException("plot-command-invalid");
                    var view=find(plugin,args[1]);
                    long cents=new BigDecimal(args[2]).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
                    long duration=Math.multiplyExact(Long.parseLong(args[3]),86400_000L);
                    info(plugin,player,plugin.plots().offerRent(actor,view.id(),args[1],cents,duration,System.currentTimeMillis(),plugin.civic().currency()));
                    plugin.messages().send(player,"plot-rent-offered");
                }
                case "rent" -> {
                    if(args.length!=2) throw new IllegalArgumentException("plot-command-invalid");
                    plugin.civic().rent(player,args[1],r->plugin.service().deliver(player,r));
                }
                case "unoffer" -> {
                    if(args.length!=2) throw new IllegalArgumentException("plot-command-invalid");
                    info(plugin,player,plugin.plots().cancelOffer(actor,find(plugin,args[1]).id(),args[1]));
                }
                case "rename","kind","public","transfer" -> {
                    if(args.length!=3) throw new IllegalArgumentException("plot-command-invalid");
                    var view=find(plugin,args[1]);Plot result;
                    if(args[0].equals("transfer")) result=plugin.plots().transfer(actor,view.id(),args[1],playerId(args[2]));
                    else {
                        if(args[0].equals("public") && !args[2].equals("true") && !args[2].equals("false")) throw new IllegalArgumentException("plot-command-invalid");
                        result=plugin.plots().update(actor,view.id(),args[1],args[0].equals("rename")?args[2]:null,
                                args[0].equals("kind")?PlotKind.valueOf(args[2].toUpperCase(java.util.Locale.ROOT)):null,
                                args[0].equals("public")?Boolean.valueOf(args[2]):null);
                    }
                    info(plugin,player,result);plugin.messages().send(player,"plot-updated");
                }
                case "remove" -> {
                    if(args.length!=3) throw new IllegalArgumentException("plot-command-invalid");
                    var view=find(plugin,args[1]);
                    plugin.plots().remove(actor,view.id(),args[1],Long.parseLong(args[2]),System.currentTimeMillis());
                    plugin.messages().send(player,"plot-removed");
                }
                case "info" -> { if(args.length!=2) throw new IllegalArgumentException("plot-command-invalid");info(plugin,player,find(plugin,args[1]).raw().plots().get(args[1])); }
                default -> plugin.messages().send(player,"plot-help");
            }
        } catch(IllegalArgumentException|ArithmeticException e) {
            String code=e.getMessage();
            if(code==null || !(code.startsWith("plot-") || code.startsWith("selection-") || code.equals("action-state-changed") || code.equals("hall-not-set") || code.equals("not-in-town"))) code="plot-command-invalid";
            plugin.messages().send(player,code);
        }
    }

    private static TownSnapshotView find(TownPlugin plugin,String plotId) {
        return plugin.service().listTowns().stream().filter(view->view.raw().plots().containsKey(plotId)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("plot-not-found"));
    }
    private static PlotBounds bounds(Player player,String[] args,int start) {
        return PlotBounds.between(player.getWorld().getName(),Integer.parseInt(args[start]),Integer.parseInt(args[start+1]),Integer.parseInt(args[start+2]),Integer.parseInt(args[start+3]));
    }
    private static UUID playerId(String raw) {
        Player target=Bukkit.getPlayerExact(raw);
        return target!=null?target.getUniqueId():UUID.fromString(raw);
    }
    private static String describe(PlotBounds bounds) {
        return bounds.world()+" ["+bounds.minX()+","+bounds.minZ()+" → "+bounds.maxX()+","+bounds.maxZ()+"] "+bounds.area()+" 方块";
    }
    private static void info(TownPlugin plugin,Player player,Plot plot) {
        plugin.messages().send(player,"plot-info",Placeholder.unparsed("id",plot.id()),Placeholder.unparsed("name",plot.name()),
                Placeholder.unparsed("kind",plot.kind().display()),Placeholder.unparsed("area",String.valueOf(plot.bounds().area())),
                Placeholder.unparsed("owner",plot.owner().toString()),Placeholder.unparsed("bounds",plot.bounds().minX()+","+plot.bounds().minZ()+" → "+plot.bounds().maxX()+","+plot.bounds().maxZ()));
        if(plot.lease()!=null && plot.lease().active(System.currentTimeMillis())) player.sendPlainMessage("承租人 "+plot.lease().tenant()+" · 到期 "+java.time.Instant.ofEpochMilli(plot.lease().expiresAt()));
        if(plot.offeredDurationMillis()>0) player.sendPlainMessage("出租 "+com.example.town.economy.Money.format(plot.offeredRentCents())+" / "+plot.offeredDurationMillis()/86400_000+" 天 · /town plot rent "+plot.id());
        player.sendPlainMessage("地块版本 "+plot.revision()+" · 公开交互 "+plot.publicInteractionAt(System.currentTimeMillis()));
    }
}
