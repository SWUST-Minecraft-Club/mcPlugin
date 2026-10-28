package com.example.yudreamui.plugin;

import com.example.wallet.api.*;
import com.google.gson.JsonObject;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Wallet purchases with sealed server quotes and a durable delivery queue. */
final class ShopService implements AutoCloseable {
    record Product(String id,String name,String category,Material material,int amount,BigDecimal price,String currency,String description,String permission) {}
    record Quote(Product product,int packs,BigDecimal total) {}
    static final class View {
        String stage="catalog",status="挑选一份旅途所需的补给。",balance="查询中";
        Product selected; Quote quote; BiConsumer<Player,YudreamUiApi.Action> handler;
    }
    private final YudreamUiPlugin plugin;
    private final ShopOrders orders;
    private final List<Product> products;
    private final String title;
    private final Set<UUID> processing=ConcurrentHashMap.newKeySet();
    ShopService(YudreamUiPlugin plugin) throws Exception {
        this.plugin=plugin;orders=new ShopOrders(plugin.getDataPath().resolve("shop-orders"));
        var yaml=YamlConfiguration.loadConfiguration(plugin.getDataPath().resolve("shop.yml").toFile());title=yaml.getString("title","林间补给站");
        var section=yaml.getConfigurationSection("products");var result=new ArrayList<Product>();
        if(section!=null)for(String id:section.getKeys(false)) {
            var p=section.getConfigurationSection(id);Material material=Material.matchMaterial(p.getString("material",""));
            var price=new BigDecimal(p.getString("price","0"));int amount=p.getInt("amount",1);String currency=p.getString("currency","coin");
            if(!id.matches("[a-z0-9_-]{1,40}")||material==null||!material.isItem()||material.isAir()||amount<1||amount>64||price.signum()<=0||price.precision()>15||!currency.matches("[A-Za-z0-9_-]{1,32}"))throw new IllegalArgumentException("Invalid shop product "+id);
            result.add(new Product(id,p.getString("name",id),p.getString("category","物资"),material,amount,price,currency,p.getString("description",""),p.getString("permission","")));
        }
        if(result.isEmpty()||result.size()>64)throw new IllegalArgumentException("Shop needs 1-64 products");products=List.copyOf(result);
    }
    void open(Player player) {
        if(!player.hasPermission("yudreamui.shop"))return;
        var view=new View();view.handler=(p,a)->action(p,view,a);render(player,view);balance(player,view);
        long paid=orders.pending(player.getUniqueId()).stream().filter(o->o.status()==ShopOrders.Status.PAID).count();
        if(paid>0){view.status="有 "+paid+" 笔已付款物资等待领取。";render(player,view);}
    }
    private void action(Player player,View view,YudreamUiApi.Action action) {
        if(!player.hasPermission("yudreamui.shop"))return;
        switch(action.name()) {
            case "server:back"->{view.stage="catalog";view.quote=null;render(player,view);}
            case "server:quote"->{
                int packs=action.form().get("quantity").getAsInt();float exact=action.form().get("quantity").getAsFloat();
                if(view.selected==null||packs!=exact||packs<1||packs>64)return;
                view.quote=new Quote(view.selected,packs,view.selected.price.multiply(BigDecimal.valueOf(packs)));view.stage="confirm";render(player,view);
            }
            case "server:pay"->{var quote=view.quote;view.quote=null;if(quote!=null)buy(player,view,quote);}
            case "server:recover"->recover(player,view);
            default->{
                if(action.name().startsWith("server:product-")) {
                    String id=action.name().substring("server:product-".length());
                    var product=products.stream().filter(p->p.id.equals(id)).findFirst().orElse(null);
                    if(product==null||!allowed(player,product))return;
                    view.selected=product;view.stage="quantity";render(player,view);
                }
            }
        }
    }
    private static boolean allowed(Player p,Product product){return product.permission.isBlank()||p.hasPermission(product.permission);}
    private void render(Player player,View view) {
        if(!plugin.isEnabled())return;
        var state=new JsonObject();state.addProperty("shop-status",view.status);state.addProperty("shop-balance",view.balance);
        var body=new StringBuilder("<mc-panel id=\"shop\" class=\"shell\">").append(Views.heading(title));
        body.append("<div class=\"shop-account\"><span class=\"muted\">物资集市 / 系统商店</span><span>钱包余额　<span class=\"gold\" bind=\"shop-balance\"></span></span></div><main id=\"shop-body\" class=\"body\">");
        var actions=new HashSet<String>();
        if(view.stage.equals("catalog")) {
            var categories=new LinkedHashMap<String,List<Product>>();for(var p:products)if(allowed(player,p))categories.computeIfAbsent(p.category,k->new ArrayList<>()).add(p);
            body.append(Views.hero("SUPPLIES / EQUIPMENT","为下一次冒险做好准备","从第一把铁镐，到远行时的护甲。让熟悉的方块物资陪你走得更远。"))
                .append("<div class=\"shop-columns\"><section class=\"grow\"><div class=\"section-head\"><h2>物资陈列</h2><span class=\"muted\">").append(categories.values().stream().mapToInt(List::size).sum()).append(" 件商品</span></div><mc-tabs><mc-tab id=\"filter-all\" class=\"filter-button selected\" action=\"filter:all\">全部</mc-tab>");int i=0;
            for(var entry:categories.entrySet())body.append("<mc-tab id=\"filter-category").append(i).append("\" class=\"filter-button\" action=\"filter:category").append(i++).append("\">").append(Views.escape(entry.getKey())).append(" · ").append(entry.getValue().size()).append("</mc-tab>");
            body.append("</mc-tabs><div class=\"grid products\">");i=0;
            for(var entry:categories.entrySet()) {
                for(var product:entry.getValue()) {
                    body.append("<mc-card id=\"card-").append(product.id).append("\" filter-category=\"category").append(i).append("\" class=\"tile product\"><div class=\"row product-head\"><mc-slot item=\"minecraft:").append(product.material.name().toLowerCase(Locale.ROOT)).append("\"/><div class=\"grow\"><span class=\"muted\">").append(Views.escape(product.category)).append(" / × ").append(product.amount).append("</span><h3>")
                        .append(Views.escape(product.name)).append("</h3></div></div><p class=\"product-description muted\">").append(Views.escape(description(product))).append("</p><div class=\"product-price\"><span class=\"price\">")
                        .append(product.price.toPlainString()).append("</span><span class=\"muted\">").append(Views.escape(product.currency)).append(" / 份</span></div>");
                    button(body,actions,"product-"+product.id,"挑选 "+product.name,product.material.name(),"挑选 "+product.name+" · "+product.amount+" 个 / "+product.price.toPlainString()+" "+product.currency);body.append("</mc-card>");
                }
                i++;
            }
            body.append("</div></section><section class=\"aside shop-aside\"><mc-card><span class=\"eyebrow\">YOUR SATCHEL</span><h2>行囊与订单</h2><div class=\"data-line\"><span>背包空位</span><span class=\"gold\">").append(Arrays.stream(player.getInventory().getStorageContents()).filter(s->s==null||s.getType().isAir()).count()).append(" / 36</span></div><div class=\"data-line\"><span>待处理订单</span><span>").append(orders.pending(player.getUniqueId()).size()).append("</span></div><div class=\"rule\"></div><p class=\"muted\">01　挑选物资与份数</p><p class=\"muted\">02　核对完整报价</p><p class=\"muted\">03　付款后放入背包</p></mc-card><mc-card><span class=\"eyebrow\">出发前的小提醒</span><p>给行囊留一点空间。</p><p class=\"muted\">已付款物资会保留领取记录，整理好背包后可以继续领取。</p></mc-card></section></div>");
            if(!orders.pending(player.getUniqueId()).isEmpty())button(body,actions,"recover","领取物资 / 重试原订单","CHEST");
        } else if(view.stage.equals("quantity")) {
            var product=view.selected;
            body.append("<div class=\"purchase-layout\"><div class=\"purchase-art\"><mc-slot item=\"minecraft:").append(product.material.name().toLowerCase(Locale.ROOT)).append("\"/><span class=\"eyebrow\">").append(Views.escape(product.category)).append(" / 物资详情</span><h2>").append(Views.escape(product.name)).append("</h2><p class=\"muted\">").append(Views.escape(product.description)).append("</p></div><mc-card class=\"purchase-form\"><span class=\"eyebrow\">01 / 选择份数</span><h2>").append(Views.escape(product.name)).append("</h2><p>每份 ").append(product.amount).append(" 个，")
                .append(product.price.toPlainString()).append(" ").append(Views.escape(product.currency)).append("</p><label>购买份数（1～64）　<span class=\"gold\" id=\"quantity-value\">1</span> 份</label><mc-slider id=\"quantity\" value-target=\"quantity-value\" aria-label=\"购买份数\" min=\"1\" max=\"64\" step=\"1\" value=\"1\"/>");
            actions.add("server:quote");body.append("<mc-button class=\"primary\" id=\"quote\" action=\"server:quote\" input-ids=\"quantity\" aria-label=\"查看报价\">查看报价 →</mc-button><p class=\"muted\">下一步核对总数与金额。</p></mc-card></div>");
            button(body,actions,"back","返回商店","ARROW");
        } else if(view.stage.equals("confirm")) {
            var quote=view.quote;String summary=quote.product.name+" × "+(quote.product.amount*quote.packs)+" · 合计 "+quote.total.toPlainString()+" "+quote.product.currency;
            state.addProperty("shop-quote",summary);
            body.append("<mc-card class=\"checkout\"><span class=\"eyebrow\">02 / 核对订单</span><h2>确认购买</h2><div class=\"row\"><mc-slot item=\"minecraft:").append(quote.product.material.name().toLowerCase(Locale.ROOT)).append("\"/><p class=\"quote-line\" bind=\"shop-quote\"></p></div><div class=\"rule\"></div><p class=\"muted\">确认后从钱包扣款，物资交付到背包。</p>");
            button(body,actions,"pay","确认付款","EMERALD");button(body,actions,"back","取消并返回","ARROW");body.append("</mc-card>");
        } else body.append("<mc-card><h2>正在处理订单</h2><p>订单完成后会显示结果，关闭界面也会保留交付记录。</p></mc-card>");
        body.append("</main><footer class=\"status\" id=\"shop-status\" bind=\"shop-status\"></footer></mc-panel>");
        plugin.open(player,Views.document(title,Views.page(title,"""
            .shop-account{display:flex;justify-content:space-between;gap:12px;flex-shrink:0;padding:9px 22px;font-size:12px;background-color:#14272b;}
            .shop-columns{display:flex;align-items:flex-start;}.shop-aside{margin-top:20px;}.hero{height:100px;min-height:0;}.hero-copy{width:50%;padding:12px 20px;}.hero-copy h2{font-size:23px;margin-bottom:7px;}.hero-art{width:50%;height:100px;}
            .product{width:23%;min-width:150px;flex-grow:0;padding:10px;}.product-head .mc-slot{width:45px;height:45px;border:0;background-color:#183038;}.product-head .muted{font-size:10px;}.product-head{margin-bottom:7px;gap:8px;}
            .product h3{font-size:14px;margin:3px 0 0;}.product-description{font-size:10px;height:15px;overflow:hidden;margin:0;}.product-price{display:flex;align-items:center;gap:7px;margin:4px 0 5px;}.price{font-size:20px;color:#ecd19d;}
            .product .mc-button{width:100%;min-width:0;font-size:11px;height:29px;padding:5px 2px;}.filter-button{font-size:12px;min-width:55px;}
            .purchase-layout{display:flex;gap:24px;max-width:850px;margin:25px auto;}.purchase-art{width:40%;text-align:center;padding:20px;background-color:#1d3439;}.purchase-art .mc-slot{display:block;width:150px;height:150px;margin:0 auto 16px;}
            .purchase-form{flex:1;min-width:0;padding:25px;}.purchase-form .mc-button{display:block;width:100%;margin:18px 0;}.checkout{width:80%;max-width:700px;margin:30px auto;padding:28px;}.checkout .mc-button{margin:14px 10px 0 0;}.quote-line{font-size:20px;color:#e8d6ad;}
            @media(max-width:1100px){.shop-aside{display:none;}.product{width:23.5%;}}
            @media(max-width:760px){.product{width:31%;min-width:125px;}.shop-account{padding:8px 14px;}.purchase-layout{gap:12px;}.purchase-form{padding:16px;}}
            @media(max-width:520px){.product{width:47%;}.purchase-art{display:none;}.hero-art{display:none;}.hero-copy{width:100%;}.checkout{width:100%;}}
            @media(max-height:530px){.hero{display:none;}.shop-columns .section-head{margin-top:0;}.shop-aside{display:none;}.purchase-layout{margin:5px auto;}.purchase-art .mc-slot{width:90px;height:90px;}.checkout{margin:5px auto;padding:20px;}}
            """,body.toString()),state,actions),view.handler);
    }
    private static void button(StringBuilder body,Set<String> actions,String id,String label,String icon) {
        button(body,actions,id,label,icon,label);
    }
    private static String description(Product product) {
        if(!product.description.startsWith("原商店目录"))return product.description;
        return switch(product.category) {
            case "装备" -> "穿戴防护 · 为远行做好准备";
            case "工具" -> "采集与建造 · 开始新的工程";
            case "材料" -> "合成与储备 · 随时补充物资";
            case "武器" -> "战斗与防御 · 探索未知之地";
            default -> "冒险补给 · 备好你的行囊";
        };
    }
    private static void button(StringBuilder body,Set<String> actions,String id,String label,String icon,String fallbackLabel) {
        actions.add("server:"+id);body.append("<mc-button id=\"").append(id).append("\" action=\"server:").append(id).append("\" aria-label=\"")
            .append(Views.escape(label)).append("\" fallback-label=\"").append(Views.escape(fallbackLabel)).append("\" fallback-icon=\"").append(icon).append("\">").append(Views.escape(label)).append("</mc-button>");
    }
    private void balance(Player player,View view) {
        var service=WalletApi.walletOrNull();if(service==null)return;
        var currencies=products.stream().map(Product::currency).distinct().toList();var futures=new ArrayList<CompletableFuture<WalletBalance>>();
        for(String currency:currencies)futures.add(service.balance(player.getUniqueId(),player.getName(),currency));
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ok,error)->plugin.onPlayer(player,()->{
            if(!plugin.current(player,view.handler))return;
            view.balance=error==null?futures.stream().map(f->f.join().balance().toPlainString()+" "+f.join().assetCode()).collect(java.util.stream.Collectors.joining(" / ")):"暂时无法查询";
            var state=new JsonObject();state.addProperty("shop-balance",view.balance);plugin.patch(player,state);
        }));
    }
    private void buy(Player player,View view,Quote quote) {
        if(!allowed(player,quote.product)||!orders.pending(player.getUniqueId()).isEmpty()){feedback(player,view,"请先处理未完成订单。",true);return;}
        int amount=quote.product.amount*quote.packs;
        if(!fits(player,quote.product.material,amount)){feedback(player,view,"背包空间不足，请先整理背包。",true);return;}
        if(!processing.add(player.getUniqueId()))return;
        var wallet=WalletApi.walletOrNull();
        if(wallet==null){processing.remove(player.getUniqueId());feedback(player,view,"钱包暂不可用，尚未发起扣款。",true);return;}
        view.stage="busy";view.status="正在校验币种与订单…";render(player,view);
        var order=new ShopOrders.Order(UUID.randomUUID().toString(),player.getUniqueId(),player.getName(),quote.product.id,quote.product.material.name(),amount,quote.product.currency,quote.total,signature(quote.product.currency),ShopOrders.Status.PAYMENT_PENDING);
        wallet.assets().whenComplete((assets,currencyError)->plugin.onPlayer(player,()->{
            try {
                if(currencyError!=null)throw new IllegalStateException("Wallet unavailable",currencyError);
                var asset=assets.stream().filter(a->a.enabled()&&a.code().equalsIgnoreCase(order.currency())).findFirst().orElseThrow();
                order.price().setScale(asset.scale(),RoundingMode.UNNECESSARY);
                if(WalletApi.walletOrNull()!=wallet||!signature(order.currency()).equals(order.wallet()))throw new IllegalStateException("Wallet source changed");
            }catch(Exception invalid){processing.remove(order.player());feedback(player,view,"币种不可用或价格精度不符，尚未创建订单或扣款。",true);return;}
            orders.create(order).whenComplete((created,error)->{
                if(error!=null){processing.remove(order.player());plugin.onPlayer(player,()->feedback(player,view,"订单无法落盘，尚未发起扣款。",true));return;}
                pay(player,view,created);
            });
        },()->processing.remove(order.player())));
    }
    private void pay(Player player,View view,ShopOrders.Order order) {
        if(!signature(order.currency()).equals(order.wallet())){processing.remove(order.player());plugin.onPlayer(player,()->feedback(player,view,"钱包来源配置已变化，订单需人工核对："+order.id(),true));return;}
        var wallet=WalletApi.walletOrNull();
        if(wallet==null){processing.remove(order.player());return;}
        wallet.assets().thenCompose(assets->{
            var asset=assets.stream().filter(a->a.enabled()&&a.code().equalsIgnoreCase(order.currency())).findFirst().orElseThrow(()->new IllegalArgumentException("Currency unavailable"));
            order.price().setScale(asset.scale(),RoundingMode.UNNECESSARY);
            return wallet.debit(order.player(),order.playerName(),order.currency(),order.price(),order.businessNo(),"系统商店 "+order.product());
        }).whenComplete((transaction,error)->{
            if(error!=null){
                Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
                // Only a definitive insufficient-funds rejection is final. Unknown network outcomes keep the same key.
                if(cause instanceof WalletException && cause.getMessage()!=null && cause.getMessage().contains("余额不足")) {
                    orders.transition(order,ShopOrders.Status.PAYMENT_PENDING,ShopOrders.Status.DECLINED).whenComplete((ignored,storageError)->{
                        processing.remove(order.player());plugin.onPlayer(player,()->feedback(player,view,storageError==null?"余额不足，购买未完成。":"订单状态待核对："+order.id(),true));
                    });
                } else {processing.remove(order.player());plugin.onPlayer(player,()->feedback(player,view,"扣款结果待确认，可用原订单重试："+order.id(),true));}
                return;
            }
            orders.transition(order,ShopOrders.Status.PAYMENT_PENDING,ShopOrders.Status.PAID).whenComplete((paid,error2)->{
                if(error2!=null){processing.remove(order.player());plugin.onPlayer(player,()->feedback(player,view,"已扣款，交付记录待核对："+order.id(),true));return;}
                plugin.onPlayer(player,()->deliver(player,view,paid),()->processing.remove(order.player()));
            });
        });
    }
    private void deliver(Player player,View view,ShopOrders.Order order) {
        Material material=Material.matchMaterial(order.material());
        if(!player.isOnline()||material==null||!fits(player,material,order.amount())) {
            processing.remove(order.player());feedback(player,view,"订单已付款，整理背包后可领取物资。",true);return;
        }
        orders.transition(order,ShopOrders.Status.PAID,ShopOrders.Status.DELIVERING).whenComplete((delivering,error)->{
            if(error!=null){processing.remove(order.player());return;}
            plugin.onPlayer(player,()->{
                if(!fits(player,material,order.amount())) {
                    orders.transition(delivering,ShopOrders.Status.DELIVERING,ShopOrders.Status.PAID).whenComplete((o,e)->processing.remove(order.player()));return;
                }
                try {
                    var items=stacks(material,order.amount());
                    if(!player.getInventory().addItem(items.toArray(ItemStack[]::new)).isEmpty())throw new IllegalStateException("Partial inventory delivery");
                    player.saveData();
                    orders.transition(delivering,ShopOrders.Status.DELIVERING,ShopOrders.Status.DELIVERED).whenComplete((done,error2)->{
                        processing.remove(order.player());plugin.onPlayer(player,()->{feedback(player,view,error2==null?"购买成功，物资已放入背包。":"物资已交付，订单记录需核对："+order.id(),true);balance(player,view);});
                    });
                } catch(Exception failure) {
                    orders.transition(delivering,ShopOrders.Status.DELIVERING,ShopOrders.Status.REVIEW).whenComplete((o,e)->processing.remove(order.player()));
                    feedback(player,view,"交付状态需人工核对："+order.id(),true);
                }
            },()->orders.transition(delivering,ShopOrders.Status.DELIVERING,ShopOrders.Status.PAID).whenComplete((o,e)->processing.remove(order.player())));
        });
    }
    private void recover(Player player,View view) {
        var pending=orders.pending(player.getUniqueId());if(pending.isEmpty())return;
        var order=pending.getFirst();if(!processing.add(player.getUniqueId()))return;
        if(order.status()==ShopOrders.Status.PAID)deliver(player,view,order);
        else if(order.status()==ShopOrders.Status.PAYMENT_PENDING){view.stage="busy";view.status="正在重试原订单…";render(player,view);pay(player,view,order);}
        else {processing.remove(order.player());feedback(player,view,"该订单需人工核对："+order.id(),true);}
    }
    private void feedback(Player player,View view,String message,boolean catalog) {
        if(!plugin.isEnabled())return;
        if(plugin.current(player,view.handler)){view.status=message;if(catalog)view.stage="catalog";render(player,view);}
        else player.sendMessage(net.kyori.adventure.text.Component.text(message));
    }
    static List<ItemStack> stacks(Material material,int amount) {
        var items=new ArrayList<ItemStack>();int max=material.getMaxStackSize();
        while(amount>0){int count=Math.min(max,amount);items.add(new ItemStack(material,count));amount-=count;}return items;
    }
    private static boolean fits(Player player,Material material,int amount) {
        int room=0;var prototype=new ItemStack(material);int max=material.getMaxStackSize();
        for(var stack:player.getInventory().getStorageContents()) {
            if(stack==null||stack.getType().isAir())room+=max;else if(stack.isSimilar(prototype))room+=Math.max(0,max-stack.getAmount());
        }
        return room>=amount;
    }
    private String signature(String currency) {
        var wallet=(JavaPlugin)Bukkit.getPluginManager().getPlugin("Wallet");if(wallet==null||!wallet.isEnabled())return "unavailable";var c=wallet.getConfig();
        var section=c.getConfigurationSection("currencies."+currency);
        String config=c.getString("backend","local")+"|"+c.getBoolean("test-mode.enabled")+"|"+c.getString("test-mode.currency-code","TEST")+"|"+c.getString("base-url","")+"|"+currency+"|"+(section==null?"":new TreeMap<>(section.getValues(true)).toString());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(config.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}
    }
    List<ShopOrders.Order> orders(){return orders.all();}
    @Override public void close(){orders.close();}
}
