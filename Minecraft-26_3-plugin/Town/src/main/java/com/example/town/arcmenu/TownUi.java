package com.example.town.arcmenu;

import com.example.town.TownConfig;
import com.example.town.TownPlugin;
import com.example.town.model.ChunkKey;
import com.example.town.model.Role;
import com.example.town.model.TownSnapshotView;
import com.example.town.model.TownState;
import com.example.town.service.TownService;
import com.example.town.util.Threads;
import com.fentai.arcmenu.api.ArcMenuApi;
import com.fentai.arcmenu.api.ArcMenuReloadReport;
import com.fentai.arcmenu.api.ArcMenuStateScope;
import com.fentai.arcmenu.api.ArcMenuTextInputEndReason;
import com.fentai.arcmenu.api.ArcMenuTextInputHandle;
import com.fentai.arcmenu.api.ArcMenuTextInputOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 在打开每页前写齐该页 META，并统一管理导航、确认和会话刷新。 */
public final class TownUi {

    public static final int MEMBER_ROWS = 8;
    public static final int INVITE_ROWS = 8;
    public static final int BUFF_ROWS = 6;
    /** 圈地地图边长（7×7 区块，44 单位格在 70% 缩放下仍可读）。 */
    public static final int CLAIM_GRID = 7;

    private final TownPlugin plugin;
    private final ArcMenuApi api;
    private final Map<UUID, String> current = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> memberPage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> invitePage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> buffPage = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkKey> claimCenter = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> selectedMember = new ConcurrentHashMap<>();
    private final Map<UUID, ConfirmSession> confirmations = new ConcurrentHashMap<>();
    private final Map<UUID, ArcMenuTextInputHandle> textInputs = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkKey> chunkPreviews = new ConcurrentHashMap<>();
    private final Set<UUID> balanceLoading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> balanceRetryAt = new ConcurrentHashMap<>();
    private final AtomicBoolean assetsLoading = new AtomicBoolean();
    private TownMapRenderer mapRenderer;
    private final TownOperations operations;

    private record ConfirmSession(String action, String returnScreen) {
    }

    private TownUi(final TownPlugin plugin, final ArcMenuApi api) {
        this.plugin = plugin;
        this.api = api;
        operations = new TownOperations(plugin,this);
    }

