package com.example.mainmenu.arcmenu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.sidecard.SidecardLayout;
import com.example.mainmenu.sidecard.SidecardService;
import com.example.mainmenu.sidecard.SidecardService.Rendered;
import com.example.mainmenu.sidecard.SidecardSettings;
import com.example.mainmenu.util.Schedulers;
import com.fentai.arcmenu.api.ArcMenuApi;
import com.fentai.arcmenu.api.ArcMenuStateScope;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Adds private visuals and real hitboxes to an existing native document session. */
final class ArcMenuSidecards implements Listener, PluginMessageListener {
    private static final Pattern META = Pattern.compile("\\{meta:([A-Za-z0-9_.:-]+)}");
    private static final String INPUT_CHANNEL = "arcmenu:menu_input";
    private final MainMenuPlugin plugin;
    private final ArcMenuApi api;
    private final Supplier<String> home;
    private final Map<UUID, State> views = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pages = new ConcurrentHashMap<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    private static final class State {
        final UUID token;
        final String menu;
        final Set<String> keys = new HashSet<>();
        List<String> signature = List.of();
        Map<String, Rendered> cards = Map.of();
        List<SidecardLayout.Page> layout = List.of();
        SidecardSettings config;
        SidecardScene scene;
        ScheduledTask tick;
        int page;
        int previousSlots;
        long lastClick;
        State(final UUID token, final String menu, final int page) { this.token = token; this.menu = menu; this.page = page; }
    }

