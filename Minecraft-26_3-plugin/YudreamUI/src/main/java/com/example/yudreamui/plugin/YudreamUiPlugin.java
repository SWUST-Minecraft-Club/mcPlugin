package com.example.yudreamui.plugin;

import com.example.yudreamui.protocol.*;
import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class YudreamUiPlugin extends JavaPlugin implements Listener,YudreamUiApi {
    private final Map<UUID,Peer> peers = new ConcurrentHashMap<>();
    private final Map<UUID,Session> sessions = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private FallbackMenus fallbacks;
    private String demo;
    private LegacyPresentation legacy;
    private DialogueService dialogues;
    private ShopService shop;
    private Runnable mainMenuCleanup = () -> {};

    @Override public void onEnable() {
        saveDefaultConfig();
        for(String name:List.of("showcase.html","assets.html"))if(!Files.exists(getDataPath().resolve("menus/"+name)))saveResource("menus/"+name,false);
        if(!Files.exists(getDataPath().resolve("fallback/trmenu-yudreamui.yml")))saveResource("fallback/trmenu-yudreamui.yml",false);
        for(String name:List.of("shop.yml","dialogues.yml"))if(!Files.exists(getDataPath().resolve(name)))saveResource(name,false);
        reloadDefinitions();
        fallbacks = new FallbackMenus(this);
        fallbacks.register(new TrMenuBridge(this));
        getServer().getPluginManager().registerEvents(fallbacks,this);
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getMessenger().registerOutgoingPluginChannel(this,Wire.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this,Wire.CHANNEL,(channel,player,bytes)->onPlayer(player,()->receive(player,bytes)));
        getServer().getServicesManager().register(YudreamUiApi.class,this,this,ServicePriority.Normal);
        legacy = new LegacyPresentation(this);
        dialogues = new DialogueService(this);
        if(getServer().getPluginManager().isPluginEnabled("Wallet")) {
            try { shop = new ShopService(this); }
            catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"系统商店初始化失败；不会执行购买",error);}
        }
        if(getServer().getPluginManager().isPluginEnabled("MainMenu")) {
            var integration = new MainMenuIntegration(this,legacy); mainMenuCleanup = integration::shutdown;
        }
        if(getServer().getPluginManager().isPluginEnabled("Town"))new TownIntegration(this,legacy);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,event->{
            var root=Commands.literal("yui").requires(s->s.getSender().hasPermission("yudreamui.use"))
                .executes(ctx->{if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->show(p,false,"auto"));return 1;})
                .then(Commands.literal("assets").requires(s->s.getSender().hasPermission("yudreamui.admin")).executes(ctx->{if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->show(p,true,"auto"));return 1;}))
                .then(Commands.literal("shop").requires(s->s.getSender().hasPermission("yudreamui.shop")).executes(ctx->{if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->openShop(p));return 1;}))
                .then(Commands.literal("dialogue").executes(ctx->{if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->openDialogue(p,"guide"));return 1;})
                    .then(Commands.argument("id",StringArgumentType.word()).executes(ctx->{if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->openDialogue(p,StringArgumentType.getString(ctx,"id")));return 1;})))
                .then(Commands.literal("orders").requires(s->s.getSender().hasPermission("yudreamui.admin")).executes(ctx->{
                    if(shop!=null)for(var order:shop.orders())ctx.getSource().getSender().sendMessage(Component.text(order.id()+" "+order.playerName()+" "+order.price().toPlainString()+" "+order.currency()+" "+order.status()));return 1;}))
                .then(Commands.literal("import-shop").requires(s->s.getSender().hasPermission("yudreamui.admin"))
                    .then(Commands.argument("currency",StringArgumentType.word()).executes(ctx->{
                        importShop(ctx.getSource().getSender(),StringArgumentType.getString(ctx,"currency"));return 1;})))
                .then(Commands.literal("refresh").requires(s->s.getSender().hasPermission("yudreamui.admin")).executes(ctx->{
                    if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->{reloadDefinitions();show(p,true,"auto");});return 1;}))
                .then(Commands.literal("theme").requires(s->s.getSender().hasPermission("yudreamui.admin")).executes(ctx->{
                    if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->{reloadConfig();var s=sessions.get(p.getUniqueId());if(s!=null&&s.original!=null){var d=s.original;open(p,new Document(d.title(),d.html(),d.assets(),s.state.deepCopy(),d.actions(),d.fallback()),s.pendingHandler);}});return 1;}))
                .then(Commands.literal("status").executes(ctx->{if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->{
                    var s=sessions.get(p.getUniqueId());p.sendMessage(Component.text("YudreamUI client="+isClientReady(p)+" backend="+(s==null?"closed":s.backend)+" revision="+(s==null?0:s.committed)));});return 1;}))
                .then(Commands.literal("invoke").then(Commands.argument("ticket",StringArgumentType.word())
                    .then(Commands.argument("control",StringArgumentType.word()).executes(ctx->{
                        if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->invokeExternal(p,StringArgumentType.getString(ctx,"ticket"),StringArgumentType.getString(ctx,"control")));return 1;}))));
            root.then(Commands.literal("open").requires(source->source.getSender().hasPermission("yudreamui.admin"))
                .then(Commands.argument("player",StringArgumentType.word()).executes(ctx->{
                    var player=getServer().getPlayerExact(StringArgumentType.getString(ctx,"player"));
                    if(player!=null)onPlayer(player,()->show(player,false,"auto"));return 1;
                })));
            for(String backend:List.of("inventory","dialog","trmenu"))root.then(Commands.literal(backend).executes(ctx->{
                if(ctx.getSource().getSender() instanceof Player p)onPlayer(p,()->show(p,false,backend));return 1;}));
            event.registrar().register(root.build(),"Open YudreamUI");
        });
        getLogger().info("YudreamUI protocol 1 / semantic fallback 1 / RmlUi 6.3 / mc-1");
    }
    private void reloadDefinitions() {
        reloadConfig();
        try {String candidate=Files.readString(getDataPath().resolve("menus/showcase.html"));FallbackPlan.fromHtml(candidate,"小镇工坊");demo=candidate;}
        catch(Exception error) { throw new IllegalStateException("Invalid UI definition",error); }
    }
    @EventHandler public void join(PlayerJoinEvent event) { var p=event.getPlayer();p.getScheduler().runDelayed(this,t->send(p,Wire.message("probe")),null,10); }
    @EventHandler public void quit(PlayerQuitEvent event) { var p=event.getPlayer();peers.remove(p.getUniqueId());sessions.remove(p.getUniqueId());fallbacks.forget(p);legacy.forget(p.getUniqueId()); }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void legacyShopCommand(PlayerCommandPreprocessEvent event) {
        if(!getConfig().getBoolean("takeover.enabled",true)||shop==null)return;
        String command=event.getMessage().strip().toLowerCase(Locale.ROOT);
        if(command.matches("/(?:arcmenu|arc) open shop(?:-[a-z0-9_-]+)?")) {event.setCancelled(true);onPlayer(event.getPlayer(),()->openShop(event.getPlayer()));}
    }
    @Override public void registerFallback(FallbackProvider provider) { fallbacks.register(provider); }
    @Override public boolean isClientReady(Player player) { var peer=peers.get(player.getUniqueId());return peer!=null&&peer.features.containsAll(Set.of("rml:6.3","builtin:mc-1","fallback:1")); }
    boolean supportsFeature(Player player,String feature){var peer=peers.get(player.getUniqueId());return isClientReady(player)&&peer.features.contains(feature);}

    private void receive(Player player,byte[] bytes) {
        var peer=peers.computeIfAbsent(player.getUniqueId(),id->new Peer());if(!peer.allow())return;
        try {
            var m=peer.decoder.accept(bytes);if(m==null)return;String kind=m.get("kind").getAsString();
            if(kind.equals("hello")) {
                var features=new HashSet<String>();
                if(m.has("features")&&m.getAsJsonArray("features").size()<=32)for(var feature:m.getAsJsonArray("features"))if(feature.getAsString().length()<=64)features.add(feature.getAsString());
                peer.features=Set.copyOf(features);send(player,Wire.message("welcome"));return;
            }
            var s=sessions.get(player.getUniqueId());
            if(s==null||!s.backend.equals("rml")||!m.get("session").getAsString().equals(s.id))return;
            long revision=m.get("revision").getAsLong();
            if(kind.equals("ready")) {
                if(revision==s.nextRevision) {s.committed=revision;s.active=s.pending;s.handler=s.pendingHandler;patch(player,s.state.deepCopy());}return;
            }
            if(kind.equals("error")) {
                if(revision==s.nextRevision&&s.committed==0)fallback(player,s,getConfig().getString("fallback.backend","inventory"));return;
            }
            if(revision!=s.committed)return;
            if(kind.equals("closed")) {if(sessions.remove(player.getUniqueId(),s))notifyClosed(s);return;}
            if(!kind.equals("action"))return;
            long sequence=m.get("sequence").getAsLong();if(sequence<=s.lastSequence)return;s.lastSequence=sequence;
            dispatch(player,s,m.get("id").getAsString(),m.get("action").getAsString(),m.getAsJsonObject("form"));
        } catch(Exception error) { getLogger().warning("Rejected UI message: "+error.getClass().getSimpleName()); }
    }
    @Override public void open(Player player,Document document,BiConsumer<Player,Action> handler) {open(player,document,handler,"auto");}
    private void open(Player player,Document document,BiConsumer<Player,Action> handler,String preferred) {
        requireOwner(player);Objects.requireNonNull(handler);
        validateState(document.state());
        String source=document.html();var manifest=document.assets().deepCopy();
        for(var entry:getConfig().getMapList("presentation-assets")) {
            String id=Objects.toString(entry.get("id"),"");
            if(!Set.of("atlas-village","atlas-guide").contains(id)||!source.contains("builtin:"+id))continue;
            var asset=new JsonObject();for(String key:List.of("id","url","hash"))asset.addProperty(key,Objects.toString(entry.get(key),""));asset.addProperty("kind","image");
            manifest.add(asset);source=source.replace("builtin:"+id,"asset:"+id);
        }
        String html=HtmlCompiler.compile(source,"");
        if(document.actions().stream().anyMatch(a->!a.matches("server:[A-Za-z0-9_-]{1,64}")))throw new IllegalArgumentException("Invalid server action");
        for(var button:document.fallback().buttons())if(!document.actions().contains(button.action()))throw new IllegalArgumentException("Undeclared UI action");
        var frozen=new Document(document.title(),html,manifest,document.state().deepCopy(),Set.copyOf(document.actions()),document.fallback());
        var s=sessions.computeIfAbsent(player.getUniqueId(),id->new Session());
        s.closed = null;
        String oldBackend=s.backend;
        if(!oldBackend.equals("rml"))fallbacks.close(player);
        s.nextRevision++;s.original=document;s.pending=frozen;s.pendingHandler=handler;s.state=frozen.state().deepCopy();s.ticket=UUID.randomUUID().toString();
        boolean supports=isClientReady(player)&&(frozen.assets().isEmpty()||peers.get(player.getUniqueId()).features.contains("assets:sha256"));
        supports=supports&&(!html.contains("ui-density=\"2\"")||peers.get(player.getUniqueId()).features.contains("atlas:2"));
        supports=supports&&(!html.contains("<mcterrain")||peers.get(player.getUniqueId()).features.contains("terrain:local-1"));
        if(!preferred.equals("auto")||!supports) {
            if(oldBackend.equals("rml")&&s.committed>0){var cancel=address("cancel",s,s.committed);send(player,cancel);}
            fallback(player,s,preferred.equals("auto")?getConfig().getString("fallback.backend","inventory"):preferred);return;
        }
        if(!oldBackend.equals("rml"))s.committed=0;
        s.backend="rml";
        var m=address("open",s,s.nextRevision);m.addProperty("title",frozen.title());m.addProperty("html",frozen.html());
        m.add("assets",frozen.assets());m.add("state",frozen.state());m.add("fallback",gson.toJsonTree(frozen.fallback()));send(player,m);
        long pending=s.nextRevision;
        player.getScheduler().runDelayed(this,t->{
            if(sessions.get(player.getUniqueId())==s&&s.backend.equals("rml")&&s.nextRevision==pending&&s.committed==0)fallback(player,s,getConfig().getString("fallback.backend","inventory"));
        },null,600);
    }
    private void fallback(Player player,Session s,String backend) {
        send(player,address("cancel",s,s.nextRevision));
        s.backend=backend;s.active=s.pending;s.handler=s.pendingHandler;s.committed=s.nextRevision;
        String ticket=s.ticket;long revision=s.committed;
        var context=new FallbackProvider.Context() {
            public FallbackPlan plan(){return s.active.fallback();}
            public JsonObject state(){return s.state.deepCopy();}
            public String ticket(){return ticket;}
            public boolean active(){return sessions.get(player.getUniqueId())==s&&s.ticket.equals(ticket)&&s.committed==revision&&!s.backend.equals("rml");}
            public void invoke(String id,JsonObject form){if(active())dispatch(player,s,id,plan().button(id).action(),form);}
            public void edit(String id){if(active())fallbacks.edit(player,id);}
            public void close(){if(active())YudreamUiPlugin.this.close(player);}
        };
        s.backend=fallbacks.open(player,context,backend);
    }
    private void dispatch(Player player,Session s,String id,String action,JsonObject submitted) {
        requireOwner(player);
        if(!player.hasPermission("yudreamui.use")||!s.active.actions().contains(action)||!s.allowAction())return;
        try {
            if(submitted.size()>128||submitted.toString().length()>8192)return;
            var form=s.active.fallback().validate(id,action,submitted);
            s.handler.accept(player,new Action(action,id,form));
        }catch(IllegalArgumentException error){getLogger().warning("Rejected UI action: "+error.getMessage());}
    }
    private void invokeExternal(Player player,String ticket,String id) {
        var s=sessions.get(player.getUniqueId());if(s==null||!s.backend.equals("trmenu")||!s.ticket.equals(ticket))return;
        try {var button=s.active.fallback().button(id);if(button.inputs().isEmpty())dispatch(player,s,id,button.action(),new JsonObject());else fallbacks.edit(player,id);}
        catch(IllegalArgumentException ignored) {}
    }
    @Override public void patch(Player player,JsonObject state) {
        requireOwner(player);var s=sessions.get(player.getUniqueId());if(s==null)return;
        validateState(state);
        state.entrySet().forEach(e->s.state.add(e.getKey(),e.getValue().deepCopy()));if(s.committed==0)return;
        if(!s.backend.equals("rml")){String actual=fallbacks.patch(player);if(actual!=null)s.backend=actual;return;}
        var m=address("patch",s,s.committed);m.add("state",state);send(player,m);
    }
    @Override public void close(Player player) {
        requireOwner(player);var s=sessions.remove(player.getUniqueId());if(s==null)return;
        if(s.backend.equals("rml"))send(player,address("cancel",s,s.nextRevision));else fallbacks.close(player);
        notifyClosed(s);
    }
    boolean current(Player player,BiConsumer<Player,Action> handler) {var s=sessions.get(player.getUniqueId());return handler!=null&&s!=null&&s.pendingHandler==handler;}
    void setCloseHandler(Player player,Runnable closed){var s=sessions.get(player.getUniqueId());if(s!=null)s.closed=closed;}
    void clearCloseHandler(Player player){var s=sessions.get(player.getUniqueId());if(s!=null)s.closed=null;}
    private void notifyClosed(Session session){var closed=session.closed;session.closed=null;if(closed!=null)closed.run();}
    @Override public void openDialogue(Player player,String id){requireOwner(player);try{dialogues.open(player,id);}catch(IllegalArgumentException error){player.sendMessage(Component.text(error.getMessage()));}}
    @Override public void openShop(Player player){requireOwner(player);if(shop==null){player.sendMessage(Component.text("系统商店暂不可用，请检查 Wallet 和商品配置。"));return;}shop.open(player);}
    private void importShop(org.bukkit.command.CommandSender sender,String currency) {
        // Administrative migration runs away from region threads; existing files are copied before replacement.
        getServer().getAsyncScheduler().runNow(this,task->{
            try {
                var result=ArcShopImport.read(getDataPath().getParent().resolve("ArcMenu"),currency);
                var backup=getDataPath().resolve("migration/"+java.time.Instant.now().toEpochMilli());Files.createDirectories(backup);
                Path catalog=getDataPath().resolve("shop.yml");if(Files.exists(catalog))Files.copy(catalog,backup.resolve("shop.yml"));
                Files.writeString(getDataPath().resolve("shop.imported.yml"),result.yaml());
                Files.writeString(backup.resolve("report.txt"),"products="+result.products()+"\ncurrency="+currency+"\nsources="+result.sources()+"\nskipped="+result.skipped()+"\n");
                // Keep running quotes and uncompleted orders stable. Apply the concrete import on the next restart.
                reply(sender,"已生成 shop.imported.yml："+result.products()+" 项商品；旧配置已备份。停服后将其替换为 shop.yml 再启动。跳过 "+result.skipped().size()+" 项，详情见 migration 记录。");
            }catch(Exception error){reply(sender,"商店导入失败："+error.getMessage());}
        });
    }
    private void reply(org.bukkit.command.CommandSender sender,String text){if(sender instanceof Player p)onPlayer(p,()->p.sendMessage(Component.text(text)));else sender.sendMessage(Component.text(text));}
    private void show(Player player,boolean custom,String backend) {
        try {
            var previous=sessions.get(player.getUniqueId());int balance=previous==null?120:previous.balance;
            var state=new JsonObject();state.addProperty("balance",balance);state.addProperty("status","欢迎，"+player.getName());state.addProperty("research","0.62");
            String html=custom?Files.readString(getDataPath().resolve("menus/assets.html")):demo;
            var manifest=new JsonArray();
            if(custom)for(var entry:getConfig().getMapList("assets")){var asset=new JsonObject();entry.forEach((k,v)->asset.addProperty(k.toString(),v.toString()));manifest.add(asset);}
            open(player,new Document("小镇工坊",html,manifest,state,Set.of("server:buy","server:save","server:slow")),this::demoAction,backend);
            sessions.get(player.getUniqueId()).balance=balance;
        }catch(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"Cannot open UI",error);}
    }
    private void demoAction(Player player,Action action) {
        var s=sessions.get(player.getUniqueId());if(s==null)return;var state=new JsonObject();
        switch(action.name()) {
            case "server:buy"->{if(s.balance>=10){s.balance-=10;state.addProperty("status","购买成功 · 已扣除 10 枚绿宝石");}else state.addProperty("status","余额不足");state.addProperty("balance",s.balance);patch(player,state);}
            case "server:save"->{state.addProperty("status","已保存："+action.form().get("nickname").getAsString());patch(player,state);}
            case "server:slow"->player.getScheduler().runDelayed(this,t->{if(sessions.get(player.getUniqueId())!=s)return;var update=new JsonObject();update.addProperty("status","慢请求已完成 · 本地控件保持响应");patch(player,update);},null,10);
            default->{}
        }
    }
    private JsonObject address(String kind,Session s,long revision){var m=Wire.message(kind);m.addProperty("session",s.id);m.addProperty("revision",revision);return m;}
    private void validateState(JsonObject state){
        if(state.size()>128||state.toString().length()>32768)throw new IllegalArgumentException("UI state too large");
        for(var entry:state.entrySet())if(!entry.getKey().matches("[A-Za-z0-9_-]{1,64}")||!entry.getValue().isJsonPrimitive())throw new IllegalArgumentException("UI state must contain named primitive values");
    }
    private void send(Player player,JsonObject message){if(player.isOnline())for(var frame:Wire.encode(message))player.sendPluginMessage(this,Wire.CHANNEL,frame);}
    void onPlayer(Player player,Runnable action){onPlayer(player,action,()->{});}
    void onPlayer(Player player,Runnable action,Runnable retired){if(!isEnabled()){retired.run();return;}if(Bukkit.isOwnedByCurrentRegion(player))action.run();else player.getScheduler().run(this,t->action.run(),retired);}
    private void requireOwner(Player player){if(!Bukkit.isOwnedByCurrentRegion(player))throw new IllegalStateException("Use the player's Folia entity scheduler");}
    @Override public void onDisable(){mainMenuCleanup.run();if(shop!=null)shop.close();getServer().getServicesManager().unregisterAll(this);getServer().getMessenger().unregisterIncomingPluginChannel(this);getServer().getMessenger().unregisterOutgoingPluginChannel(this);sessions.clear();peers.clear();}
    private static final class Session {
        final String id=UUID.randomUUID().toString();String ticket="",backend="closed";long nextRevision,committed,lastSequence,actionWindow;int balance=120,actions;
        JsonObject state=new JsonObject();Document pending,active,original;BiConsumer<Player,Action> pendingHandler,handler;
        Runnable closed;
        boolean allowAction(){long now=System.nanoTime();if(now-actionWindow>1_000_000_000L){actionWindow=now;actions=0;}return ++actions<=20;}
    }
    private static final class Peer {
        final Wire.Decoder decoder=new Wire.Decoder();Set<String> features=Set.of();long window;int frames;
        boolean allow(){long now=System.nanoTime();if(now-window>1_000_000_000L){window=now;frames=0;}return ++frames<=80;}
    }
}