    public static TownUi create(final TownPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("ArcMenu") == null) {
            return null;
        }
        try {
            final ArcMenuApi api = plugin.getServer().getServicesManager().load(ArcMenuApi.class);
            if (api == null) {
                plugin.getLogger().warning("ArcMenu 已安装但 API 未注册，图形界面降级为指令。");
                return null;
            }
            plugin.getLogger().info("已接入 ArcMenu（apiVersion=" + api.apiVersion() + "）。");
            return new TownUi(plugin, api);
        } catch (final LinkageError e) {
            plugin.getLogger().warning("ArcMenu API 类不可用，图形界面禁用：" + e.getClass().getSimpleName());
            return null;
        }
    }

    public void installMenus() {
        final Path dir = plugin.getDataFolder().toPath().getParent()
                .resolve("ArcMenu").resolve("menus");
        mapRenderer = new TownMapRenderer(plugin, dir.getParent());
        mapRenderer.ensurePlaceholder();
        mapRenderer.warmSpawn();
        final boolean needResourceRebuild;
        try {
            final List<TownMenus.Written> written = TownMenus.writeAll(plugin.config(), dir);
            plugin.getLogger().info("已生成 " + written.size() + " 个小镇菜单文件到 " + dir);
            mergeAnimations(dir.getParent());
            needResourceRebuild = mapRenderer.hasPendingIndex();
        } catch (final IOException | RuntimeException e) {
            plugin.getLogger().warning("小镇菜单写入失败：" + e.getMessage());
            return;
        }
        Threads.global(plugin, () -> {
            if (needResourceRebuild) {
                plugin.getLogger().info("检测到小地图图片变化，先重建 ArcMenu 资源包。");
                mapRenderer.rebuildNow();
            }
            final ArcMenuReloadReport report = api.menus().reload();
            if (report.isApplied()) {
                plugin.getLogger().info("ArcMenu 菜单已重载：共 " + report.getMenuCount() + " 个，无错误。");
            } else {
                plugin.getLogger().warning("ArcMenu 菜单重载失败（" + report.getErrors().size() + " 个错误）：");
                report.getErrors().stream().limit(10).forEach(line -> plugin.getLogger().warning("  · " + line));
            }
        });
    }

    /**
     * 增量合并 Town 的过渡与标题微动画到 ArcMenu 全局 animations.yml；
     * 只添加 town- 前缀键，不触碰默认菜单已有的绑定。首次写入前备份原文件。
     */
    private void mergeAnimations(final Path arcMenuDir) throws IOException {
        final Path file = arcMenuDir.resolve("animations.yml");
        final org.bukkit.configuration.file.YamlConfiguration yml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
        boolean changed = false;
        if (!yml.contains("transitions.town-fade")) {
            yml.set("transitions.town-fade.enter.duration", 6);
            yml.set("transitions.town-fade.enter.easing", "ease-out");
            yml.set("transitions.town-fade.enter.scale.x", 0.96);
            yml.set("transitions.town-fade.enter.scale.y", 0.96);
            yml.set("transitions.town-fade.exit.duration", 5);
            yml.set("transitions.town-fade.exit.easing", "ease-in");
            yml.set("transitions.town-fade.exit.scale.x", 0.97);
            yml.set("transitions.town-fade.exit.scale.y", 0.97);
            yml.set("transitions.town-fade.switch.duration", 6);
            yml.set("transitions.town-fade.switch.easing", "ease-out");
            yml.set("transitions.town-fade.switch.offset.x", 12);
            yml.set("transitions.town-fade.switch.offset.y", 0);
            yml.set("transitions.town-fade.switch.offset.z", 0);
            changed = true;
        }
        if (!yml.contains("tracks.town-title-pulse")) {
            yml.set("tracks.town-title-pulse.target", "title");
            yml.set("tracks.town-title-pulse.property", "opacity");
            yml.set("tracks.town-title-pulse.duration", 30);
            yml.set("tracks.town-title-pulse.easing", "ease-in-out");
            yml.set("tracks.town-title-pulse.loop", "ping-pong");
            yml.set("tracks.town-title-pulse.trigger", "open");
            final List<Map<String, Object>> keyframes = new ArrayList<>();
            keyframes.add(Map.of("at", 0.0, "value", 150));
            keyframes.add(Map.of("at", 1.0, "value", 255));
            yml.set("tracks.town-title-pulse.keyframes", keyframes);
            changed = true;
        }
        for (final String id : TownMenus.all(plugin.config()).keySet()) {
            final String path = "menus." + id;
            if (!"town-fade".equals(yml.getString(path + ".transition"))) {
                yml.set(path + ".transition", "town-fade");
                yml.set(path + ".tracks", List.of("town-title-pulse"));
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        if (!Files.exists(arcMenuDir.resolve("animations.town-backup.yml"))) {
            if (Files.exists(file)) {
                Files.copy(file, arcMenuDir.resolve("animations.town-backup.yml"));
            }
        }
        Files.writeString(file, yml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
        plugin.getLogger().info("已合并小镇动画到 ArcMenu animations.yml（原文件备份为 animations.town-backup.yml）。");
    }

    // ------------------------------------------------------------------ 打开页面

    public void openHome(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        writeCommon(player, view);
        final String id;
        if (view == null) {
            id = plugin.service().inviteOf(player.getUniqueId()) == null
                    ? TownMenus.HOME_NONE : TownMenus.HOME_INVITED;
        } else {
            id = view.active() ? TownMenus.HOME_ACTIVE : TownMenus.HOME_PENDING;
        }
        markAndOpen(player, id);
    }

    public void openCreate(final Player player) {
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        markAndOpen(player, TownMenus.CREATE);
    }

    public void openMembers(final Player player, final int page) {
        memberPage.put(player.getUniqueId(), Math.max(1, page));
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeMembers(player);
        markAndOpen(player, TownMenus.MEMBERS);
    }

    public void openMember(final Player player, final String targetName) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        UUID found = null;
        if (view != null) {
            for (final UUID member : view.members().keySet()) {
                final String name = nameOf(member);
                if (name.equalsIgnoreCase(targetName)) {
                    found = member;
                    break;
                }
            }
        }
        if (found == null) {
            plugin.messages().send(player, "target-not-member");
            openMembers(player, memberPage.getOrDefault(player.getUniqueId(), 1));
            return;
        }
        selectedMember.put(player.getUniqueId(), found);
        writeCommon(player, view);
        writeSelected(player);
        markAndOpen(player, TownMenus.MEMBER_DETAIL);
    }

    public void openInvitePicker(final Player player, final int page) {
        invitePage.put(player.getUniqueId(), Math.max(1, page));
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeCandidates(player);
        markAndOpen(player, TownMenus.INVITE_PICKER);
    }

    public void openClaim(final Player player) {
        openClaimAt(player, ChunkKey.of(player.getLocation()));
    }

    public void openClaimAt(final Player player, final ChunkKey center) {
        claimCenter.put(player.getUniqueId(), center);
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeClaim(player);
        markAndOpen(player, TownMenus.CLAIM);
    }

    public void openBuffs(final Player player, final int page) {
        buffPage.put(player.getUniqueId(), Math.max(1, page));
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeBuffs(player);
        markAndOpen(player, TownMenus.BUFFS);
    }

    public void openConfirm(final Player player, final String action) {
        final String returnScreen = current.getOrDefault(player.getUniqueId(), homeScreen(player));
        confirmations.put(player.getUniqueId(), new ConfirmSession(action, returnScreen));
        writeCommon(player, plugin.service().townOf(player.getUniqueId()));
        writeConfirm(player, action);
        markAndOpen(player, TownMenus.CONFIRM);
    }

    public void cancelConfirm(final Player player) {
        final ConfirmSession session = confirmations.remove(player.getUniqueId());
        if (session == null) {
            openHome(player);
            return;
        }
        reopen(player, session.returnScreen());
    }

    /** 指令结果统一在这里决定停留、刷新或回跳。 */
    public void afterResult(final Player player, final TownService.Result result) {
        if (!player.isOnline()) {
            return;
        }
        Threads.forPlayer(plugin, player, () -> {
            final String screen = current.get(player.getUniqueId());
            if (screen == null) {
                return;
            }
            if (TownMenus.CONFIRM.equals(screen)) {
                final ConfirmSession session = confirmations.remove(player.getUniqueId());
                if (session == null) {
                    openHome(player);
                    return;
                }
                final String head = session.action().split(" ")[0];
                switch (head) {
                    case "found", "disband", "leave" -> openHome(player);
                    case "claimmap", "unclaimat", "claimpos", "unclaimpos" -> openClaimAt(player,
                            claimCenter.getOrDefault(player.getUniqueId(), ChunkKey.of(player.getLocation())));
                    case "buff" -> openBuffs(player, buffPage.getOrDefault(player.getUniqueId(), 1));
                    case "kick", "transfer" -> openMembers(player,
                            memberPage.getOrDefault(player.getUniqueId(), 1));
                    default -> reopen(player, session.returnScreen());
                }
                return;
            }
            if (TownMenus.HOME_INVITED.equals(screen)
                    || (result.ok() && TownMenus.CREATE.equals(screen))) {
                openHome(player);
                return;
            }
            refresh(player);
        });
    }

    private void reopen(final Player player, final String screen) {
        switch (screen) {
            case TownMenus.CREATE -> openCreate(player);
            case TownMenus.MEMBERS -> openMembers(player, memberPage.getOrDefault(player.getUniqueId(), 1));
            case TownMenus.MEMBER_DETAIL -> {
                final UUID selected = selectedMember.get(player.getUniqueId());
                if (selected == null) {
                    openMembers(player, memberPage.getOrDefault(player.getUniqueId(), 1));
                } else {
                    openMember(player, nameOf(selected));
                }
            }
            case TownMenus.INVITE_PICKER -> openInvitePicker(player,
                    invitePage.getOrDefault(player.getUniqueId(), 1));
            case TownMenus.CLAIM -> openClaimAt(player,
                    claimCenter.getOrDefault(player.getUniqueId(), ChunkKey.of(player.getLocation())));
            case TownMenus.BUFFS -> openBuffs(player, buffPage.getOrDefault(player.getUniqueId(), 1));
            default -> openHome(player);
        }
    }

    private String homeScreen(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        if (view == null) {
            return plugin.service().inviteOf(player.getUniqueId()) == null
                    ? TownMenus.HOME_NONE : TownMenus.HOME_INVITED;
        }
        return view.active() ? TownMenus.HOME_ACTIVE : TownMenus.HOME_PENDING;
    }

    void markAndOpen(final Player player, final String menuId) {
        current.put(player.getUniqueId(), menuId);
        // 不强制切换输入模式：默认 mouse（客户端 Mod 本地渲染光标，最跟手），
        // 玩家可用 /arcmenu mode 自选 touch / vision，服务器策略由 ArcMenu config 决定。
        api.open(player, renderedMenu(player, menuId));
    }

    /** 等待页完全不创建图片字形，未接收资源包的客户端也能正常操作网格。 */
    private String renderedMenu(Player player, String menuId) {
        if (!TownMenus.CLAIM.equals(menuId)) return menuId;
        ChunkKey center = claimCenter.get(player.getUniqueId());
        return center != null && mapRenderer != null && mapRenderer.isReady(TownMapRenderer.fileKey(center))
                && plugin.resourcePacks().loaded(player) ? TownMenus.CLAIM : TownMenus.CLAIM_WAITING;
    }

    /** 供 CE 手工重建后的回执验证使用，只读取资源文件。 */
    public boolean verifyMapPack(Path pack) throws IOException {
        return mapRenderer != null && mapRenderer.containsCurrentMaps(pack);
    }

    // ------------------------------------------------------------------ API 输入与地图拖动

    /** 点击输入框 / 邀请按钮时唤起 ArcMenu 文本输入；提交后直接执行对应业务，绕开 Arcitx 需先 Esc 的问题。 */
    public void requestInput(final Player player, final String kind) {
        final boolean create = "name".equals(kind);
        if (api.textInput() == null) {
            plugin.messages().send(player, "input-unavailable");
            return;
        }
        final ArcMenuTextInputOptions options;
        try {
            final ArcMenuTextInputOptions.Builder builder = ArcMenuTextInputOptions.builder()
                    .title(create ? "输入小镇名称" : "输入要邀请的玩家名")
                    .maxLength(create ? Math.min(50, plugin.config().nameMaxLength()) : 16);
            options = builder.build();
        } catch (final LinkageError | RuntimeException e) {
            plugin.getLogger().warning("ArcMenu 文本输入不可用：" + e.getClass().getSimpleName());
            plugin.messages().send(player, "input-unavailable");
            return;
        }
        final UUID playerId = player.getUniqueId();
        final ArcMenuTextInputHandle previous = textInputs.remove(playerId);
        if (previous != null) {
            try {
                previous.close();
            } catch (final RuntimeException ignored) {
            }
        }
        final ArcMenuTextInputHandle handle = api.textInput().request(plugin, player, options,
                new com.fentai.arcmenu.api.ArcMenuTextInputListener() {
                    @Override
                    public void onComplete(final com.fentai.arcmenu.api.ArcMenuTextInputResult result) {
                        textInputs.remove(playerId);
                        if (result.getReason() != ArcMenuTextInputEndReason.SUBMITTED
                                || result.getValue() == null || result.getValue().isBlank()) {
                            return;
                        }
                        Threads.forPlayer(plugin, player, () -> {
                            final String value = result.getValue().trim();
                            final var feedback = TownUi.this.feedbackFor(player);
                            if (create) {
                                plugin.service().create(player, value, feedback);
                            } else {
                                plugin.service().invite(player, value, feedback);
                            }
                        });
                    }
                });
        textInputs.put(playerId, handle);
    }

    public void operation(Player player,String input) {operations.command(player,input);}
    void closeOperations(Player player) {api.close(player);current.remove(player.getUniqueId());}
    void requestOperationInput(Player player,String title,java.util.function.Consumer<String> submitted) {
        if(api.textInput()==null) {plugin.messages().send(player,"input-unavailable");return;}
        var old=textInputs.remove(player.getUniqueId());if(old!=null) old.close();
        var handle=api.textInput().request(plugin,player,ArcMenuTextInputOptions.builder().title(title).maxLength(50).build(),
                new com.fentai.arcmenu.api.ArcMenuTextInputListener() {
                    @Override public void onComplete(com.fentai.arcmenu.api.ArcMenuTextInputResult result) {
                        textInputs.remove(player.getUniqueId());
                        if(result.getReason()==ArcMenuTextInputEndReason.SUBMITTED && result.getValue()!=null && !result.getValue().isBlank())
                            Threads.forPlayer(plugin,player,()->submitted.accept(result.getValue().trim()));
                    }
                });
        textInputs.put(player.getUniqueId(),handle);
    }

    /** 输入提交后的反馈：投递结果并按当前页面决定回跳。 */
    private java.util.function.Consumer<TownService.Result> feedbackFor(final Player player) {
        return result -> {
            plugin.service().deliver(player, result);
            afterResult(player, result);
        };
    }

    /** 圈地地图拖动：以冻结中心为基准平移后重开本页。 */
    public void moveClaim(final Player player, final int dx, final int dz) {
        final ChunkKey center = claimCenter.computeIfAbsent(player.getUniqueId(),
                ignored -> ChunkKey.of(player.getLocation()));
        claimCenter.put(player.getUniqueId(),
                new ChunkKey(center.world(), center.x() + dx, center.z() + dz));
        openClaimAt(player, claimCenter.get(player.getUniqueId()));
    }

    // ------------------------------------------------------------------ 刷新与会话

    public void refresh(final Player player) {
        final String screen = current.get(player.getUniqueId());
        if (screen == null || !player.isOnline()) {
            return;
        }
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        writeCommon(player, view);
        if(screen.startsWith(TownOperationMenus.PREFIX)) {operations.write(player);api.refresh(player);return;}
        switch (screen) {
            case TownMenus.MEMBERS -> writeMembers(player);
            case TownMenus.MEMBER_DETAIL -> writeSelected(player);
            case TownMenus.INVITE_PICKER -> writeCandidates(player);
            case TownMenus.CLAIM -> writeClaim(player);
            case TownMenus.BUFFS -> writeBuffs(player);
            case TownMenus.CONFIRM -> {
                final ConfirmSession session = confirmations.get(player.getUniqueId());
                if (session != null) {
                    writeConfirm(player, session.action());
                }
            }
            default -> {
            }
        }
        String rendered = renderedMenu(player, screen);
        var session = api.sessions().current(player);
        if (TownMenus.CLAIM.equals(screen) && session != null && !rendered.equals(session.getId())) {
            api.open(player, rendered);
        } else {
            api.refresh(player);
        }
    }

    /** 全局周期任务调用；实际写入和刷新仍回到各玩家区域线程。 */
    public void tick() {
        for (final UUID id : List.copyOf(current.keySet())) {
            final Player player = Bukkit.getPlayer(id);
            if (player == null) {
                forget(id);
                continue;
            }
            Threads.forPlayer(plugin, player, () -> {
                if (!current.containsKey(id)) {
                    return;
                }
                if (api.sessions().current(player) == null) {
                    clear(id);
                    return;
                }
                refresh(player);
                drawChunkPreview(player);
            });
        }
    }

    /** 地图页常显中心区块轮廓；确认页改为高亮目标区块（买绿 / 弃红）。 */
    private void drawChunkPreview(final Player player) {
        final UUID id = player.getUniqueId();
        final String screen = current.get(id);
        ChunkKey chunk = null;
        org.bukkit.Color color = null;
        if (TownMenus.CONFIRM.equals(screen)) {
            final ConfirmSession session = confirmations.get(id);
            if (session != null && chunkPreviews.containsKey(id)) {
                chunk = chunkPreviews.get(id);
                final boolean buying = session.action().startsWith("claimmap")
                        || session.action().startsWith("claimpos");
                color = buying ? org.bukkit.Color.fromRGB(0x52, 0xC4, 0x8B)
                        : org.bukkit.Color.fromRGB(0xD9, 0x76, 0x76);
            }
        } else if (TownMenus.CLAIM.equals(screen)) {
            chunk = claimCenter.get(id);
            color = org.bukkit.Color.fromRGB(0xD8, 0xAE, 0x63);
        }
        if (chunk == null || color == null) {
            chunkPreviews.remove(id);
            return;
        }
        final org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.4f);
        final int baseY = player.getLocation().getBlockY() + 1;
        final int minX = chunk.x() << 4;
        final int minZ = chunk.z() << 4;
        for (int i = 0; i <= 16; i += 2) {
            player.spawnParticle(org.bukkit.Particle.DUST, minX + i + 0.5, baseY, minZ + 0.5,
                    1, 0, 0, 0, 0, dust);
            player.spawnParticle(org.bukkit.Particle.DUST, minX + i + 0.5, baseY, minZ + 15.5,
                    1, 0, 0, 0, 0, dust);
            player.spawnParticle(org.bukkit.Particle.DUST, minX + 0.5, baseY, minZ + i + 0.5,
                    1, 0, 0, 0, 0, dust);
            player.spawnParticle(org.bukkit.Particle.DUST, minX + 15.5, baseY, minZ + i + 0.5,
                    1, 0, 0, 0, 0, dust);
        }
    }

    /** close 后下一 tick 再查会话；同页翻页/重开与真正关闭因此可区分。 */
    public void closed(final Player player, final String menuId) {
        final String logicalId = TownMenus.CLAIM_WAITING.equals(menuId) ? TownMenus.CLAIM : menuId;
        if (!logicalId.equals(current.get(player.getUniqueId()))) {
            return;
        }
        player.getScheduler().runDelayed(plugin, task -> {
            if (api.sessions().current(player) == null
                    && logicalId.equals(current.get(player.getUniqueId()))) {
                clear(player.getUniqueId());
            }
        }, null, 1L);
    }

    public void forget(final UUID playerId) {
        operations.forget(playerId);
        clear(playerId);
    }

    private void clear(final UUID playerId) {
        current.remove(playerId);
        memberPage.remove(playerId);
        invitePage.remove(playerId);
        buffPage.remove(playerId);
        claimCenter.remove(playerId);
        selectedMember.remove(playerId);
        confirmations.remove(playerId);
        chunkPreviews.remove(playerId);
        final ArcMenuTextInputHandle input = textInputs.remove(playerId);
        if (input != null) {
            try {
                input.close();
            } catch (final RuntimeException ignored) {
            }
        }
        balanceLoading.remove(playerId);
        balanceRetryAt.remove(playerId);
    }

    public void shutdown() {
        current.clear();
        memberPage.clear();
        invitePage.clear();
        buffPage.clear();
        claimCenter.clear();
        selectedMember.clear();
        confirmations.clear();
        chunkPreviews.clear();
        textInputs.values().forEach(handle -> {
            try {
                handle.close();
            } catch (final RuntimeException ignored) {
            }
        });
        textInputs.clear();
        balanceLoading.clear();
        balanceRetryAt.clear();
    }

    // ------------------------------------------------------------------ META 写入

    void set(final Player player, final String key, final String value) {
        if (value == null) {
            api.state().remove(player, ArcMenuStateScope.META, key);
        } else {
            api.state().set(player, ArcMenuStateScope.META, key, value);
        }
    }

    private void writeCommon(final Player player, final TownSnapshotView view) {
        final TownConfig cfg = plugin.config();
        warmAssets(player);
        writeBalance(player);
        set(player, "name_len", cfg.nameMinLength() + "~" + cfg.nameMaxLength() + " 字符");
        set(player, "min_members", String.valueOf(cfg.minMembers()));
        set(player, "found_cost", costText(cfg.foundingCost()));
        set(player, "claim_price", view != null && view.raw().freeClaimsRemaining()>0
                ? "免费 · 剩余 " + view.raw().freeClaimsRemaining() + " 块"
                : costText(cfg.pricePerChunk()) + " / 块");
        final int claimLimit=plugin.civic().catalog().claimLimit(cfg.maxChunksPerTown(),
                view==null?com.example.town.economy.CivicState.empty():view.raw().civic());
        set(player, "claim_max", claimLimit>0?String.valueOf(claimLimit):"∞");
        final TownSnapshotView invite = plugin.service().inviteOf(player.getUniqueId());
        writeInvite(player, invite);
        if (view == null) {
            for (final String key : List.of("town_name", "member_count", "claim_count", "buff_count",
                    "mayor_name", "created_date", "hall_state", "hall_position", "found_state",
                    "danger_label", "danger_action")) {
                set(player, key, null);
            }
            set(player, "is_mayor", "0");
            set(player, "can_manage", "0");
            set(player, "can_found", "0");
            set(player, "has_spawn", "0");
            return;
        }
        final UUID self = player.getUniqueId();
        final Role role = view.members().get(self);
        final boolean mayor = view.mayor().equals(self);
        set(player, "town_name", view.name());
        set(player, "member_count", String.valueOf(view.memberCount()));
        set(player, "claim_count", String.valueOf(view.claimCount()));
        set(player, "buff_count", String.valueOf(view.buffs().size()));
        set(player, "mayor_name", nameOf(view.mayor()));
        set(player, "created_date", view.createdDate());
        set(player, "is_mayor", mayor ? "1" : "0");
        set(player, "can_manage", role != null && role.canManage() ? "1" : "0");
        set(player, "has_spawn", view.spawnSet() ? "1" : "0");
        set(player, "hall_state", view.hallSet() ? "已落成" : "未放置");
        set(player, "hall_position", view.hallSet()
                ? view.raw().hallWorld() + " · " + view.raw().hallX() + " / "
                + view.raw().hallY() + " / " + view.raw().hallZ()
                : "放置镇政厅后设定中心");
        final int missing = Math.max(0, cfg.minMembers() - view.memberCount());
        set(player, "can_found", view.state() == TownState.PENDING && missing == 0 ? "1" : "0");
        set(player, "found_state", missing == 0 ? "&#2F9E44已满足成立条件" : "&#8A5CCF还差 " + missing + " 人");
        set(player, "danger_label", mayor ? "解散小镇" : "退出小镇");
        set(player, "danger_action", mayor ? "disband" : "leave");
    }

    private void writeInvite(final Player player, final TownSnapshotView invite) {
        if (invite == null) {
            set(player, "invited_from", null);
            set(player, "invited_mayor", null);
            set(player, "invited_members", null);
            set(player, "invited_expiry", null);
            return;
        }
        set(player, "invited_from", invite.name());
        set(player, "invited_mayor", nameOf(invite.mayor()));
        set(player, "invited_members", String.valueOf(invite.memberCount()));
        final Long expiry = invite.invites().get(player.getUniqueId());
        if (expiry == null) {
            set(player, "invited_expiry", "邀请仍待处理");
        } else {
            final long seconds = Math.max(0, (expiry - System.currentTimeMillis() + 999) / 1000);
            set(player, "invited_expiry", seconds < 60 ? "不足 1 分钟后过期"
                    : "邀请剩余 " + (seconds + 59) / 60 + " 分钟");
        }
    }

    private void writeMembers(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        final List<Map.Entry<UUID, Role>> ordered = view == null ? List.of() : view.raw().membersByRole();
        final int requested = memberPage.getOrDefault(player.getUniqueId(), 1);
        final int now = clampPage(player, requested, ordered.size(), MEMBER_ROWS);
        memberPage.put(player.getUniqueId(), now);
        for (int i = 0; i < MEMBER_ROWS; i++) {
            final int index = (now - 1) * MEMBER_ROWS + i;
            if (view == null || index >= ordered.size()) {
                writeEmptyMember(player, i);
                continue;
            }
            final Map.Entry<UUID, Role> entry = ordered.get(index);
            final UUID member = entry.getKey();
            final String name = nameOf(member);
            final boolean self = member.equals(player.getUniqueId());
            set(player, "member_" + i + "_present", "1");
            set(player, "member_" + i + "_name", name);
            set(player, "member_" + i + "_display", self ? "&#287FA8" + name + " (我)" : name);
            set(player, "member_" + i + "_role", entry.getValue().name());
            set(player, "member_" + i + "_role_display", switch (entry.getValue()) {
                case MAYOR -> "&#8A5CCF镇长";
                case OFFICER -> "&#2F9E44官员";
                case MEMBER -> "&#666666成员";
            });
            set(player, "member_" + i + "_online", Bukkit.getPlayer(member) == null ? "离线" : "在线");
            set(player, "member_" + i + "_action", "管理");
        }
    }

    private void writeEmptyMember(final Player player, final int i) {
        set(player, "member_" + i + "_present", "0");
        set(player, "member_" + i + "_name", "");
        set(player, "member_" + i + "_display", "—");
        set(player, "member_" + i + "_role", "");
        set(player, "member_" + i + "_role_display", "");
        set(player, "member_" + i + "_online", "");
        set(player, "member_" + i + "_action", "");
    }

    private void writeSelected(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        final UUID selected = selectedMember.get(player.getUniqueId());
        final Role role = view == null || selected == null ? null : view.members().get(selected);
        if (role == null) {
            set(player, "selected_present", "0");
            set(player, "selected_name", "");
            set(player, "selected_display", "成员已离开");
            set(player, "selected_uuid", "");
            set(player, "selected_role_display", "");
            set(player, "selected_online", "返回成员列表重新选择");
            return;
        }
        set(player, "selected_present", "1");
        set(player, "selected_name", nameOf(selected));
        set(player, "selected_display", nameOf(selected));
        set(player, "selected_uuid", selected.toString());
        set(player, "selected_role_display", switch (role) {
            case MAYOR -> "&#8A5CCF镇长";
            case OFFICER -> "&#2F9E44官员";
            case MEMBER -> "&#666666成员";
        });
        set(player, "selected_online", Bukkit.getPlayer(selected) == null ? "当前离线" : "当前在线");
    }

    private void writeCandidates(final Player player) {
        final List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        final int requested = invitePage.getOrDefault(player.getUniqueId(), 1);
        final int now = clampPage(player, requested, online.size(), INVITE_ROWS);
        invitePage.put(player.getUniqueId(), now);
        final TownSnapshotView own = plugin.service().townOf(player.getUniqueId());
        final Role ownRole = own == null ? null : own.members().get(player.getUniqueId());
        final boolean canManage = ownRole != null && ownRole.canManage();
        for (int i = 0; i < INVITE_ROWS; i++) {
            final int index = (now - 1) * INVITE_ROWS + i;
            if (index >= online.size()) {
                set(player, "candidate_" + i + "_name", "");
                set(player, "candidate_" + i + "_state", "");
                set(player, "candidate_" + i + "_action", "");
                set(player, "candidate_" + i + "_can", "0");
                continue;
            }
            final Player candidate = online.get(index);
            final boolean hasTown = plugin.service().townOf(candidate.getUniqueId()) != null;
            final boolean invited = plugin.service().inviteOf(candidate.getUniqueId()) != null;
            final boolean can = canManage && !hasTown && !invited;
            set(player, "candidate_" + i + "_name", candidate.getName());
            set(player, "candidate_" + i + "_state", !canManage ? "&#666666无邀请权限"
                    : hasTown ? "&#666666已有小镇" : invited ? "&#8A5CCF已有邀请" : "&#2F9E44可邀请");
            set(player, "candidate_" + i + "_action", can ? "&#2F9E44邀请" : "&#666666不可邀请");
            set(player, "candidate_" + i + "_can", can ? "1" : "0");
        }
    }

    private int clampPage(final Player player, final int requested, final int size, final int rows) {
        final int pages = Math.max(1, (size + rows - 1) / rows);
        final int now = Math.min(Math.max(1, requested), pages);
        set(player, "page_now", String.valueOf(now));
        set(player, "page_total", String.valueOf(pages));
        set(player, "has_prev", now > 1 ? "1" : "0");
        set(player, "has_next", now < pages ? "1" : "0");
        set(player, "prev_page", String.valueOf(now - 1));
        set(player, "next_page", String.valueOf(now + 1));
        return now;
    }

    private void writeClaim(final Player player) {
        final ChunkKey center = claimCenter.get(player.getUniqueId());
        if (center == null) {
            return;
        }
        final TownSnapshotView own = plugin.service().townOf(player.getUniqueId());
        set(player, "claim_center", center.world() + " · " + center.x() + ", " + center.z());
        final String key = TownMapRenderer.fileKey(center);
        set(player, "map_status", plugin.resourcePacks().loaded(player)
                ? (mapRenderer != null && mapRenderer.isReady(key) ? "真实地形" : "正在绘制当前位置地形")
                : plugin.resourcePacks().message(player));
        if (mapRenderer != null && mapRenderer.isReady(key)) {
            set(player, "map_key", key);
        } else {
            set(player, "map_key", "default");
            if (mapRenderer != null && plugin.craftEngine() != null) {
                mapRenderer.ensureRendered(center).thenAccept(ready -> {
                    if (!ready) {
                        return;
                    }
                    // ArcMenu 玩家 META 和 refresh 必须回到该玩家区域线程。
                    Threads.forPlayer(plugin, player, () -> {
                        if (!TownMenus.CLAIM.equals(current.get(player.getUniqueId()))
                                || !center.equals(claimCenter.get(player.getUniqueId()))
                                || !mapRenderer.isReady(key)) {
                            return;
                        }
                        refresh(player);
                    });
                });
            }
        }
        for (int r = 0; r < CLAIM_GRID; r++) {
            for (int c = 0; c < CLAIM_GRID; c++) {
                final ChunkKey cell = new ChunkKey(center.world(),
                        center.x() + c - CLAIM_GRID / 2, center.z() + r - CLAIM_GRID / 2);
                set(player, "cellmark_" + r + "_" + c, stateMarkOf(cell, own));
            }
        }
    }

    /** 状态角标：本镇/可买/他镇/不相连/镇政厅；真实地形图之外的状态层。 */
    private String stateMarkOf(final ChunkKey key, final TownSnapshotView own) {
        final TownSnapshotView owner = plugin.service().townAt(key);
        if (owner != null) {
            if (own != null && owner.id().equals(own.id()) && key.equals(own.hallChunk())) {
                return "&#A87616★";
            }
            if (own != null && owner.id().equals(own.id())) {
                return "&#2F9E44■";
            }
            return "&#C94B4B■";
        }
        if (own == null) {
            return "&#666666·";
        }
        final int max = plugin.civic().catalog().claimLimit(plugin.config().maxChunksPerTown(), own.raw().civic());
        if (max > 0 && own.claimCount() >= max) {
            return "&#666666■";
        }
        for (final ChunkKey claim : own.claims()) {
            if (claim.isAdjacentTo(key, plugin.config().diagonalConnected())) {
                return "&#287FA8□";
            }
        }
        return "&#666666·";
    }

    /** 地图命令只能使用打开时冻结的中心，不能使用点击时的玩家位置。 */
    public ChunkKey cellAt(final Player player, final int row, final int col) {
        final ChunkKey center = claimCenter.computeIfAbsent(player.getUniqueId(),
                ignored -> ChunkKey.of(player.getLocation()));
        return new ChunkKey(center.world(), center.x() + col - CLAIM_GRID / 2,
                center.z() + row - CLAIM_GRID / 2);
    }

    private void writeBuffs(final Player player) {
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        final List<TownConfig.BuffDef> defs = plugin.config().buffs();
        final int requested = buffPage.getOrDefault(player.getUniqueId(), 1);
        final int now = clampPage(player, requested, defs.size(), BUFF_ROWS);
        buffPage.put(player.getUniqueId(), now);
        final long nowMs = System.currentTimeMillis();
        for (int i = 0; i < BUFF_ROWS; i++) {
            final int index = (now - 1) * BUFF_ROWS + i;
            if (index >= defs.size()) {
                for (final String suffix : List.of("id", "icon", "name", "cost", "duration", "state", "action")) {
                    set(player, "bf_" + i + "_" + suffix, "");
                }
                set(player, "bf_" + i + "_present", "0");
                set(player, "bf_" + i + "_can_buy", "0");
                continue;
            }
            final TownConfig.BuffDef def = defs.get(index);
            final Long expiry = view == null ? null : view.buffs().get(def.id());
            final boolean activePermanent = expiry != null && expiry == -1;
            set(player, "bf_" + i + "_present", "1");
            set(player, "bf_" + i + "_id", def.id());
            final String display = TownMenus.miniToAmp(def.displayMini());
            final String plain = MenuBuilder.stripCodes(display).replace("祝福·", "");
            set(player, "bf_" + i + "_icon", plain.isEmpty() ? "✦" : plain.substring(0, 1));
            set(player, "bf_" + i + "_name", display);
            set(player, "bf_" + i + "_cost", costText(def.cost()));
            set(player, "bf_" + i + "_duration", def.permanent() ? "永久" : def.durationMillis() / 60000 + " 分钟");
            if (activePermanent) {
                set(player, "bf_" + i + "_state", "&#8A5CCF永久生效");
            } else if (expiry != null && expiry > nowMs) {
                set(player, "bf_" + i + "_state", "&#2F9E44剩余 " + (expiry - nowMs + 59999) / 60000 + " 分钟");
            } else {
                set(player, "bf_" + i + "_state", "&#666666未激活");
            }
            set(player, "bf_" + i + "_action", activePermanent ? "&#666666已激活"
                    : expiry != null && expiry > nowMs ? "&#2F9E44续时" : "&#2F9E44购买");
            set(player, "bf_" + i + "_can_buy", activePermanent ? "0" : "1");
        }
    }

    // ------------------------------------------------------------------ 确认内容

    private void writeConfirm(final Player player, final String action) {
        final String[] parts = action.trim().split("\\s+");
        final String head = parts.length == 0 ? "" : parts[0];
        final String argument = parts.length > 1 ? parts[1] : "";
        final TownSnapshotView view = plugin.service().townOf(player.getUniqueId());
        String title = "确认执行？";
        String body1 = action;
        String body2 = "请检查操作对象与结果";
        String cost = "";
        String note = "取消会返回来源页面";
        switch (head) {
            case "found" -> {
                title = "正式成立 " + (view == null ? "小镇" : view.name()) + "？";
                body1 = "当前 " + (view == null ? 0 : view.memberCount()) + " 名成员";
                body2 = "成立后放置镇政厅，划定中心与初始领地";
                cost = costText(plugin.config().foundingCost());
                note = "费用将从当前经济来源的余额中扣除";
            }
            case "disband" -> {
                title = (view != null && view.active() ? "解散 " : "放弃筹备 ") + (view == null ? "小镇" : view.name()) + "？";
                body1 = view != null && view.active() ? "全部领地与祝福将被移除" : "筹备档案将被删除";
                body2 = "此操作不可恢复";
                note = "成员会收到小镇解散通知";
            }
            case "leave" -> {
                title = "退出 " + (view == null ? "小镇" : view.name()) + "？";
                body1 = "你将失去本镇领地权限";
                body2 = "之后可以再次接受邀请";
                note = "镇长不能退出，请先转让或解散";
            }
            case "kick" -> {
                title = "踢出 " + argument + "？";
                body1 = "该成员将失去本镇领地权限";
                body2 = "之后仍可再次邀请";
                note = "请确认玩家名没有选错";
            }
            case "transfer" -> {
                title = "转让镇长给 " + argument + "？";
                body1 = "对方将成为唯一镇长";
                body2 = "你将降为官员";
                note = "离线成员也可以接受转让";
            }
            case "claimmap", "unclaimat", "claimpos", "unclaimpos" -> {
                ChunkKey key = null;
                if (("claimmap".equals(head) || "unclaimat".equals(head)) && parts.length >= 3) {
                    key = cellAt(player, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } else if (("claimpos".equals(head) || "unclaimpos".equals(head)) && parts.length >= 4) {
                    key = new ChunkKey(parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                }
                if (key != null) {
                    chunkPreviews.put(player.getUniqueId(), key);
                    final boolean buying = "claimmap".equals(head) || "claimpos".equals(head);
                    title = (buying ? "购买区块 " : "放弃区块 ") + key.x() + ", " + key.z() + "？";
                    body1 = key.world() + (view == null ? "" : " · " + view.name());
                    body2 = buying ? "目标区块已用绿色光点在世界中标出" : "目标区块已用红色光点标出，保护将移除";
                    cost = buying ? costText(plugin.service().claimPrice(player.getUniqueId())) : "";
                    note = buying ? "新区块必须与现有领地相连" : "镇政厅所在区块不能放弃";
                }
            }
            case "buff" -> {
                final TownConfig.BuffDef def = buff(argument);
                if (def != null) {
                    title = "购买 " + MenuBuilder.stripCodes(TownMenus.miniToAmp(def.displayMini())) + "？";
                    body1 = def.permanent() ? "永久生效" : "时长 " + def.durationMillis() / 60000 + " 分钟";
                    body2 = "非永久祝福可重复购买叠加时长";
                    cost = costText(def.cost());
                    note = "祝福作用于身处本镇领地的成员";
                }
            }
            default -> {
            }
        }
        set(player, "confirm_action", action);
        set(player, "confirm_title", title);
        set(player, "confirm_body_1", body1);
        set(player, "confirm_body_2", body2);
        set(player, "confirm_cost", cost);
        set(player, "confirm_note", note);
    }

    private TownConfig.BuffDef buff(final String id) {
        for (final TownConfig.BuffDef def : plugin.config().buffs()) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Wallet 与名称

    private void warmAssets(final Player player) {
        final var wallet = plugin.service().wallet();
        if (wallet == null || !wallet.resolveCurrency(plugin.config().currencyCode()).isBlank()
                || !assetsLoading.compareAndSet(false, true)) {
            return;
        }
        wallet.preloadAssets().whenComplete((ignored, error) -> {
            assetsLoading.set(false);
            // Vault may complete immediately while its provider is unavailable.
            // Refresh only after resolving a currency, otherwise wait for the next UI tick.
            if (error == null && !wallet.resolveCurrency(plugin.config().currencyCode()).isBlank()
                    && player.isOnline()) {
                Threads.forPlayer(plugin, player, () -> refresh(player));
            }
        });
    }

    private void writeBalance(final Player player) {
        final var wallet = plugin.service().wallet();
        if (wallet == null) {
            showBalance(player, "—", "未连接", "经济服务不可用", "请检查当前经济来源配置");
            return;
        }
        final String code = wallet.resolveCurrency(plugin.config().currencyCode());
        if (code.isBlank()) {
            showBalance(player, "—", "等待连接", "等待经济服务或货币加载", wallet.name());
            return;
        }
        final String symbol = BalanceDisplay.plain(wallet.symbolOf(plugin.config().currencyCode(), code));
        final String currency = symbol.isBlank() ? "货币" : symbol;
        final String source = BalanceDisplay.plain(wallet.name());
        final Double cached = wallet.cachedBalance(player.getUniqueId(), code);
        if (cached != null && Double.isFinite(cached) && cached >= 0) {
            final var amount = java.math.BigDecimal.valueOf(cached);
            showBalance(player, BalanceDisplay.amount(amount), currency,
                    BalanceDisplay.full(amount) + " " + currency, source);
            return;
        }
        if (System.nanoTime() < balanceRetryAt.getOrDefault(player.getUniqueId(), 0L)) {
            showBalance(player, "暂不可用", currency, "余额查询失败，稍后自动重试", source);
            return;
        }
        showBalance(player, "查询中", currency, "正在查询可用余额", source);
        if (balanceLoading.add(player.getUniqueId())) {
            wallet.balance(player.getUniqueId(), player.getName(), code).whenComplete((value, error) -> {
                if (error != null || value == null || !Double.isFinite(value) || value < 0) {
                    balanceRetryAt.put(player.getUniqueId(), System.nanoTime()
                            + java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
                } else {
                    balanceRetryAt.remove(player.getUniqueId());
                }
                if (player.isOnline()) {
                    Threads.forPlayer(plugin, player, () -> {
                        balanceLoading.remove(player.getUniqueId());
                        refresh(player);
                    });
                } else {
                    balanceLoading.remove(player.getUniqueId());
                }
            });
        }
    }

    private void showBalance(final Player player, final String amount, final String currency,
                             final String full, final String source) {
        set(player, "balance_amount", amount);
        set(player, "balance_currency", BalanceDisplay.currency(currency));
        set(player, "balance_full", full);
        set(player, "balance_source", source);
        set(player, "balance", full);
    }

    private String costText(final double amount) {
        final var wallet = plugin.service().wallet();
        final String symbol = wallet == null ? "✦" : wallet.symbolOf(plugin.config().currencyCode(),
                wallet.resolveCurrency(plugin.config().currencyCode()));
        return trim(amount) + (symbol.isBlank() ? "" : " " + symbol);
    }

    private static String trim(final double amount) {
        return amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    private String nameOf(final UUID uuid) {
        final String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? "未知玩家" : name;
    }
}
