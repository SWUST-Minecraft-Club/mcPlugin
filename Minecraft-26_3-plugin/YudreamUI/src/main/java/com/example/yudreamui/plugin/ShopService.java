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
        body.append("<div class=\"row\"><span class=\"muted\">Wallet 余额</span><span bind=\"shop-balance\"></span></div><main id=\"shop-body\" class=\"body\">");
        var actions=new HashSet<String>();
        if(view.stage.equals("catalog")) {
            var categories=new LinkedHashMap<String,List<Product>>();for(var p:products)if(allowed(player,p))categories.computeIfAbsent(p.category,k->new ArrayList<>()).add(p);
            body.append("<mc-tabs>");int i=0;
            for(var category:categories.keySet())body.append("<mc-tab id=\"tab-category").append(i).append("\" class=\"").append(i==0?"selected":"").append("\" action=\"tab:category").append(i++).append("\">").append(Views.escape(category)).append("</mc-tab>");
            body.append("</mc-tabs>");i=0;
            for(var entry:categories.entrySet()) {
                body.append("<div id=\"category").append(i).append("\" class=\"tab-page ").append(i++==0?"":"hidden").append("\"><div class=\"grid\">");
                for(var product:entry.getValue()) {
                    body.append("<mc-card class=\"tile\"><div class=\"row\"><mc-slot item=\"minecraft:").append(product.material.name().toLowerCase(Locale.ROOT)).append("\"/><h2>")
                        .append(Views.escape(product.name)).append("</h2></div><p class=\"muted\">").append(Views.escape(product.description)).append("</p><p>")
                        .append(product.amount).append(" 个 / 份 · ").append(product.price.toPlainString()).append(" ").append(Views.escape(product.currency)).append("</p>");
                    button(body,actions,"product-"+product.id,"挑选 "+product.name,product.material.name(),"挑选 "+product.name+" · "+product.amount+" 个 / "+product.price.toPlainString()+" "+product.currency);body.append("</mc-card>");
                }
                body.append("</div></div>");
            }
            if(!orders.pending(player.getUniqueId()).isEmpty())button(body,actions,"recover","领取物资 / 重试原订单","CHEST");
        } else if(view.stage.equals("quantity")) {
            var product=view.selected;
            body.append("<mc-card><h2>").append(Views.escape(product.name)).append("</h2><p>每份 ").append(product.amount).append(" 个，")
                .append(product.price.toPlainString()).append(" ").append(Views.escape(product.currency)).append("</p><label>购买份数（1～64）</label><mc-slider id=\"quantity\" aria-label=\"购买份数\" min=\"1\" max=\"64\" step=\"1\" value=\"1\"/>");
            actions.add("server:quote");body.append("<mc-button id=\"quote\" action=\"server:quote\" input-ids=\"quantity\" aria-label=\"查看报价\">查看报价</mc-button></mc-card>");
            button(body,actions,"back","返回商店","ARROW");
        } else if(view.stage.equals("confirm")) {
            var quote=view.quote;String summary=quote.product.name+" × "+(quote.product.amount*quote.packs)+" · 合计 "+quote.total.toPlainString()+" "+quote.product.currency;
            state.addProperty("shop-quote",summary);
            body.append("<mc-card><h2>确认购买</h2><p bind=\"shop-quote\"></p><p class=\"muted\">确认后从 Wallet 扣款并交付到背包。</p>");
            button(body,actions,"pay","确认付款","EMERALD");button(body,actions,"back","取消并返回","ARROW");body.append("</mc-card>");
        } else body.append("<mc-card><h2>正在处理订单</h2><p>订单完成后会显示结果，关闭界面也会保留交付记录。</p></mc-card>");
        body.append("</main><footer class=\"status\" id=\"shop-status\" bind=\"shop-status\"></footer></mc-panel>");
        plugin.open(player,Views.document(title,Views.page(title,"""
            .shell{background-color:#dedbc8;border-top-color:#fff8df;border-left-color:#fff8df;border-bottom-color:#53664d;border-right-color:#53664d;}
            .tile{background-color:#f4efdd;border-color:#aeb7a2;}.tile h2{font-size:13px;color:#31573c;}.tile p{margin-top:6px;}
            .tile .mc-button{background-color:#52714b;border-top-color:#b1c79a;border-left-color:#b1c79a;}.tile .mc-button:hover{background-color:#719267;}
            .mc-tabs{margin-top:8px;}.tile .mc-slot{background-color:#d4d8c5;border-top-color:#7e9175;border-left-color:#7e9175;}
            """,body.toString()),state,actions),view.handler);
    }
    private static void button(StringBuilder body,Set<String> actions,String id,String label,String icon) {
        button(body,actions,id,label,icon,label);
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