    ArcMenuSidecards(final MainMenuPlugin plugin, final ArcMenuApi api, final Supplier<String> home) {
        this.plugin = plugin; this.api = api; this.home = home;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // ArcMenu 3.2's optional client channel; Bukkit listeners cover unmodded input.
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, INPUT_CHANNEL, this);
    }

    void open(final Player player, final UUID token, final String menu) {
        close(player, null);
        final State state = new State(token, menu, pages.getOrDefault(player.getUniqueId(), 0));
        views.put(player.getUniqueId(), state);
        refresh(player);
    }

    private boolean allowed(final String menu, final SidecardSettings settings) {
        return settings.enabled() && (settings.menus().contains(menu)
                || settings.menus().contains("home") && menu.equals(home.get()));
    }

    private boolean active(final Player player, final State state) {
        final var session = api.sessions().current(player);
        return player.isOnline() && session != null && session.getToken().equals(state.token)
                && session.getId().equals(state.menu) && player.hasPermission("mainmenu.use")
                && plugin.getConfig().getBoolean("menu.arcmenu.enabled", true);
    }

    void refresh(final Player player) {
        final State state = views.get(player.getUniqueId());
        if (state == null || !active(player, state)) return;
        final var config = plugin.sidecards().settings();
        final List<Rendered> fresh = allowed(state.menu, config)
                ? plugin.sidecards().snapshot(player, value -> meta(player, value)) : List.of();
        final List<String> signature = fresh.stream().map(Rendered::structure).toList();
        final boolean changed = state.config != config || !signature.equals(state.signature);
        state.cards = fresh.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Rendered::id, card -> card));
        if (changed) {
            state.signature = signature;
            state.config = config;
            state.layout = SidecardLayout.pack(fresh.stream().map(Rendered::layout).toList(), config.area());
            state.page = Math.max(0, Math.min(state.page, state.layout.size() - 1));
            pages.put(player.getUniqueId(), state.page);
            rebuild(player, state);
        } else if (state.scene != null) state.scene.update(state.cards);
        else if (!fresh.isEmpty()) rebuild(player, state);
        publish(player, state);
        // Reload can enable sidecards while the same phone session is still open.
        if (allowed(state.menu, config) && state.tick == null) {
            state.tick = Schedulers.runForAtFixedRate(plugin, player, () -> tick(player, state), 2, 2);
        } else if (!allowed(state.menu, config) && state.tick != null) {
            Schedulers.cancel(state.tick);
            state.tick = null;
        }
    }

    private void rebuild(final Player player, final State state) {
        if (state.scene != null) state.scene.close();
        state.scene = null;
        if (state.layout.isEmpty()) return;
        final var surface = api.surface(player);
        if (surface == null) return;
        final var canvas = surface.getCanvas();
        // Mouse mode moves the plane closer and raises ppb proportionally; its visual scale stays the same.
        if (canvas.getWidth() != 800 || canvas.getHeight() != 450
                || Math.abs(canvas.getPixelsPerBlock() * canvas.getDesignDistance() - 42.7 * 2) > .001) {
            if (reported.add("canvas:" + state.menu)) plugin.getLogger().warning("侧卡跳过非默认 800×450 / ppb×distance=85.4 页面："
                    + state.menu + " (" + canvas + ')');
            return;
        }
        try {
            state.scene = new SidecardScene(plugin, player, surface, state.config,
                    state.layout.get(state.page), state.cards, state.page, state.layout.size());
        } catch (final RuntimeException e) { report("render", e); }
    }

    private void tick(final Player player, final State state) {
        if (!active(player, state)) { close(player, state.token); return; }
        if (state.scene == null) return;
        try {
            final var surface = api.surface(player);
            if (surface == null) return;
            state.scene.surface(surface);
            final var point = api.pointer(player);
            state.scene.hover(point == null ? null : state.scene.hit(400 + point.getX() / SidecardScene.G,
                    225 - point.getY() / SidecardScene.G));
        } catch (final RuntimeException e) {
            state.scene.close(); state.scene = null; report("tick", e);
        }
    }

    private boolean activate(final Player player, final ClickType click) {
        final State state = views.get(player.getUniqueId());
        if (state == null || state.scene == null || !active(player, state)) return false;
        final var point = api.pointer(player);
        if (point == null) return false;
        final var hit = state.scene.hit(400 + point.getX() / SidecardScene.G, 225 - point.getY() / SidecardScene.G);
        if (hit == null) return false;
        final long now = System.nanoTime();
        if (now - state.lastClick < 150_000_000L) return true;
        state.lastClick = now;
        // Re-evaluate visibility, owner lifetime, permission and dimensions at the moment of activation.
        final List<String> before = state.signature;
        final SidecardScene previousScene = state.scene;
        refresh(player);
        if (!before.equals(state.signature) || state.scene != previousScene) return true;
        if (hit.delta() != 0) {
            state.page = Math.clamp((long) state.page + hit.delta(), 0, state.layout.size() - 1);
            pages.put(player.getUniqueId(), state.page);
            rebuild(player, state); publish(player, state); return true;
        }
        final var card = state.cards.get(hit.id());
        if (card == null) return true;
        try {
            if (card.source() != null && card.source().clickable()) {
                api.close(player);
                card.source().onClick(player, hit.action(), click);
            }
            else for (final String action : SidecardService.actions(card, hit.action(), click)) {
                if (!run(player, action, click)) break;
            }
        } catch (final RuntimeException e) {
            report(card.id() + ":click", e);
            player.sendMessage(plugin.miniRaw("<red>该侧卡操作暂时不可用。</red>"));
        }
        return true;
    }

    private boolean run(final Player player, final String configured, final ClickType click) {
        final String action = plugin.sidecards().expand(configured, player, value -> meta(player, value));
        if (action.indexOf('\n') >= 0 || action.indexOf('\r') >= 0) return false;
        if (action.equals("close")) { api.close(player); return false; }
        final int separator = action.indexOf(": ");
        if (separator < 0) return false;
        final String value = action.substring(separator + 2).trim();
        switch (action.substring(0, separator)) {
            case "route" -> {
                final List<String> words = new ArrayList<>(List.of(value.split("\\s+")));
                final String route = words.removeFirst();
                api.dispatchRoute(player, route.startsWith("mainmenu:") ? route : "mainmenu:" + route, words);
                return false;
            }
            case "feature" -> {
                api.dispatchRoute(player, "mainmenu:feature", List.of(value, click.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
                return false;
            }
            case "menu" -> { api.menus().open(plugin, player, value, List.of(), ignored -> { }); return false; }
            case "player" -> { api.close(player); player.performCommand(value.startsWith("/") ? value.substring(1) : value); return false; }
            case "message" -> player.sendMessage(plugin.miniRaw(value));
            default -> { return false; }
        }
        return true;
    }

    private String meta(final Player player, final String input) {
        return META.matcher(input).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                java.util.Objects.toString(api.state().get(player, ArcMenuStateScope.META, match.group(1)), "--")));
    }

    private void publish(final Player player, final State state) {
        put(player, state, "count", state.cards.size());
        put(player, state, "page", state.layout.isEmpty() ? 0 : state.page + 1);
        put(player, state, "pages", state.layout.size());
        final var placements = state.layout.isEmpty() ? List.<SidecardLayout.Placement>of() : state.layout.get(state.page).cards();
        for (int index = 0; index < Math.max(placements.size(), state.previousSlots); index++) {
            final var card = index < placements.size() ? placements.get(index) : null;
            final String prefix = "slot-" + index + '-';
            put(player, state, prefix + "id", card == null ? "" : card.id());
            put(player, state, prefix + "x", card == null ? 0 : card.x());
            put(player, state, prefix + "y", card == null ? 0 : card.y());
            put(player, state, prefix + "width", card == null ? 0 : card.width());
            put(player, state, prefix + "height", card == null ? 0 : card.height());
        }
        state.previousSlots = placements.size();
    }

    private void put(final Player player, final State state, final String suffix, final Object value) {
        final String key = "mm-sidecards-" + suffix;
        state.keys.add(key); api.state().set(player, ArcMenuStateScope.META, key, String.valueOf(value));
    }

    void close(final Player player, final UUID token) {
        final State state = views.get(player.getUniqueId());
        if (state == null || token != null && !token.equals(state.token)) return;
        views.remove(player.getUniqueId(), state);
        Schedulers.cancel(state.tick);
        if (state.scene != null) state.scene.close();
        state.keys.forEach(key -> api.state().remove(player, ArcMenuStateScope.META, key));
    }

    void quit(final Player player) { close(player, null); pages.remove(player.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        final ClickType click = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK
                ? (event.getPlayer().isSneaking() ? ClickType.SHIFT_LEFT : ClickType.LEFT)
                : event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK
                ? (event.getPlayer().isSneaking() ? ClickType.SHIFT_RIGHT : ClickType.RIGHT) : null;
        if (click != null && activate(event.getPlayer(), click)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void swing(final PlayerArmSwingEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && activate(event.getPlayer(), event.getPlayer().isSneaking()
                ? ClickType.SHIFT_LEFT : ClickType.LEFT)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityInteract(final PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && activate(event.getPlayer(), event.getPlayer().isSneaking()
                ? ClickType.SHIFT_RIGHT : ClickType.RIGHT)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityAttack(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && activate(player, player.isSneaking()
                ? ClickType.SHIFT_LEFT : ClickType.LEFT)) event.setCancelled(true);
    }

    @Override public void onPluginMessageReceived(final String channel, final Player player, final byte[] bytes) {
        if (!channel.equals(INPUT_CHANNEL) || bytes.length != 3 || bytes[0] != 1 || bytes[2] < 0 || bytes[2] > 1) return;
        final ClickType click = switch (bytes[1]) {
            case 0 -> bytes[2] == 1 ? ClickType.SHIFT_LEFT : ClickType.LEFT;
            case 1 -> bytes[2] == 1 ? ClickType.SHIFT_RIGHT : ClickType.RIGHT;
            case 2 -> ClickType.MIDDLE;
            default -> null;
        };
        if (click != null) Schedulers.runFor(plugin, player, () -> activate(player, click));
    }

    private void report(final String id, final Throwable error) {
        if (reported.add(id)) plugin.getLogger().warning("MainMenu sidecard " + id + ": " + error);
    }

    void shutdown() {
        views.forEach((id, state) -> {
            Schedulers.cancel(state.tick);
            if (state.scene != null) state.scene.close();
            final Player player = plugin.getServer().getPlayer(id);
            if (player != null) state.keys.forEach(key -> api.state().remove(player, ArcMenuStateScope.META, key));
        });
        views.clear(); pages.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, INPUT_CHANNEL, this);
    }
}
