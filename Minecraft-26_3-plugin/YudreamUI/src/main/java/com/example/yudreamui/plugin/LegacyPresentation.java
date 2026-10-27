package com.example.yudreamui.plugin;

import com.google.gson.JsonObject;
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
    record Summary(String key,String text,String group,double x,double y) {}
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
            body.append("<main id=\"legacy-body\" class=\"body\">");
            var seen = new HashSet<String>(); int count = 0; var summaries = new LinkedHashMap<String,List<Summary>>();
            for (String key : frontend.getKeys(false)) {
                String text = frontend.getString(key + ".content");
                if (text == null) continue;
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
                    double area=width*height;if(height<12||area<100||area>=best)continue;
                    if(Math.abs(x-frontend.getDouble(panel+".offset.x"))<=width/2+2&&Math.abs(y-frontend.getDouble(panel+".offset.y"))<=height/2+2){best=area;group=panel.replaceFirst("-(?:bg|frame)$","");}
                }
                summaries.computeIfAbsent(group,k->new ArrayList<>()).add(new Summary(binding,text,group,x,y));
            }
            var information=new StringBuilder("<div class=\"grid information\">");
            for(var content:summaries.values()) {
                content.sort(Comparator.comparingDouble(Summary::y).reversed().thenComparingDouble(Summary::x));
                information.append("<mc-card class=\"tile info\">");
                for(var summary:content)information.append("<p bind=\"").append(summary.key).append("\"></p>");
                information.append("</mc-card>");
            }
            information.append("</div>");
            var allowed = new HashSet<String>();
            if(page.equals("town-claim")) {
                body.append("<div class=\"claim-grid\">");
                for(int r=0;r<7;r++)for(int c=0;c<7;c++) {
                    String sourceId="cellgo-"+r+"-"+c,binding="cell-"+r+"-"+c;
                    String marker=resolve.apply("{meta:cellmark_"+r+"_"+c+"}");state.addProperty(binding,Views.plain(marker));
                    String color=marker.contains("2F9E44")?"own":marker.contains("A87616")?"hall":marker.contains("287FA8")?"available":marker.contains("C94B4B")?"occupied":"neutral";
                    body.append("<mc-card class=\"map-cell ").append(color).append("\"><span bind=\"").append(binding).append("\"></span>");
                    for(var control:controls)if(control.sourceId.equals(sourceId)) {
                        String action="server:"+control.id;allowed.add(action);boolean release=control.actions.toString().contains("unclaimat");
                        body.append("<mc-button id=\"").append(control.id).append("\" action=\"").append(action).append("\" aria-label=\"")
                            .append(release?"放弃":"购买").append("区块 ").append(r).append(",").append(c).append("\">").append(release?"放弃":"选取").append("</mc-button>");
                    }
                    body.append("</mc-card>");
                }
                body.append("</div>");
            }
            body.append("<div class=\"grid controls\">");
            for (var control : controls) {
                if(page.equals("town-claim")&&control.sourceId.startsWith("cellgo-"))continue;
                String action = "server:" + control.id; allowed.add(action);
                body.append("<mc-card class=\"tile\"><mc-button id=\"").append(control.id).append("\" action=\"").append(action)
                    .append("\" aria-label=\"").append(Views.escape(control.label)).append("\"")
                    .append(test(control.condition, resolve) ? "" : " disabled=\"disabled\"").append(">")
                    .append(Views.escape(control.label)).append("</mc-button></mc-card>");
            }
            body.append("</div>").append(information).append("</main></mc-panel>");
            String html = Views.page(title, """
                .shell{background-color:#e5e7dc;border-top-color:#f8f5ea;border-left-color:#f8f5ea;border-bottom-color:#647663;border-right-color:#647663;}
                .info{background-color:#f8f5ea;border-color:#b9c4b3;}.info p:first-child{font-size:13px;color:#2d473b;margin-bottom:8px;}
                .controls .tile{background-color:#dce8d8;border-color:#b9c4b3;}.controls .mc-button{background-color:#39755c;border-top-color:#9bc1a6;border-left-color:#9bc1a6;}
                .claim-grid{display:flex;flex-wrap:wrap;gap:3px;margin-bottom:8px;}.map-cell{width:13.6%;padding:3px;margin:0;background-color:#dce8d8;}
                .map-cell.own{background-color:#b9d8b1;}.map-cell.hall{background-color:#e3cc8d;}.map-cell.available{background-color:#b7d5db;}.map-cell.occupied{background-color:#deb8ad;}
                .map-cell span{display:block;text-align:center;min-height:14px;}.map-cell .mc-button{display:inline-block;width:48%;min-width:1px;height:18px;font-size:8px;padding:0;margin-top:2px;}
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
        plugin.open(player, Views.document(title, Views.page(title, ".shell{max-width:440px;height:170px;}", body), state, Set.of("server:submit","server:cancel")), (p, action) -> {
            plugin.clearCloseHandler(p); plugin.close(p);
            if (action.name().equals("server:submit")) submitted.accept(action.form().get("value").getAsString()); else cancelled.run();
        });
        plugin.setCloseHandler(player, cancelled);
        return true;
    }
}
