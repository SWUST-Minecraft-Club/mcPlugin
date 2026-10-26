package com.example.town.command;

import com.example.town.TownPlugin;
import com.example.town.economy.Money;
import com.example.town.market.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MarketCommands {
    private MarketCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> create(TownPlugin plugin) {
        return Commands.literal("market").executes(c->{if(c.getSource().getSender() instanceof Player p) execute(plugin,p,"list");return 1;})
                .then(Commands.argument("args",StringArgumentType.greedyString()).executes(c->{if(c.getSource().getSender() instanceof Player p) execute(plugin,p,StringArgumentType.getString(c,"args"));return 1;}));
    }
    public static void execute(TownPlugin plugin,Player p,String input) {
        String[] a=input.trim().split("\\s+");var s=plugin.market();
        try {
            switch(a[0]) {
                case "stock","order"->{
                    if(a.length!=3) throw new IllegalArgumentException("手持商品样品：/town market stock|order 数量 单价");
                    int quantity=Integer.parseInt(a[1]);long unit=Money.cents(a[2]);
                    if(a[0].equals("stock")) s.stock(p,quantity,unit);else s.order(p,quantity,unit);
                }
                case "buy","supply"->{
                    if(a.length!=5) throw new IllegalArgumentException("用法：buy|supply ID 数量 报价版本 单价分；请从商品详情确认。");
                    int quantity=Integer.parseInt(a[2]);long revision=Long.parseLong(a[3]),unit=Long.parseLong(a[4]);
                    if(a[0].equals("buy")) s.buy(p,a[1],quantity,revision,unit);else s.supply(p,a[1],quantity,revision,unit);
                }
                case "close"->{if(a.length!=3) throw new IllegalArgumentException("close ID 版本");s.close(p,a[1],Long.parseLong(a[2]));}
                case "collect"->{if(a.length!=3) throw new IllegalArgumentException("collect 收件ID 数量");s.collect(p,a[1],Integer.parseInt(a[2]));}
                case "resume"->s.resume(p);
                case "resolve"->{
                    if(a.length!=3 || !p.hasPermission("town.admin") || (!a[2].equals("before") && !a[2].equals("after"))) throw new IllegalArgumentException("管理员核对后：resolve 在线玩家 before|after");
                    Player target=Bukkit.getPlayerExact(a[1]);if(target==null) throw new IllegalArgumentException("玩家不在线。");s.resolve(p,target,a[2].equals("after"));
                }
                case "mail"->{for(var m:s.mail(p.getUniqueId())) p.sendPlainMessage(m.id()+" · "+InventoryImage.decode(m.item()).getType().translationKey()+" × "+m.quantity()+" · /town market collect "+m.id()+" 数量");}
                default->{
                    for(var l:s.listings()) p.sendPlainMessage(l.id()+" · "+(l.kind()==MarketBook.Kind.STOCK?"现货":"收购")+" · "+InventoryImage.decode(l.item()).getType()+" × "+l.remaining()+" · "+Money.format(l.unitCents())+" "+l.currency()+" · 版本 "+l.revision());
                    p.sendPlainMessage("/town market stock|order 数量 单价；buy|supply ID 数量 版本 单价分；mail；close ID 版本");
                }
            }
        }catch(IllegalArgumentException|ArithmeticException error) {p.sendPlainMessage("[小镇] "+(error.getMessage()==null?"参数无效。":error.getMessage()));}
    }
}
