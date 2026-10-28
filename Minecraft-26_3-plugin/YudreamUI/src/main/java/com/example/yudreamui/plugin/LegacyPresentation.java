package com.example.yudreamui.plugin;

import com.google.gson.JsonObject;
import com.example.yudreamui.protocol.TerrainSpec;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** A deliberately bounded migration of server-owned Arc pages into responsive semantic views.
 * Never executes Arc financial actions. Unsupported constructs delegate to the existing renderer. */
final class LegacyPresentation {
    private final YudreamUiPlugin plugin;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private static final Pattern COMPARISON = Pattern.compile("^(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
    record Control(String id, String sourceId, String label, String condition, List<String> actions) {}
    record Summary(String key,String text,String group,double x,double y,String source) {}
    static final class View {
        String page, html;
        List<Control> controls;
        UnaryOperator<String> resolve;
        Consumer<String> execute;
        BiConsumer<Player,YudreamUiApi.Action> handler;
    }
    LegacyPresentation(YudreamUiPlugin plugin) { this.plugin = plugin; }
    boolean enabled() {return plugin.getConfig().getBoolean("takeover.enabled",true);}

    boolean open(Player player, String page, String source, UnaryOperator<String> resolve, Consumer<String> execute) {
        if (!enabled() || !player.hasPermission("yudreamui.use")) return false;
        try {
            var yaml = new YamlConfiguration(); yaml.loadFromString(source);
            String permission = yaml.getString("permission", "");
            if (!permission.isBlank() && !player.hasPermission(permission)) return false;
            var frontend = yaml.getConfigurationSection("frontend");
            var backend = yaml.getConfigurationSection("backend");
            if (frontend == null || backend == null) return false;
            var controls = new ArrayList<Control>();
            for (String id : backend.getKeys(false)) {
                var section = backend.getConfigurationSection(id);
                var actions = section.getConfigurationSection("actions");
                if (actions == null) continue;
                var variants = new LinkedHashMap<List<String>,String>();
                for (String key : actions.getKeys(false)) {
                    if (!Set.of("all","left","right","middle","shift-left","shift-right","shift-middle").contains(key)) throw new IllegalArgumentException("click type " + key);
                    var raw = actions.getList(key, List.of());
                    var steps = new ArrayList<String>();
                    for (Object item : raw) {
                        if (!(item instanceof String action)) throw new IllegalArgumentException("nested action");
                        if (action.startsWith("sound:") || action.startsWith("animate:") || action.startsWith("stop-animation:") || action.startsWith("refresh:")) continue;
                        if (!supported(action)) throw new IllegalArgumentException("action " + action.split(":",2)[0]);
                        steps.add(action);
                    }
                    if (!steps.isEmpty()) variants.putIfAbsent(List.copyOf(steps), key);
                }
                String condition = section.getString("condition", "");
                supportedCondition(condition);
                String label = label(frontend, section, id, resolve);
                for (var variant : variants.entrySet()) {
                    String suffix = switch (variant.getValue()) {
                        case "right" -> " · 右键操作"; case "middle" -> " · 中键操作";
                        case "shift-left" -> " · Shift 左键"; case "shift-right" -> " · Shift 右键";
                        case "shift-middle" -> " · Shift 中键"; default -> "";
                    };
                    controls.add(new Control("legacy-" + controls.size(), id, label + suffix, condition, variant.getKey()));
                }
            }
            if (controls.size() > 120) throw new IllegalArgumentException("more than 120 controls");
            String title = Views.plain(resolve.apply(frontend.getString("title.content", page.startsWith("town-") ? "小镇" : "冒险手册")));
            var state = new JsonObject(); var body = new StringBuilder("<mc-panel id=\"shell\" class=\"shell\">").append(Views.heading(title));
            TerrainSpec terrain=page.equals("town-claim")&&plugin.supportsFeature(player,"terrain:local-1")
                ?claimTerrain(resolve.apply("{meta:claim_center}"),player.getWorld().getName(),player.getWorld().getKey().asString()):null;
            body.append("<main id=\"legacy-body\" class=\"body\">");
            if(!page.equals("town-claim"))body.append(Views.hero(page.startsWith("town-")?"TOWN / 镇务手册":"WORKSPACE / 我的手册",title,
                page.startsWith("town-")?"规划、协作与建设，让每一块土地都有自己的故事。":"整理你的功能与偏好，让熟悉的世界更顺手。"));
            var seen = new HashSet<String>(); int count = 0; var summaries = new LinkedHashMap<String,List<Summary>>();
            for (String key : frontend.getKeys(false)) {
                String text = frontend.getString(key + ".content");
                if (text == null) continue;
                if(page.equals("town-claim")&&key.startsWith("legend-terrain-"))continue;
                text = Views.plain(resolve.apply(text));
                if (text.length() < 2 || text.equals(title) || text.matches("[━─·/\\s0-9]+") || !seen.add(text)
                    || key.startsWith("close") || key.startsWith("main-menu") || key.endsWith("-t") || key.contains("-no-") || key.startsWith("cellmark-") || key.startsWith("bal-")) continue;
                if (count == 48) break;
                String binding="summary"+count++;state.addProperty(binding,text);
                double x=frontend.getDouble(key+".offset.x"),y=frontend.getDouble(key+".offset.y");
                String group="overview";double best=Double.MAX_VALUE;
                for(String panel:frontend.getKeys(false)) {
                    String type=frontend.getString(panel+".type","");if(!type.equals("rectangle")&&!type.equals("frame"))continue;
                    double width=frontend.getDouble(panel+".width"),height=frontend.getDouble(panel+".height");
                    double area=width*height;if(height<5||area<100||area>=best)continue;
                    if(Math.abs(x-frontend.getDouble(panel+".offset.x"))<=width/2+2&&Math.abs(y-frontend.getDouble(panel+".offset.y"))<=height/2+2){best=area;group=panel.replaceFirst("-(?:bg|frame)$","");}
                }
                if(key.startsWith("progress-t-"))group="steps";
                if(key.equals("hint"))group="caption";
                summaries.computeIfAbsent(group,k->new ArrayList<>()).add(new Summary(binding,text,group,x,y,key));
            }
            var information=new StringBuilder("<div class=\"section-head\"><h2>当前概况</h2><span class=\"muted\">查看状态与详细说明</span></div><div class=\"grid information\">");
            if(summaries.containsKey("steps")) {
                body.append("<div class=\"town-steps\">");
                summaries.remove("steps").stream().sorted(Comparator.comparingDouble(Summary::x)).forEach(s->body.append("<div bind=\"").append(s.key).append("\"></div>"));
                body.append("</div>");
            }
            var captions=summaries.remove("caption");
            for(var content:summaries.values().stream().sorted(Comparator.comparingInt((List<Summary> c)->c.size()).reversed()).toList()) {
                content.sort(Comparator.comparingDouble(Summary::y).reversed().thenComparingDouble(Summary::x));
                information.append("<mc-card class=\"tile info\"><span class=\"info-mark\">◇</span>");
                for(var summary:content) {
                    var value=summary.source.startsWith("i-")?content.stream().filter(s->s.source.equals("v-"+summary.source.substring(2))).findFirst():Optional.<Summary>empty();
                    if(value.isPresent())information.append("<div class=\"data-line\"><span class=\"muted\" bind=\"").append(summary.key).append("\"></span><span bind=\"").append(value.get().key).append("\"></span></div>");
                    else if(!summary.source.startsWith("v-")||content.stream().noneMatch(s->s.source.equals("i-"+summary.source.substring(2))))information.append("<p bind=\"").append(summary.key).append("\"></p>");
                }
                information.append("</mc-card>");
            }
            information.append("</div>");
            if(captions!=null)for(var caption:captions)information.append("<p class=\"muted context-note\" bind=\"").append(caption.key).append("\"></p>");
            var allowed = new HashSet<String>();
            if(page.equals("town-claim")) {
                body.append("<div class=\"claim-workspace\"><section class=\"claim-cartography\"><div class=\"section-head\"><h2>").append(terrain==null?"领地状态":"本地地形").append("</h2><span class=\"muted\">N ↑ · 7 × 7 区块</span></div><div id=\"claim-board\" class=\"claim-board\">");
                if(terrain!=null)body.append("<mc-terrain id=\"claim-terrain\" dimension=\"").append(Views.escape(terrain.dimension())).append("\" chunk-x=\"").append(terrain.chunkX()).append("\" chunk-z=\"").append(terrain.chunkZ()).append("\" chunks=\"7\"/>");
                body.append("<div class=\"claim-grid\">");
                for(int r=0;r<7;r++)for(int c=0;c<7;c++) {
                    String sourceId="cellgo-"+r+"-"+c,binding="cell-"+r+"-"+c;
                    String marker=resolve.apply("{meta:cellmark_"+r+"_"+c+"}");state.addProperty(binding,Views.plain(marker));
                    String color=marker.contains("2F9E44")?"own":marker.contains("A87616")?"hall":marker.contains("287FA8")?"available":marker.contains("C94B4B")?"occupied":"neutral";
                    body.append("<div class=\"map-cell ").append(color).append(r==3&&c==3?" map-center":"").append("\"><span bind=\"").append(binding).append("\"></span><div class=\"cell-actions\">");
                    for(var control:controls)if(control.sourceId.equals(sourceId)) {
                        String action="server:"+control.id;allowed.add(action);boolean release=control.actions.toString().contains("unclaimat");
                        body.append("<mc-button id=\"").append(control.id).append("\" action=\"").append(action).append("\" aria-label=\"")
                            .append(release?"放弃":"购买").append("区块 ").append(r).append(",").append(c).append("\">").append(release?"放弃":"选取").append("</mc-button>");
                    }
                    body.append("</div></div>");
                }
                body.append("</div></div><p class=\"muted map-note\">").append(terrain==null?"当前使用领地状态视图。":"逐方块表面地形 · 白点是你<br/>棋盘格为未加载区块；地形由本地缓存刷新。").append("</p></section><section class=\"claim-sidebar\"><div class=\"map-legend\"><span class=\"accent\">■ 本镇</span><span class=\"gold\">★ 政厅</span><span>□ 可选</span><span>■ 他镇</span></div>").append(information);
            }
            if(!page.equals("town-claim"))body.append("<div class=\"legacy-columns\"><section class=\"legacy-information\">").append(information).append("</section><section class=\"legacy-actions\">");
            body.append("<div class=\"section-head\"><h2>可用操作</h2><span class=\"muted\">由你的身份与当前状态决定</span></div><div class=\"grid controls\">");
            for (var control : controls) {
                if(page.equals("town-claim")&&control.sourceId.startsWith("cellgo-"))continue;
                String action = "server:" + control.id; allowed.add(action);
                body.append("<mc-card class=\"tile\"><mc-button id=\"").append(control.id).append("\" action=\"").append(action)
                    .append("\" aria-label=\"").append(Views.escape(control.label)).append("\"")
                    .append(test(control.condition, resolve) ? "" : " disabled=\"disabled\"").append(">")
                    .append(Views.escape(control.label)).append("</mc-button></mc-card>");
            }
            body.append("</div></section></div></main><footer class=\"status\">方块生活 / ").append(Views.escape(title)).append("</footer></mc-panel>");
            String html = Views.page(title, """
                .hero{height:120px;min-height:0;}.hero-copy{width:52%;padding:15px 20px;}.hero-art{width:48%;}.information{align-items:flex-start;}.info{position:relative;min-width:200px;padding:14px 18px;border-left:2px #87bfa4;}.info p{font-size:13px;line-height:1.7;margin-bottom:5px;}.info p:first-of-type{font-size:18px;color:#e5d9b7;margin-bottom:10px;}.info-mark{position:absolute;right:12px;top:10px;color:#6e9b88;font-size:22px;}
                .town-steps{display:flex;gap:8px;margin-top:12px;}.town-steps div{flex:1;padding:10px 12px;border-bottom:2px #87bfa4;background-color:#223b3e;font-size:12px;}.context-note{margin-top:10px;}.info .data-line span:last-child{text-align:right;}
                .legacy-columns{display:flex;gap:20px;align-items:flex-start;}.legacy-information{flex:1;min-width:0;}.legacy-actions{width:32%;flex-shrink:0;}.legacy-actions .controls .tile{width:100%;}.legacy-actions .section-head .muted{display:none;}
                .controls .tile{padding:0;min-width:160px;width:23%;flex-grow:1;background-color:transparent;border:0;}.controls .mc-button{width:100%;height:auto;min-height:38px;text-align:left;}
                .claim-workspace{display:flex;gap:24px;align-items:flex-start;}.claim-cartography{width:448px;flex-shrink:0;}.claim-cartography .section-head{margin-top:0;}.claim-sidebar{flex:1;min-width:0;}.claim-sidebar .section-head .muted{display:none;}.claim-sidebar .controls .tile{width:47%;min-width:90px;}.claim-sidebar .info{width:100%;min-width:0;}
                .map-legend{display:flex;flex-wrap:wrap;gap:15px;font-size:12px;margin:0 0 12px;color:#aac4cb;}.claim-board{position:relative;width:448px;height:448px;background-color:#29383f;}.mc-terrain{display:block;position:absolute;top:0;left:0;width:100%;height:100%;}
                .claim-grid{position:absolute;left:0;top:0;width:100%;height:100%;display:flex;flex-wrap:wrap;gap:0;}.map-cell{position:relative;width:14.285714%;height:14.285714%;padding:0;margin:0;background-color:transparent;border:1px #c4d7c048;}
                .map-cell.own{border-color:#69cd85;}.map-cell.hall{border-color:#edcb70;}.map-cell.available{border-color:#6ed4ec;}.map-cell.occupied{border-color:#e98080;}.map-cell.map-center{border-color:#fff0b5;border-width:2px;}
                .map-cell span{position:absolute;top:1px;right:2px;text-align:center;background-color:#10272bb0;padding:0 3px;font-size:12px;color:#d1ddda;}.map-cell.own span{color:#96e8a8;}.map-cell.hall span{color:#ffe189;}.map-cell.available span{color:#86def2;}.map-cell.occupied span{color:#f0a2a2;}
                .cell-actions{position:absolute;bottom:1px;left:0;width:100%;display:flex;gap:1px;}.map-cell .mc-button{width:49%;min-width:1px;height:18px;font-size:9px;padding:1px 0;background-color:#10272bb0;border:0;}.map-cell .mc-button:hover{background-color:#aacfb1;color:#15342b;}.map-note{margin-top:10px;line-height:1.6;}
                @media(max-width:1000px){.claim-cartography{width:350px;}.claim-board{width:350px;height:350px;}.claim-workspace{gap:16px;}}
                @media(max-width:760px){.hero{max-height:130px;}.info{min-width:180px;}.claim-cartography{width:280px;}.claim-board{width:280px;height:280px;}.claim-workspace{gap:12px;}}
                @media(max-width:620px){.legacy-columns{display:block;}.legacy-actions{width:100%;}.town-steps{flex-wrap:wrap;}.town-steps div{min-width:40%;}}
                @media(max-width:620px){.claim-workspace{display:block;}.claim-cartography{margin:0 auto;}.claim-sidebar{margin-top:16px;}}
                @media(max-height:530px){.hero{display:none;}.town-steps{margin-top:0;}.claim-cartography{width:280px;}.claim-board{width:280px;height:280px;}.map-cell span{font-size:9px;}.map-cell .mc-button{height:14px;font-size:8px;padding:0;}.map-legend{margin:0 0 8px;}.section-head{margin-top:4px;}.map-note{font-size:10px;margin-top:5px;}}
                """, body.toString());
            var view = views.get(player.getUniqueId());
            boolean same = view != null && page.equals(view.page) && plugin.current(player, view.handler);
            if (!same) {
                view = new View(); view.page = page;
                final View target = view;
                view.handler = (p, action) -> {
                    var control = target.controls.stream().filter(c -> c.id.equals(action.element())).findFirst().orElseThrow();
                    if (!test(control.condition, target.resolve)) return;
                    // Resolve again at activation: balances, selection and authority can change after rendering.
                    for (String step : control.actions) {
                        String resolved=target.resolve.apply(step);
                        if(resolved.startsWith("tell:"))p.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(resolved.substring(5).strip()));
                        else target.execute.accept(resolved);
                    }
                };
                views.put(player.getUniqueId(), view);
            }
            view.controls = List.copyOf(controls); view.resolve = resolve; view.execute = execute;
            if (same && html.equals(view.html)) plugin.patch(player, state);
            else { view.html = html; plugin.open(player, Views.document(title, html, state, allowed), view.handler); }
            return true;
        } catch (Exception error) {
            if (reported.add(page + ":" + error.getMessage())) plugin.getLogger().warning("菜单 " + page + " 保留原渲染器：" + error.getMessage());
            return false;
        }
    }
    static TerrainSpec claimTerrain(String center,String world,String dimension){
        var match=Pattern.compile("^(.*) · (-?\\d+), (-?\\d+)$").matcher(center);
        if(!match.matches()||!match.group(1).equals(world))return null;
        try{return new TerrainSpec(dimension,Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3)),7);}
        catch(IllegalArgumentException invalid){return null;}
    }
    private static boolean supported(String action) {
        return action.equals("close") || action.equals("close:") || action.startsWith("command: town ")
                || action.equals("command: town") || action.startsWith("open: mainmenu:") || action.startsWith("tell:");
    }
    static void supportedCondition(String condition) {
        if (condition.isBlank()) return;
        for (String clause : condition.split("&&", -1)) if (!COMPARISON.matcher(clause.strip()).matches() || clause.contains("||") || clause.contains("("))
            throw new IllegalArgumentException("unsupported condition");
    }
    static boolean test(String condition, UnaryOperator<String> resolve) {
        if (condition.isBlank()) return true;
        for (String clause : resolve.apply(condition).split("&&")) {
            var m = COMPARISON.matcher(clause.strip()); if (!m.matches()) return false;
            String a = unquote(m.group(1)), b = unquote(m.group(3));
            if (a.contains("{meta:") || b.contains("{meta:") || a.contains("%") || b.contains("%")) return false;
            int cmp;
            try { cmp = new BigDecimal(a).compareTo(new BigDecimal(b)); } catch (NumberFormatException ignored) { cmp = a.compareToIgnoreCase(b); }
            if (!switch (m.group(2)) { case "==" -> cmp == 0; case "!=" -> cmp != 0; case ">=" -> cmp >= 0; case "<=" -> cmp <= 0; case ">" -> cmp > 0; default -> cmp < 0; }) return false;
        }
        return true;
    }
    private static String unquote(String text) { return text.strip().replaceAll("^['\"]|['\"]$", ""); }
    private static String label(ConfigurationSection frontend, ConfigurationSection region, String id, UnaryOperator<String> resolve) {
        for (String line : region.getStringList("tooltip")) {
            String label = Views.plain(resolve.apply(line)).lines().findFirst().orElse(""); if (label.length() > 1 && !label.matches("[━─\\s]+")) return label;
        }
        double x = region.getDouble("x"), y = region.getDouble("y");
        String result = id; double best = Double.MAX_VALUE;
        for (String key : frontend.getKeys(false)) {
            String raw = frontend.getString(key + ".content"); if (raw == null) continue;
            String text = Views.plain(resolve.apply(raw)); if (text.isBlank()) continue;
            double distance = Math.pow(x - frontend.getDouble(key + ".offset.x"), 2) + Math.pow(y - frontend.getDouble(key + ".offset.y"), 2);
            if (distance < best) { best = distance; result = text; }
        }
        return result;
    }
    boolean active(Player player, String page) {
        var view = views.get(player.getUniqueId()); return view != null && Objects.equals(page, view.page) && plugin.current(player, view.handler);
    }
    void close(Player player) { views.remove(player.getUniqueId()); plugin.close(player); }
    void forget(UUID player) { views.remove(player); }
    boolean input(Player player, String title, String initial, int maximum, Consumer<String> submitted, Runnable cancelled) {
        if(!enabled()||!player.hasPermission("yudreamui.use"))return false;
        var state = new JsonObject(); state.addProperty("prompt", title);
        String body = "<mc-panel class=\"shell\"><h1 bind=\"prompt\"></h1><main class=\"body\"><mc-input id=\"value\" aria-label=\"输入内容\" maxlength=\"" + maximum + "\" value=\"" + Views.escape(initial)
            + "\"/><mc-button id=\"submit\" action=\"server:submit\" input-ids=\"value\" aria-label=\"保存输入\">保存输入</mc-button><mc-button id=\"cancel\" action=\"server:cancel\" aria-label=\"取消并返回\">取消并返回</mc-button></main></mc-panel>";
        plugin.open(player, Views.document(title, Views.page(title, ".shell{max-width:600px;height:250px;padding:24px;}.body{padding:20px 0;}.mc-button{margin:18px 12px 0 0;}", body), state, Set.of("server:submit","server:cancel")), (p, action) -> {
            plugin.clearCloseHandler(p); plugin.close(p);
            if (action.name().equals("server:submit")) submitted.accept(action.form().get("value").getAsString()); else cancelled.run();
        });
        plugin.setCloseHandler(player, cancelled);
        return true;
    }
}
