package com.example.yudreamui.plugin;

import com.example.mainmenu.api.*;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.ServicePriority;

/** Loaded only when MainMenu is present. Business actions stay in its feature registry. */
final class MainMenuIntegration implements MenuPageRenderer, org.bukkit.event.Listener {
    private static final int PAGE_SIZE = 12;
    private static final Map<ClickType,String> ALTERNATES = Map.of(
        ClickType.RIGHT,"右键", ClickType.SHIFT_LEFT,"Shift 左键", ClickType.SHIFT_RIGHT,"Shift 右键", ClickType.MIDDLE,"中键");
    private record Activation(MenuFeature feature, ClickType click) {}
    private static final class Home {
        int page;
        String html;
        final Map<String,Activation> actions = new HashMap<>();
        BiConsumer<Player,YudreamUiApi.Action> handler;
    }
    private final Map<UUID,Home> homes = new ConcurrentHashMap<>();
    private final YudreamUiPlugin plugin;
    private final LegacyPresentation legacy;
    MainMenuIntegration(YudreamUiPlugin plugin, LegacyPresentation legacy) {
        this.plugin = plugin; this.legacy = legacy;
        Bukkit.getServicesManager().register(MenuPageRenderer.class, this, plugin, ServicePriority.High);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        var registry = MainMenuApi.registry();
        registry.register(MenuFeature.builder("yudreamui:shop").owner("YudreamUI").icon(Material.EMERALD)
                .name("<green>系统商店</green>").line("<gray>物资与冒险补给</gray>")
                .onClick((p,c) -> plugin.openShop(p)).build());
        registry.register(MenuFeature.builder("yudreamui:dialogue").owner("YudreamUI").icon(Material.WRITABLE_BOOK)
                .name("<gold>林间向导</gold>").line("<gray>对话与冒险指引</gray>")
                .onClick((p,c) -> plugin.openDialogue(p, "guide")).build());
    }
    public boolean open(Player player,String page,String source,UnaryOperator<String> resolve,Consumer<String> action) {
        return page.equals("mainmenu-home") ? home(player,false) : legacy.open(player,page,source,resolve,action);
    }
    public boolean active(Player player,String page) {
        var home=homes.get(player.getUniqueId());
        return page.equals("mainmenu-home") ? home!=null&&plugin.current(player,home.handler) : legacy.active(player,page);
    }
    public void close(Player player) { homes.remove(player.getUniqueId());legacy.close(player); }
    public boolean input(Player player,String title,String initial,int max,Consumer<String> submit,Runnable cancel) {
        return legacy.input(player,title,initial,max,submit,cancel);
    }
    @Override public boolean openHome(Player player) {return home(player,true);}
    private boolean home(Player player,boolean firstPage) {
        if(!player.hasPermission("mainmenu.use")||!player.hasPermission("yudreamui.use")||!legacy.enabled())return false;
        var registry=MainMenuApi.registry();
        var features=registry.features().stream().filter(f->!f.id().startsWith("placeholder:")&&registry.placement(f.id()).isPresent())
            .sorted(Comparator.comparingInt((MenuFeature f)->registry.placement(f.id()).orElseThrow().page())
                .thenComparingInt(f->registry.placement(f.id()).orElseThrow().slot())).toList();
        var home=homes.computeIfAbsent(player.getUniqueId(),id->new Home());
        if(firstPage)home.page=0;
        int pages=Math.max(1,(features.size()+PAGE_SIZE-1)/PAGE_SIZE);home.page=Math.min(home.page,pages-1);
        if(home.handler==null)home.handler=(p,a)->{
            if(a.name().equals("server:previous")||a.name().equals("server:next")) {
                home.page+=a.name().equals("server:next")?1:-1;home.page=Math.max(0,home.page);home(p,false);return;
            }
            if(a.name().equals("server:manage")) {
                if(!p.hasPermission("mainmenu.admin"))return;
                plugin.close(p);var main=com.example.mainmenu.MainMenuPlugin.instance();if(main!=null)main.admin().openManage(p);return;
            }
            var activation=home.actions.get(a.element());if(activation==null)return;
            var feature=activation.feature;
            if(!p.hasPermission("mainmenu.use")||registry.placement(feature.id()).isEmpty()
                ||registry.features().stream().noneMatch(f->f==feature)||!feature.clickable(activation.click))return;
            plugin.close(p);
            try{feature.onClick(p,activation.click);}catch(RuntimeException error){
                plugin.getLogger().log(java.util.logging.Level.WARNING,"主菜单功能执行失败："+feature.id(),error);
                p.sendMessage(net.kyori.adventure.text.Component.text("该功能暂时无法打开。"));
            }
        };
        home.actions.clear();var state=new JsonObject();var allowed=new HashSet<String>();
        state.addProperty("player-name",player.getName());
        state.addProperty("player-world",player.getWorld().getName());
        var location=player.getLocation();
        state.addProperty("player-position",location.getBlockX()+" / "+location.getBlockY()+" / "+location.getBlockZ());
        state.addProperty("player-level",Integer.toString(player.getLevel()));
        state.addProperty("player-health",String.format(Locale.ROOT,"%.0f",player.getHealth()));
        state.addProperty("player-food",Integer.toString(player.getFoodLevel()));
        var body=new StringBuilder("<mc-panel class=\"shell\">").append(Views.heading("冒险手册"))
            .append("<div class=\"workspace\"><nav class=\"rail\"><p class=\"rail-title\">你的方块生活</p><mc-tab id=\"tab-desk\" class=\"selected\" action=\"tab:desk\">01　总览</mc-tab><mc-tab id=\"tab-notes\" action=\"tab:notes\">02　探索指南</mc-tab><p class=\"rail-note\">从一块原木开始，<br/>把日常变成冒险。</p></nav><main id=\"home-body\" class=\"body\"><div id=\"desk\" class=\"tab-page\"><div class=\"home-columns\"><section class=\"grow\">")
            .append(Views.hero("HELLO, ADVENTURER","今天，想做些什么？","建造一座小镇，准备下一场远行，或与熟悉的人聊聊天。"))
            .append("<div class=\"section-head\"><h2>生活与冒险</h2><span class=\"muted\">").append(features.size()).append(" 项服务 · 即刻前往</span></div><div class=\"grid feature-grid\">");
        var plain=PlainTextComponentSerializer.plainText();
        for(int index=home.page*PAGE_SIZE;index<Math.min(features.size(),(home.page+1)*PAGE_SIZE);index++) {
            var feature=features.get(index);String id="feature-"+index;var icon=feature.icon(player);var meta=icon.getItemMeta();
            String name=meta!=null&&meta.hasDisplayName()?plain.serialize(meta.displayName()):feature.id();
            String item=icon.getType().getKey().toString();state.addProperty(id,name);
            state.addProperty("description-"+index,feature.description(player).stream().map(plain::serialize).collect(java.util.stream.Collectors.joining(" · ")));
            body.append("<mc-card class=\"tile feature\"><div class=\"row\"><mc-slot item=\"").append(Views.escape(item)).append("\"/><div class=\"grow\">");
            button(body,allowed,id,name,item,feature.clickable(ClickType.LEFT),id);
            home.actions.put(id,new Activation(feature,ClickType.LEFT));
            body.append("<p class=\"muted\" bind=\"description-").append(index).append("\"></p></div></div>");
            var alternatives=ALTERNATES.entrySet().stream().filter(e->feature.clickable(e.getKey())).sorted(Map.Entry.comparingByKey()).toList();
            if(!alternatives.isEmpty()) {
                body.append("<mc-button id=\"more-").append(index).append("\" action=\"toggle:options-").append(index)
                    .append("\" class=\"more\" aria-label=\"").append(Views.escape(name)).append("的更多操作\">更多操作　＋</mc-button><div id=\"options-").append(index).append("\" class=\"hidden\">");
                for(var alternate:alternatives) {
                    String action=id+"-"+alternate.getKey().name().toLowerCase(Locale.ROOT);
                    button(body,allowed,action,name+" · "+alternate.getValue(),item,true,null);
                    home.actions.put(action,new Activation(feature,alternate.getKey()));
                }
                body.append("</div>");
            }
            body.append("</mc-card>");
        }
        body.append("</div></section><section class=\"aside\"><mc-card><span class=\"eyebrow\">PLAYER PROFILE</span><div class=\"row\"><mc-slot item=\"minecraft:player_head\"/><div><h2 bind=\"player-name\"></h2><span class=\"muted\">此刻，身处方块世界</span></div></div><div class=\"rule\"></div><div class=\"data-line\"><span>所在世界</span><span bind=\"player-world\"></span></div><div class=\"data-line\"><span>坐标</span><span bind=\"player-position\"></span></div><div class=\"data-line\"><span>经验等级</span><span class=\"gold\" bind=\"player-level\"></span></div><div class=\"data-line\"><span>生命 / 饱食</span><span><span bind=\"player-health\"></span> / <span bind=\"player-food\"></span></span></div></mc-card><mc-card class=\"guide-note\">")
            .append(Views.art("guide","guide-thumb")).append("<span class=\"eyebrow\">旅途小记</span><h3>你的下一站</h3><p class=\"muted\">工具、物资与新邻居，都在这份手册里。出发前记得整理背包。</p></mc-card></section></div></div><div id=\"notes\" class=\"tab-page hidden\"><span class=\"eyebrow\">FIELD NOTES</span><h2>把日子过成自己的样子</h2><div class=\"grid\"><mc-card class=\"tile\"><mc-slot item=\"minecraft:oak_log\"/><h3>安顿下来</h3><p>从小镇入口选择聚居地，规划领土与地块。成员、建筑和科技都由镇务页面管理。</p></mc-card><mc-card class=\"tile\"><mc-slot item=\"minecraft:diamond_pickaxe\"/><h3>准备出发</h3><p>系统商店按用途分类。选择份数后查看完整报价，确认付款后物品进入背包。</p></mc-card><mc-card class=\"tile\"><mc-slot item=\"minecraft:written_book\"/><h3>听听故事</h3><p>林间向导提供分支对话。你的回答会带来新的话题，对话记录可以随时展开。</p></mc-card></div></div></main></div><footer class=\"row status navigation\"><span>第 ").append(home.page+1).append(" / ").append(pages).append(" 页</span>");
        if(home.page>0)button(body,allowed,"previous","上一页","ARROW",true,null);
        if(home.page+1<pages)button(body,allowed,"next","下一页","ARROW",true,null);
        if(player.hasPermission("mainmenu.admin"))button(body,allowed,"manage","桌面管理","COMMAND_BLOCK",true,null);
        body.append("</footer></mc-panel>");
        String html=Views.page("冒险手册","""
            .home-columns{display:flex;align-items:flex-start;}.hero{height:168px;min-height:0;}.hero-art{height:168px;}.feature{width:47%;min-width:190px;padding:11px;}
            .feature .row{align-items:flex-start;}.feature .mc-button{text-align:left;background-color:transparent;border:0;padding:0;height:auto;font-size:16px;}
            .feature .mc-button:hover{color:#d4edb9;}.feature p{margin:5px 0 0;font-size:11px;}.feature .more{font-size:10px;color:#8eaaa0;display:block;margin:8px 0 0 52px;}
            .navigation{gap:14px;flex-wrap:wrap;}.navigation .mc-button{height:27px;font-size:11px;padding:4px 9px;}
            .guide-note{position:relative;min-height:180px;overflow:hidden;padding-right:104px;}.guide-thumb{position:absolute;right:0;bottom:0;width:112px;height:168px;}
            #notes .tile h3{margin-top:14px;}#notes .grid{margin-top:20px;}
            @media(max-width:1000px){.home-columns .aside{display:none;}.feature{min-width:180px;}}
            @media(max-width:900px){.rail{display:none;}}
            @media(max-height:530px){.hero{height:94px;}.hero-art{height:94px;}.hero-copy p{display:none;}.hero-copy h2{font-size:21px;}.feature{padding:9px;}.navigation .mc-button{height:24px;}}
            """,body.toString());
        if(plugin.current(player,home.handler)&&html.equals(home.html))plugin.patch(player,state);
        else{home.html=html;plugin.open(player,Views.document("冒险手册",html,state,allowed),home.handler);}
        return true;
    }
    private static void button(StringBuilder body,Set<String> allowed,String id,String label,String icon,boolean enabled,String binding) {
        allowed.add("server:"+id);body.append("<mc-button id=\"").append(id).append("\" action=\"server:").append(id)
            .append("\" fallback-icon=\"").append(Views.escape(icon)).append("\"").append(enabled?"":" disabled=\"disabled\"").append(">");
        if(binding!=null)body.append("<span bind=\"").append(binding).append("\">").append(Views.escape(label)).append("</span>");
        else body.append(Views.escape(label));
        body.append("</mc-button>");
    }
    @org.bukkit.event.EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){homes.remove(event.getPlayer().getUniqueId());}
    void shutdown(){var registry=MainMenuApi.registryOrNull();if(registry!=null){registry.unregister("yudreamui:shop");registry.unregister("yudreamui:dialogue");}}
}
