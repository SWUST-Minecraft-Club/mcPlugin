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
        var body=new StringBuilder("<mc-panel class=\"shell\">").append(Views.heading("冒险手册"))
            .append("<main class=\"body\"><div class=\"grid\">");
        var plain=PlainTextComponentSerializer.plainText();
        for(int index=home.page*PAGE_SIZE;index<Math.min(features.size(),(home.page+1)*PAGE_SIZE);index++) {
            var feature=features.get(index);String id="feature-"+index;var icon=feature.icon(player);var meta=icon.getItemMeta();
            String name=meta!=null&&meta.hasDisplayName()?plain.serialize(meta.displayName()):feature.id();
            String item=icon.getType().getKey().toString();state.addProperty(id,name);
            state.addProperty("description-"+index,feature.description(player).stream().map(plain::serialize).collect(java.util.stream.Collectors.joining(" · ")));
            body.append("<mc-card class=\"tile\"><mc-slot item=\"").append(Views.escape(item)).append("\"/>");
            button(body,allowed,id,name,item,feature.clickable(ClickType.LEFT),id);
            home.actions.put(id,new Activation(feature,ClickType.LEFT));
            body.append("<p class=\"muted\" bind=\"description-").append(index).append("\"></p>");
            var alternatives=ALTERNATES.entrySet().stream().filter(e->feature.clickable(e.getKey())).sorted(Map.Entry.comparingByKey()).toList();
            if(!alternatives.isEmpty()) {
                body.append("<mc-button id=\"more-").append(index).append("\" action=\"toggle:options-").append(index)
                    .append("\" aria-label=\"").append(Views.escape(name)).append("的更多操作\">更多操作</mc-button><div id=\"options-").append(index).append("\" class=\"hidden\">");
                for(var alternate:alternatives) {
                    String action=id+"-"+alternate.getKey().name().toLowerCase(Locale.ROOT);
                    button(body,allowed,action,name+" · "+alternate.getValue(),item,true,null);
                    home.actions.put(action,new Activation(feature,alternate.getKey()));
                }
                body.append("</div>");
            }
            body.append("</mc-card>");
        }
        body.append("</div></main><footer class=\"row navigation\"><span>第 ").append(home.page+1).append(" / ").append(pages).append(" 页</span>");
        if(home.page>0)button(body,allowed,"previous","上一页","ARROW",true,null);
        if(home.page+1<pages)button(body,allowed,"next","下一页","ARROW",true,null);
        if(player.hasPermission("mainmenu.admin"))button(body,allowed,"manage","桌面管理","COMMAND_BLOCK",true,null);
        body.append("</footer></mc-panel>");
        String html=Views.page("冒险手册",".shell{background-color:#e5e7dc;border-top-color:#f8f5ea;border-left-color:#f8f5ea;}.tile{background-color:#f8f5ea;border-color:#b9c4b3;}.tile .mc-button{background-color:#39755c;}.tile p{margin-top:6px;}.navigation{margin-top:6px;flex-shrink:0;flex-wrap:wrap;}",body.toString());
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
