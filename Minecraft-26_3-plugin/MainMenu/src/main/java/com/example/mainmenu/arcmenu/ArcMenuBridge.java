package com.example.mainmenu.arcmenu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.FeaturePlacement;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.internal.PlaceholderFeature;
import com.example.mainmenu.menu.AdminLayout;
import com.example.mainmenu.menu.DisplayMenus;
import com.example.mainmenu.menu.MenuLayout;
import com.example.mainmenu.util.Schedulers;
import com.fentai.arcmenu.api.ArcMenuApi;
import com.fentai.arcmenu.api.ArcMenuBeforeOpenEvent;
import com.fentai.arcmenu.api.ArcMenuSessionClosedEvent;
import com.fentai.arcmenu.api.ArcMenuSessionOpenedEvent;
import com.fentai.arcmenu.api.ArcMenuStateScope;
import com.fentai.arcmenu.api.ArcMenuTextInputEndReason;
import com.fentai.arcmenu.api.ArcMenuTextInputHandle;
import com.fentai.arcmenu.api.ArcMenuTextInputListener;
import com.fentai.arcmenu.api.ArcMenuTextInputOptions;
import com.fentai.arcmenu.api.ArcMenuTextInputResult;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;

/** Native YAML owns the phone; a private companion layer renders optional registered sidecards. */
public final class ArcMenuBridge implements DisplayMenus, Listener {
    private static final int APPS_PER_PAGE = 12;
    private static final ZoneId DEFAULT_CLOCK_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA);
    private static final Map<String, String> DEFAULT_BINDINGS = Map.of(
            "town", "town:main", "wallet", "wallet:wallet", "music", "concertomenu:menu",
            "notice", "joinannounce:notice", "time", "realtime:clock",
            "playtime", "yudreamplaytime:my-playtime");
    private final MainMenuPlugin plugin;
    private final ArcMenuApi api;
    private final ArcMenuSidecards sidecards;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private volatile Map<String, String> bindings = DEFAULT_BINDINGS;
    private volatile String home = "mainmenu-home";
    private volatile ZoneId clockZone = DEFAULT_CLOCK_ZONE;
    private volatile boolean ready;

    private static final class View {
        int page = 1;
        int appsPage = 1;
        int leftPage = 1;
        int rightPage = 1;
        String manageSide = "left";
        String selected;
        UUID token;
        ScheduledTask refresh;
        ArcMenuTextInputHandle input;
        long inputSequence;
        final Set<String> aliases = new HashSet<>();
        final Set<String> keys = new HashSet<>();
        final Map<String, String> materials = new HashMap<>();
    }

    private ArcMenuBridge(final MainMenuPlugin plugin, final ArcMenuApi api) {
        this.plugin = plugin;
        this.api = api;
        this.sidecards = new ArcMenuSidecards(plugin, api, () -> home);
    }

    public static DisplayMenus create(final MainMenuPlugin plugin) throws IOException {
        final ArcMenuApi api = plugin.getServer().getServicesManager().load(ArcMenuApi.class);
        if (api == null) {
            plugin.getLogger().warning("ArcMenu API 未就绪，使用背包菜单。");
            return null;
        }
        final ArcMenuBridge bridge = new ArcMenuBridge(plugin, api);
        try {
            bridge.install();
        } catch (final IOException e) {
            bridge.sidecards.shutdown();
            throw e;
        }
        bridge.registerRoutes();
        plugin.getServer().getPluginManager().registerEvents(bridge, plugin);
        return bridge;
    }

    private void install() throws IOException {
        final var menus = plugin.getDataFolder().toPath().toAbsolutePath().getParent()
                .resolve("ArcMenu").resolve("menus").resolve("mainmenu");
        MenuTemplates.install(menus, plugin::getResource);
    }

    private record Settings(String home, Map<String, String> bindings, ZoneId clockZone) { }

    private Settings configure() {
        final String configured = plugin.string("menu.arcmenu.home-menu", "mainmenu-home");
        final String configuredHome = configured.matches("[a-z0-9][a-z0-9_-]*") ? configured : "mainmenu-home";
        final ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("menu.arcmenu.bindings");
        final Map<String, String> result = new LinkedHashMap<>(DEFAULT_BINDINGS);
        if (section != null) {
            for (final String alias : section.getKeys(false)) {
                if (!alias.matches("[a-z0-9][a-z0-9-]{0,31}")) {
                    plugin.getLogger().warning("Invalid MainMenu binding alias: " + alias);
                    continue;
                }
                result.put(alias, section.getString(alias, ""));
            }
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.string("menu.arcmenu.clock-zone", DEFAULT_CLOCK_ZONE.getId()));
        } catch (final DateTimeException e) {
            zone = DEFAULT_CLOCK_ZONE;
            plugin.getLogger().warning("主页 clock-zone 无效，使用 Asia/Shanghai。");
        }
        return new Settings(configuredHome, Map.copyOf(result), zone);
    }

    @Override
    public void reload(final CommandSender sender) {
        final Settings settings;
        try {
            install();
            settings = configure();
        } catch (final IOException e) {
            feedback(sender, "<red>主页模板读取失败：" + e.getClass().getSimpleName() + "</red>");
            plugin.getLogger().warning("主页模板安装失败：" + e);
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            final var report = api.menus().reload();
            if (report.isApplied()) {
                home = settings.home();
                bindings = settings.bindings();
                clockZone = settings.clockZone();
                ready = api.menus().catalog().getMenus().stream().anyMatch(menu -> menu.getId().equals(home));
                plugin.getLogger().info("MainMenu ArcMenu YAML 已发布：" + MenuTemplates.IDS.size()
                        + " 页；现有 YAML 文件均已保留。");
                feedback(sender, ready ? "<green>主菜单配置与 ArcMenu YAML 已重载。</green>"
                        : "<red>主页 ID 不存在：" + home + "；请检查 menu.arcmenu.home-menu。</red>");
            } else {
                plugin.getLogger().warning("MainMenu ArcMenu YAML 校验失败；已发布菜单保持不变。");
                report.getErrors().forEach(plugin.getLogger()::warning);
                feedback(sender, "<red>ArcMenu YAML 校验失败；请查看控制台错误，原菜单继续使用。</red>");
            }
        });
    }

    private void feedback(final CommandSender sender, final String message) {
        if (sender == null) {
            return;
        }
        if (sender instanceof Player player) {
            Schedulers.runFor(plugin, player, () -> player.sendMessage(plugin.miniRaw(message)));
        } else {
            sender.sendMessage(plugin.miniRaw(message));
        }
    }

    private View view(final Player player) {
        return views.computeIfAbsent(player.getUniqueId(), ignored -> new View());
    }

    private boolean ours(final String id) {
        return home.equals(id) || MenuTemplates.IDS.contains(id);
    }

    @Override
    public boolean openHome(final Player player) {
        if (!ready || !plugin.getConfig().getBoolean("menu.arcmenu.enabled", true)) {
            return false;
        }
        if (!player.hasPermission("mainmenu.use")) {
            plugin.message(player, "messages.no-permission");
            return true;
        }
        view(player).page = 1;
        view(player).appsPage = 1;
        return open(player, home);
    }

    @Override
    public boolean openManage(final Player player) {
        if (!ready || !plugin.getConfig().getBoolean("menu.arcmenu.enabled", true)) {
            return false;
        }
        if (admin(player)) {
            open(player, "mainmenu-manage");
        }
        return true;
    }

    @Override
    public boolean openEconomy(final Player player) {
        if (!ready || !plugin.getConfig().getBoolean("menu.arcmenu.enabled", true)) {
            return false;
        }
        if (!player.hasPermission("mainmenu.use")) {
            plugin.message(player, "messages.no-permission");
            return true;
        }
        return open(player, "mainmenu-economy");
    }

    private boolean open(final Player player, final String id) {
        publish(player);
        return api.menus().open(plugin, player, id, List.of(), outcome -> {
            switch (outcome.getKind()) {
                case REJECTED, ERROR, TIMED_OUT -> feedback(player,
                        "<red>界面暂时无法打开，请稍后重试。</red>");
                default -> { }
            }
        }).isAccepted();
    }

    private boolean admin(final Player player) {
        if (player.hasPermission("mainmenu.admin")) {
            return true;
        }
        plugin.message(player, "messages.no-permission");
        return false;
    }

    private void route(final String path, final boolean admin, final BiConsumer<Player, List<String>> action) {
        api.registerRoute(plugin, "mainmenu:" + path, (player, args) -> {
            Schedulers.runFor(plugin, player, () -> {
                if (!plugin.getConfig().getBoolean("menu.arcmenu.enabled", true)
                        || !player.hasPermission("mainmenu.use") || (admin && !admin(player))) {
                    return;
                }
                try {
                    action.accept(player, args);
                } catch (final RuntimeException e) {
                    plugin.getLogger().warning("MainMenu route " + path + " failed: " + e);
                    player.sendMessage(plugin.miniRaw("<red>操作未完成，请稍后重试。</red>"));
                }
            });
            return true;
        });
    }

    private void registerRoutes() {
        route("home", false, (player, args) -> openHome(player));
        route("apps", false, (player, args) -> {
            final View state = view(player);
            state.appsPage = Math.clamp((long) state.appsPage + integer(args, 0, 0),
                    1, appPages(visibleApps().size()));
            open(player, "mainmenu-features");
        });
        route("profile", false, (player, args) -> open(player, "mainmenu-profile"));
        route("economy", false, (player, args) -> openEconomy(player));
        route("tools", false, (player, args) -> {
            if (player.hasPermission("mainmenu.admin")) {
                openManage(player);
            } else {
                open(player, "mainmenu-profile");
            }
        });
        route("features", false, (player, args) -> {
            final View state = view(player);
            state.page = Math.clamp((long) state.page + integer(args, 0, 0), 1, plugin.features().pageCount());
            open(player, "mainmenu-features");
        });
        route("feature", false, (player, args) -> activate(player, argument(args, 0), click(args, 1)));
        route("slot", false, (player, args) -> plugin.features()
                .featureAt(integer(args, 0, -1), view(player).page)
                .ifPresent(feature -> activate(player, feature.id(), click(args, 1))));
        route("manage", true, (player, args) -> {
            final View state = view(player);
            final int delta = integer(args, 1, 0);
            if (argument(args, 0).equals("left")) {
                state.manageSide = "left";
                state.leftPage = Math.clamp((long) state.leftPage + delta,
                        1, pages(plugin.features().functionAreaFeatures().size()));
            } else if (argument(args, 0).equals("right")) {
                state.manageSide = "right";
                state.rightPage = Math.clamp((long) state.rightPage + delta,
                        1, pages(plugin.features().fixedFeatures().size()));
            }
            open(player, "mainmenu-manage");
        });
        route("edit", true, (player, args) -> {
            final String id = argument(args, 0);
            if (plugin.features().featureById(id).isPresent()) {
                view(player).selected = id;
                attribute(player);
            }
        });
        route("toggle", true, (player, args) -> withSelected(player, feature -> {
            plugin.features().setHidden(feature.id(), !plugin.features().isHidden(feature.id()));
            attribute(player);
        }));
        route("order", true, (player, args) -> withSelected(player, feature -> {
            final int step = integer(args, 0, 0);
            if (Set.of(-4, -1, 1, 4).contains(step)) {
                final long value = (long) plugin.features().orderOf(feature) + step;
                plugin.features().setOrder(feature.id(), Math.clamp(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
            }
            attribute(player);
        }));
        route("fixed", true, (player, args) -> withSelected(player, feature -> {
            if (plugin.features().isFixed(feature)) {
                plugin.features().makeFunction(feature.id());
            } else {
                plugin.features().makeFixed(feature.id());
            }
            attribute(player);
        }));
        route("position", true, (player, args) -> withSelected(player,
                feature -> open(player, "mainmenu-position")));
        route("pick", true, (player, args) -> withSelected(player, feature -> {
            final int slot = integer(args, 0, -1);
            if (MenuLayout.isFreeSlot(slot) && !(feature instanceof PlaceholderFeature)) {
                plugin.features().setPosition(feature.id(), slot);
            }
            attribute(player);
        }));
        route("attribute", true, (player, args) -> attribute(player));
        route("placeholder-add", true, (player, args) -> {
            final String id = plugin.features().addPlaceholder();
            view(player).manageSide = "left";
            final List<MenuFeature> features = plugin.features().functionAreaFeatures();
            for (int index = 0; index < features.size(); index++) {
                if (features.get(index).id().equals(id)) {
                    view(player).leftPage = index / AdminLayout.PAGE_SIZE + 1;
                    break;
                }
            }
            open(player, "mainmenu-manage");
        });
        route("placeholder-delete", true, (player, args) -> withSelected(player, feature -> {
            plugin.features().removePlaceholder(feature.id());
            view(player).selected = null;
            open(player, "mainmenu-manage");
        }));
        route("custom", true, (player, args) -> open(player, "mainmenu-custom"));
        route("input", true, (player, args) -> requestName(player));
    }

    private void activate(final Player player, final String id, final ClickType click) {
        final MenuFeature feature = plugin.features().featureById(id).orElse(null);
        // Recheck authority and visibility at activation, including direct extension routes.
        if (feature == null || feature instanceof PlaceholderFeature || plugin.features().isHidden(id)
                || plugin.features().placement(id).isEmpty() || !feature.clickable()
                || !feature.clickable(click)) {
            return;
        }
        api.close(player);
        try {
            feature.onClick(player, click);
        } catch (final RuntimeException e) {
            plugin.getLogger().warning("功能 " + id + " 点击处理出错：" + e);
            player.sendMessage(plugin.miniRaw("<red>该功能暂时无法打开。</red>"));
        }
    }

    private void withSelected(final Player player, final java.util.function.Consumer<MenuFeature> action) {
        final String id = view(player).selected;
        if (id == null) {
            open(player, "mainmenu-manage");
            return;
        }
        plugin.features().featureById(id).ifPresentOrElse(action,
                () -> open(player, "mainmenu-manage"));
    }

    private void attribute(final Player player) {
        withSelected(player, feature -> open(player, feature instanceof PlaceholderFeature
                ? "mainmenu-attribute-placeholder" : plugin.features().isFixed(feature)
                ? "mainmenu-attribute-fixed" : "mainmenu-attribute-function"));
    }

    private void requestName(final Player player) {
        final View state = view(player);
        final long sequence = ++state.inputSequence;
        if (state.input != null) {
            state.input.close();
        }
        // Native input is limited to 50 characters on unmodded clients.
        state.input = api.textInput().request(plugin, player, ArcMenuTextInputOptions.builder()
                        .title("自定义功能名称").maxLength(50).build(), new ArcMenuTextInputListener() {
                    @Override
                    public void onComplete(final ArcMenuTextInputResult result) {
                        if (!plugin.isEnabled()) {
                            return;
                        }
                        Schedulers.runFor(plugin, player, () -> {
                            if (views.get(player.getUniqueId()) != state || state.inputSequence != sequence || !player.isOnline()) {
                                return;
                            }
                            state.input = null;
                            if (!admin(player)) {
                                return;
                            }
                            if (result.getReason() == ArcMenuTextInputEndReason.SUBMITTED
                                    && result.getValue() != null && !result.getValue().isBlank()) {
                                final String id = plugin.createCustomFeature(result.getValue().trim());
                                if (id != null) {
                                    state.selected = id;
                                    attribute(player);
                                    return;
                                }
                            }
                            open(player, "mainmenu-manage");
                        });
                    }
                });
    }

    @EventHandler
    public void beforeOpen(final ArcMenuBeforeOpenEvent event) {
        if (ours(event.getMenuId())) {
            publish(event.getPlayer());
        }
    }

    @EventHandler
    public void opened(final ArcMenuSessionOpenedEvent event) {
        if (!ours(event.getSession().getId())) {
            return;
        }
        final Player player = event.getPlayer();
        final View state = view(player);
        Schedulers.cancel(state.refresh);
        state.token = event.getSession().getToken();
        sidecards.open(player, state.token, event.getSession().getId());
        final long period = Math.max(1L, Math.min(3600L,
                plugin.getConfig().getLong("menu.refresh-seconds", 1L))) * 20L;
        state.refresh = Schedulers.runForAtFixedRate(plugin, player, () -> {
            final var current = api.sessions().current(player);
            if (current == null || !current.getToken().equals(state.token) || !ours(current.getId())) {
                Schedulers.cancel(state.refresh);
                state.refresh = null;
                return;
            }
            final Map<String, String> materials = Map.copyOf(state.materials);
            publish(player);
            sidecards.refresh(player);
            if (!materials.equals(state.materials)) {
                // 3.2 refresh updates text/images, so a changed item needs a new document session.
                open(player, current.getId());
            } else {
                api.refresh(player);
            }
        }, period, period);
    }

    @EventHandler
    public void closed(final ArcMenuSessionClosedEvent event) {
        sidecards.close(event.getPlayer(), event.getSession().getToken());
        final View state = views.get(event.getPlayer().getUniqueId());
        if (state != null && event.getSession().getToken().equals(state.token)) {
            Schedulers.cancel(state.refresh);
            state.refresh = null;
            state.token = null;
        }
    }

    @EventHandler
    public void quit(final PlayerQuitEvent event) {
        sidecards.quit(event.getPlayer());
        final View state = views.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            Schedulers.cancel(state.refresh);
            if (state.input != null) {
                state.input.close();
            }
            state.keys.forEach(key -> api.state().remove(event.getPlayer(), ArcMenuStateScope.META, key));
        }
    }

    private void put(final Player player, final View state, final String key, final Object value) {
        final String full = "mm-" + key;
        state.keys.add(full);
        api.state().set(player, ArcMenuStateScope.META, full, String.valueOf(value));
    }

    private void display(final Player player, final View state, final String prefix,
                         final FeatureDisplay data, final boolean available) {
        state.materials.put(prefix, data == null ? "" : data.id() + "|" + data.context() + "|" + data.material());
        put(player, state, prefix + "-available", available);
        put(player, state, prefix + "-badge", data == null ? "" : "◆");
        put(player, state, prefix + "-id", data == null ? "" : data.id());
        put(player, state, prefix + "-name", data == null ? "" : data.name());
        put(player, state, prefix + "-plain-name", data == null ? "" : data.plainName());
        put(player, state, prefix + "-heading", data == null ? "" : compact(data.plainName(), 300));
        put(player, state, prefix + "-short-name", data == null ? "" : compact(data.plainName(), 54));
        put(player, state, prefix + "-summary", data == null || data.plainLines().isEmpty()
                ? "" : compact(data.plainLines().getFirst(), 168));
        put(player, state, prefix + "-material", data == null ? "minecraft:paper" : data.material());
        put(player, state, prefix + "-context", data == null ? "GUI" : data.context());
        put(player, state, prefix + "-clickable", data != null && data.clickable());
        put(player, state, prefix + "-lore", data == null ? "" : String.join("\n", data.lines()));
        put(player, state, prefix + "-tooltip", tooltip(data));
        for (int line = 0; line < 8; line++) {
            put(player, state, prefix + "-line-" + line,
                    data != null && line < data.lines().size() ? data.lines().get(line) : "");
            put(player, state, prefix + "-plain-line-" + line,
                    data != null && line < data.plainLines().size() ? data.plainLines().get(line) : "");
        }
    }

    private void publish(final Player player) {
        final View state = view(player);
        final Map<String, FeatureDisplay> cache = new HashMap<>();
        final java.util.function.Function<MenuFeature, FeatureDisplay> resolve = feature -> {
            if (cache.containsKey(feature.id())) {
                return cache.get(feature.id());
            }
            FeatureDisplay data = null;
            try {
                data = FeatureDisplay.read(feature, player);
            } catch (final RuntimeException e) {
                if (reported.add(feature.id())) {
                    plugin.getLogger().warning("功能 " + feature.id() + " 无法渲染：" + e);
                }
            }
            cache.put(feature.id(), data);
            return data;
        };
        put(player, state, "player", player.getName());
        put(player, state, "player-short", compact(player.getName(), 90));
        final String head = HeadMaterial.of(player.getPlayerProfile());
        put(player, state, "player-material", head);
        state.materials.put("player", head);
        final var balance = plugin.economy().read(player);
        put(player, state, "economy-available", balance.available());
        put(player, state, "economy-balance", balance.balance());
        put(player, state, "economy-short-balance", balance.shortBalance());
        put(player, state, "economy-currency", balance.currency());
        put(player, state, "economy-provider", compact(balance.provider(), 156));
        put(player, state, "economy-status", balance.status());
        final ZonedDateTime now = ZonedDateTime.now(clockZone);
        put(player, state, "clock", CLOCK.format(now));
        put(player, state, "date", DATE.format(now));
        put(player, state, "greeting", now.getHour() < 6 ? "夜深了，旅行者" : now.getHour() < 12
                ? "早上好，旅行者" : now.getHour() < 18 ? "下午好，旅行者" : "晚上好，旅行者");
        final boolean administrator = player.hasPermission("mainmenu.admin");
        state.materials.put("tools", administrator ? "minecraft:writable_book" : "minecraft:clock");
        put(player, state, "tools-label", administrator ? "桌面管理" : "我的时光");
        put(player, state, "tools-material", administrator ? "minecraft:writable_book" : "minecraft:clock");
        put(player, state, "tools-tooltip", administrator ? "&f桌面管理\n&7显示、排序与固定位置" : "&f我的时光\n&7查看自己的在线时长");
        put(player, state, "online", Bukkit.getOnlinePlayers().size());
        put(player, state, "feature-count", plugin.features().placements().stream()
                .filter(p -> !plugin.features().isPlaceholder(p.id())).count());
        put(player, state, "admin-label", player.hasPermission("mainmenu.admin") ? "菜单管理" : "");
        for (final String old : Set.copyOf(state.aliases)) {
            if (!bindings.containsKey(old)) {
                display(player, state, "bind-" + old, null, false);
                state.materials.remove("bind-" + old);
                state.aliases.remove(old);
            }
        }
        state.aliases.addAll(bindings.keySet());
        for (final var binding : bindings.entrySet()) {
            final MenuFeature feature = plugin.features().featureById(binding.getValue()).orElse(null);
            final boolean available = feature != null && !(feature instanceof PlaceholderFeature)
                    && !plugin.features().isHidden(feature.id()) && plugin.features().placement(feature.id()).isPresent();
            display(player, state, "bind-" + binding.getKey(), available ? resolve.apply(feature) : null, available);
        }
        state.page = Math.min(state.page, plugin.features().pageCount());
        put(player, state, "page", state.page);
        put(player, state, "pages", plugin.features().pageCount());
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            final MenuFeature feature = plugin.features().featureAt(slot, state.page).orElse(null);
            final boolean available = feature != null && !(feature instanceof PlaceholderFeature);
            display(player, state, "slot-" + slot, available ? resolve.apply(feature) : null, available);
            if (available && plugin.features().placement(feature.id())
                    .map(p -> p.movedToFunctionArea()).orElse(false)) {
                put(player, state, "slot-" + slot + "-tooltip", tooltip(resolve.apply(feature)) + "\n"
                        + legacy(plugin.string("menu.feature.conflict-hint", "")));
            }
            put(player, state, "slot-" + slot + "-conflict", feature != null
                    && plugin.features().placement(feature.id()).map(p -> p.movedToFunctionArea()).orElse(false)
                    ? legacy(plugin.string("menu.feature.conflict-hint", "")) : "");
        }
        final List<MenuFeature> apps = visibleApps();
        state.appsPage = Math.min(state.appsPage, appPages(apps.size()));
        put(player, state, "apps-page", state.appsPage);
        put(player, state, "apps-pages", appPages(apps.size()));
        put(player, state, "apps-count", apps.size());
        for (int index = 0; index < APPS_PER_PAGE; index++) {
            final int source = (state.appsPage - 1) * APPS_PER_PAGE + index;
            final MenuFeature feature = source < apps.size() ? apps.get(source) : null;
            displayApp(player, state, "app-" + index, feature == null ? null : resolve.apply(feature));
        }
        if (!player.hasPermission("mainmenu.admin")) {
            return;
        }
        final List<MenuFeature> functions = plugin.features().functionAreaFeatures();
        final List<MenuFeature> fixed = plugin.features().fixedFeatures();
        state.leftPage = Math.min(state.leftPage, pages(functions.size()));
        state.rightPage = Math.min(state.rightPage, pages(fixed.size()));
        publishColumn(player, state, "function", functions, state.leftPage, resolve);
        publishColumn(player, state, "fixed", fixed, state.rightPage, resolve);
        final boolean left = state.manageSide.equals("left");
        final List<MenuFeature> managed = left ? functions : fixed;
        final int managedPage = left ? state.leftPage : state.rightPage;
        put(player, state, "manager-side", state.manageSide);
        put(player, state, "manager-page", managedPage);
        put(player, state, "manager-pages", pages(managed.size()));
        put(player, state, "manager-count", managed.size());
        put(player, state, "manager-title", left ? "功能区" : "固定位置");
        put(player, state, "function-tab", (left ? "&#183E35" : "&#747B6B") + "功能区");
        put(player, state, "fixed-tab", (left ? "&#747B6B" : "&#183E35") + "固定位置");
        for (int index = 0; index < AdminLayout.PAGE_SIZE; index++) {
            final int source = (managedPage - 1) * AdminLayout.PAGE_SIZE + index;
            final MenuFeature feature = source < managed.size() ? managed.get(source) : null;
            final String prefix = "manager-" + index;
            displayApp(player, state, prefix, feature == null ? null : resolve.apply(feature));
            put(player, state, prefix + "-state", feature == null ? "" : feature instanceof PlaceholderFeature
                    ? "占位符" : plugin.features().isHidden(feature.id()) ? "&c已隐藏" : "&7显示中");
            if (feature != null) {
                put(player, state, prefix + "-tooltip", tooltip(resolve.apply(feature))
                        + "\n&7插件：" + feature.owner() + "\n&7ID：" + feature.id()
                        + "\n&7状态：" + (feature instanceof PlaceholderFeature ? "占位符"
                        : plugin.features().isHidden(feature.id()) ? "已隐藏" : "显示中")
                        + "\n&7排序值：" + plugin.features().orderOf(feature) + "\n&e点击管理");
            }
        }
        final MenuFeature selected = state.selected == null ? null
                : plugin.features().featureById(state.selected).orElse(null);
        display(player, state, "selected", selected == null ? null : resolve.apply(selected), selected != null);
        if (selected != null) {
            put(player, state, "selected-tooltip", tooltip(resolve.apply(selected))
                    + "\n&7插件：" + selected.owner() + "\n&7ID：" + selected.id());
        }
        put(player, state, "selected-owner", selected == null ? "" : selected.owner());
        put(player, state, "selected-order", selected == null ? 0 : plugin.features().orderOf(selected));
        put(player, state, "selected-visibility", selected != null && plugin.features().isHidden(selected.id())
                ? "已隐藏" : "显示中");
        final Integer position = selected == null ? null : plugin.features().fixedPositionOf(selected);
        put(player, state, "selected-position", position == null ? "未设置" : MenuLayout.describe(position));
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            final int index = slot;
            final String occupant = plugin.features().placements().stream().filter(p -> p.slot() == index)
                    .map(p -> p.id()).findFirst().orElse("");
            put(player, state, "position-" + slot + "-occupant", occupant);
        }
    }

    private String legacy(final String mini) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                .character('&').hexColors().build().serialize(plugin.miniRaw(mini));
    }

    private List<MenuFeature> visibleApps() {
        return plugin.features().placements().stream()
                .sorted(Comparator.comparingInt(FeaturePlacement::page).thenComparingInt(FeaturePlacement::slot))
                .map(placement -> plugin.features().featureById(placement.id()).orElse(null))
                .filter(feature -> feature != null && !(feature instanceof PlaceholderFeature)
                        && !plugin.features().isHidden(feature.id()))
                .toList();
    }

    private void displayApp(final Player player, final View state, final String prefix, final FeatureDisplay data) {
        display(player, state, prefix, data, data != null);
        if (data == null) {
            // ArcMenu 3.2 cannot hide item nodes dynamically; use a quiet, explicit empty app position.
            put(player, state, prefix + "-material", "minecraft:light_gray_stained_glass_pane");
            put(player, state, prefix + "-short-name", "空位");
            put(player, state, prefix + "-tooltip", "&7暂无应用");
        }
    }

    private static int appPages(final int size) {
        return Math.max(1, (size + APPS_PER_PAGE - 1) / APPS_PER_PAGE);
    }

    private void publishColumn(final Player player, final View state, final String name,
                               final List<MenuFeature> features, final int page,
                               final java.util.function.Function<MenuFeature, FeatureDisplay> resolve) {
        put(player, state, name + "-page", page);
        put(player, state, name + "-pages", pages(features.size()));
        put(player, state, name + "-count", features.size());
        for (int index = 0; index < AdminLayout.PAGE_SIZE; index++) {
            final int source = (page - 1) * AdminLayout.PAGE_SIZE + index;
            final MenuFeature feature = source < features.size() ? features.get(source) : null;
            final String prefix = name + "-" + index;
            final FeatureDisplay data = feature == null ? null : resolve.apply(feature);
            display(player, state, prefix, data, feature != null);
            final String visibility = feature == null ? "" : feature instanceof PlaceholderFeature ? "占位符"
                    : plugin.features().isHidden(feature.id()) ? "已隐藏" : "显示中";
            put(player, state, prefix + "-state", visibility);
            if (feature != null) {
                put(player, state, prefix + "-badge", feature instanceof PlaceholderFeature ? "□"
                        : plugin.features().isHidden(feature.id()) ? "&#985D48◆" : "◆");
                put(player, state, prefix + "-tooltip", tooltip(data) + "\n&7状态：" + visibility
                        + "\n&7排序值：" + plugin.features().orderOf(feature) + "\n&e点击管理");
            }
            put(player, state, prefix + "-order", feature == null ? 0 : plugin.features().orderOf(feature));
        }
    }

    private static String tooltip(final FeatureDisplay data) {
        return data == null ? "&7暂无功能" : data.name()
                + (data.lines().isEmpty() ? "" : "\n" + String.join("\n", data.lines()));
    }

    private static int pages(final int size) {
        return Math.max(1, (size + AdminLayout.PAGE_SIZE - 1) / AdminLayout.PAGE_SIZE);
    }

    private static String compact(final String text, final int maxPixels) {
        final StringBuilder result = new StringBuilder();
        int pixels = 0;
        for (final int character : text.codePoints().toArray()) {
            pixels += character > 0x2E80 ? 9 : 6;
            if (pixels > maxPixels) {
                return result.append('…').toString();
            }
            result.appendCodePoint(character);
        }
        return result.toString();
    }

    private static String argument(final List<String> args, final int index) {
        return index < args.size() ? args.get(index) : "";
    }

    private static int integer(final List<String> args, final int index, final int fallback) {
        try {
            return Integer.parseInt(argument(args, index));
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    private static ClickType click(final List<String> args, final int index) {
        return switch (argument(args, index).toLowerCase(Locale.ROOT)) {
            case "right" -> ClickType.RIGHT;
            case "shift-left" -> ClickType.SHIFT_LEFT;
            case "shift-right" -> ClickType.SHIFT_RIGHT;
            case "middle" -> ClickType.MIDDLE;
            default -> ClickType.LEFT;
        };
    }

    @Override
    public void shutdown() {
        ready = false;
        sidecards.shutdown();
        api.unregisterRoutes(plugin);
        HandlerList.unregisterAll(this);
        views.forEach((id, state) -> {
            Schedulers.cancel(state.refresh);
            if (state.input != null) {
                state.input.close();
            }
            final Player player = Bukkit.getPlayer(id);
            if (player != null) {
                final Runnable cleanup = () -> {
                    final var session = api.sessions().current(player);
                    if (session != null && ours(session.getId())) {
                        api.close(player);
                    }
                    state.keys.forEach(key -> api.state().remove(player, ArcMenuStateScope.META, key));
                };
                if (Schedulers.ownedBy(player)) {
                    cleanup.run();
                } else if (plugin.isEnabled()) {
                    Schedulers.runFor(plugin, player, cleanup);
                }
            }
        });
        views.clear();
    }
}
