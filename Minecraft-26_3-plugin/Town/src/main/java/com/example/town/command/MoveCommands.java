package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.economy.Money;
import com.example.town.relocation.RelocationService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

public final class MoveCommands {
    private MoveCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> create(TownPlugin plugin) {
        return Commands.literal("move").executes(ctx->{if(ctx.getSource().getSender() instanceof Player p) execute(plugin,p,"status");return 1;})
                .then(Commands.argument("arguments",StringArgumentType.greedyString()).executes(ctx->{
                    if(ctx.getSource().getSender() instanceof Player p) execute(plugin,p,StringArgumentType.getString(ctx,"arguments"));return 1;
                }));
    }
    public static void execute(TownPlugin plugin,Player player,String line) {
        String[] args=line.trim().split("\\s+");var service=plugin.relocation();
        try {
            switch(args[0]) {
                case "begin","hall"->{
                    if(args.length!=4 && args.length!=5) throw new IllegalArgumentException("用法：begin X Y Z [地块ID]；政厅迁址使用 hall X Y Z");
                    service.begin(player,Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]),args.length==5?args[4]:"",args[0].equals("hall"));
                }
                case "here"->{var at=player.getLocation();service.begin(player,at.getBlockX(),at.getBlockY(),at.getBlockZ(),args.length>1?args[1]:"",false);}
                case "shift"->{if(args.length!=4) throw new IllegalArgumentException("shift X Y Z");service.adjust(player,Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]),false);}
                case "rotate"->service.adjust(player,0,0,0,true);
                case "ground"->service.ground(player);
                case "cancel"->service.cancel(player);
                case "status"->service.status(player);
                case "confirm"->{if(args.length!=3) throw new IllegalArgumentException("请使用当前方案中的确认施工按钮。");service.confirm(player,args[1],Long.parseLong(args[2]));}
                case "accept"->{if(args.length!=2) throw new IllegalArgumentException("accept 工程ID");service.accept(player,args[1]);}
                case "undo"->{if(args.length!=2) throw new IllegalArgumentException("undo 工程ID");service.undo(player,args[1]);}
                case "retry"->{if(args.length!=2) throw new IllegalArgumentException("retry 工程ID");service.retry(player,args[1]);}
                case "approve"->{if(args.length!=2) throw new IllegalArgumentException("approve 方案ID");service.approve(player,args[1]);}
                case "inspect"->{
                    if(args.length!=2) throw new IllegalArgumentException("inspect 方案ID");var d=service.inspect(args[1]);
                    if(!d.required().contains(player.getUniqueId()) && !d.actor().equals(player.getUniqueId())) throw new IllegalArgumentException("无权查看此方案");
                    player.sendMessage(Component.text("源区 "+RelocationService.box(d.move().source())+" → "+RelocationService.box(d.move().target())+" · "+d.move().quarterTurns()*90+"° · 关联地块 "+d.plot()+" · 点击同意")
                            .clickEvent(ClickEvent.runCommand("/town move approve "+d.token())));
                }
                case "approvals"->{for(var d:service.approvals(player.getUniqueId())) player.sendMessage(Component.text("[迁移审批] "+d.token()).clickEvent(ClickEvent.runCommand("/town move inspect "+d.token())));}
                case "jobs"->{for(var j:service.jobs(player.getUniqueId())) player.sendMessage(Component.text(j.id()+" · "+j.stage()+" · "+Money.format(j.cents())+" "+j.currency()+" · "+j.error()));}
                default->throw new IllegalArgumentException("用法：/town select 后选三维源区；/town move begin X Y Z [地块ID]；shift / rotate / ground / status / cancel / jobs / approvals");
            }
        }catch(IllegalArgumentException|ArithmeticException error) {player.sendMessage(Component.text("[小镇] "+service.describe(RelocationService.reason(error))));}
    }
}
