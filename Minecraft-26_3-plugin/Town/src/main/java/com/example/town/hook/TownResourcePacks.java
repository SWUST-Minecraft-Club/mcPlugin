package com.example.town.hook;

import com.example.town.TownPlugin;
import com.example.town.util.Threads;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** CE 完成合包、托管和发送以后，仍需逐玩家确认同一 SHA-1 对应的包 UUID 已加载。 */
public final class TownResourcePacks implements Listener {
    private record Expected(String hash, CompletableFuture<UUID> id) { }

    private final TownPlugin plugin;
    private final ClassLoader loader;
    private final Map<UUID, Expected> expected = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, PlayerResourcePackStatusEvent.Status>> statuses = new ConcurrentHashMap<>();
    private final Map<UUID,String> confirmationRequests=new ConcurrentHashMap<>();
    private volatile String publishedHash = "";

    public TownResourcePacks(TownPlugin plugin) {
        this.plugin = plugin;
        var ce = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        loader = ce == null ? null : ce.getClass().getClassLoader();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** 纯 IO/CE 工作流在线程池执行；首次启动等待 CE 延迟加载结束，不在区域线程阻塞生成资源。 */
    public CompletableFuture<Path> rebuild() {
        CompletableFuture<Path> result = new CompletableFuture<>();
        if (loader == null) result.complete(null);
        else rebuild(result, 0);
        return result;
    }

    private void rebuild(CompletableFuture<Path> result, int attempt) {
        if (!plugin.isEnabled()) { result.complete(null); return; }
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
            try {
                Object engine = engine();
                if (!Boolean.TRUE.equals(engine.getClass().getMethod("isFullyLoaded").invoke(engine))
                        || Boolean.TRUE.equals(engine.getClass().getMethod("isReloading").invoke(engine))) {
                    retry(result, attempt, null);
                    return;
                }
                Object manager = engine.getClass().getMethod("packManager").invoke(engine);
                manager.getClass().getMethod("triggerWorkflows", String.class).invoke(manager, "reload_pack");
                Path pack = packFile();
                if (!Files.isRegularFile(pack)) throw new IllegalStateException("CE 工作流没有生成最终 ZIP");
                result.complete(pack);
            } catch (Exception | LinkageError error) {
                Throwable cause = error;
                while (cause.getCause() != null) cause = cause.getCause();
                if (cause.getClass().getSimpleName().equals("BusyException")) retry(result, attempt, cause);
                else result.completeExceptionally(cause);
            }
        }, attempt == 0 ? 1 : 1000, TimeUnit.MILLISECONDS);
    }

    private void retry(CompletableFuture<Path> result, int attempt, Throwable error) {
        if (attempt < 60) rebuild(result, attempt + 1);
        else result.completeExceptionally(error == null ? new IllegalStateException("等待 CE 资源就绪超时") : error);
    }

    private Object engine() throws ReflectiveOperationException {
        return Class.forName("net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine", true, loader)
                .getMethod("instance").invoke(null);
    }

    /** 读取现有 CE 工作流配置，不覆盖运营者的托管、上传或发送设置。 */
    private Path packFile() {
        Path root = plugin.getDataFolder().toPath().getParent().resolve("CraftEngine");
        var config = YamlConfiguration.loadConfiguration(root.resolve("config.yml").toFile());
        var workflows = config.getConfigurationSection("resource-pack.workflows");
        if (workflows != null) {
            for (String name : workflows.getKeys(false)) {
                if (!"reload_pack".equals(workflows.getString(name + ".trigger"))) continue;
                for (Map<?, ?> step : workflows.getMapList(name + ".steps")) {
                    if ("zip".equals(step.get("type")) && step.get("path") instanceof String path)
                        return root.resolve(path).normalize();
                }
            }
        }
        return root.resolve("generated/resource_pack.zip");
    }

    public Path currentPack() { return packFile(); }

    /** 由地图渲染器在验证最终 CE 包包含本批 PNG 后发布。必须在 IO 线程调用。 */
    public void published(Path pack) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(pack)));
        if (!hash.equals(publishedHash)) {
            publishedHash = hash;
            expected.clear();
        }
    }

    /** 必须在玩家区域线程查询；prepareResourcePacks 保留 CE 的玩家偏好和多包选择。 */
    public boolean loaded(Player player) {
        String hash = publishedHash;
        if (loader == null || hash.isEmpty()) return false;
        Expected value = expected.compute(player.getUniqueId(), (id, old) ->
                old != null && old.hash().equals(hash) ? old : new Expected(hash, expectedId(player, hash)));
        UUID packId = value.id().getNow(null);
        return packId != null && statuses.getOrDefault(player.getUniqueId(), Map.of()).get(packId)
                == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<UUID> expectedId(Player player, String hash) {
        try {
            Object engine = engine();
            Object network = engine.getClass().getMethod("networkManager").invoke(engine);
            Object user = network.getClass().getMethod("getUser", Player.class).invoke(network, player);
            if (user == null) return CompletableFuture.completedFuture(null);
            Object manager = engine.getClass().getMethod("packManager").invoke(engine);
            Class<?> userType = Class.forName("net.momirealms.craftengine.core.plugin.network.NetWorkUser", true, loader);
            var packs = (CompletableFuture<List<?>>) manager.getClass().getMethod("prepareResourcePacks", userType)
                    .invoke(manager, user);
            return packs.thenApply(list -> {
                for (Object pack : list) {
                    try {
                        if (hash.equals(pack.getClass().getMethod("sha1").invoke(pack)))
                            return (UUID) pack.getClass().getMethod("uuid").invoke(pack);
                    } catch (ReflectiveOperationException error) { return null; }
                }
                return null;
            }).exceptionally(error -> null);
        } catch (ReflectiveOperationException | LinkageError error) {
            return CompletableFuture.completedFuture(null);
        }
    }

    public String message(Player player) {
        if (loader == null) return "启用服务器资源包后可查看地形";
        Expected value = expected.get(player.getUniqueId());
        UUID id = value == null ? null : value.id().getNow(null);
        var status = id == null ? null : statuses.getOrDefault(player.getUniqueId(), Map.of()).get(id);
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED)
            return "请在服务器设置中启用资源包";
        if (status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || status == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
                || status == PlayerResourcePackStatusEvent.Status.INVALID_URL)
            return "资源包加载失败，请检查下载地址";
        return "地形资源加载中 · 仍可选择区块";
    }

    /** 首次真正查看地图时才补 PLAY 回执；普通入服不再为地图额外重载一次整包。 */
    public void confirmForMap(Player player) {
        String hash=publishedHash;
        if(loader==null || hash.isEmpty() || loaded(player)
                || hash.equals(confirmationRequests.put(player.getUniqueId(),hash))) return;
        player.getScheduler().runDelayed(plugin, task -> {
            if(!hash.equals(publishedHash)) {confirmationRequests.remove(player.getUniqueId(),hash);return;}
            if(loaded(player)) return;
            Path config = plugin.getDataFolder().toPath().getParent().resolve("CraftEngine/config.yml");
            if (!YamlConfiguration.loadConfiguration(config.toFile()).getBoolean("resource-pack.delivery.send-on-join", true)) return;
            try {
                Object engine = engine();
                Object network = engine.getClass().getMethod("networkManager").invoke(engine);
                Object user = network.getClass().getMethod("getOnlineUser", UUID.class).invoke(network, player.getUniqueId());
                if (user == null) return;
                Object manager = engine.getClass().getMethod("packManager").invoke(engine);
                Class<?> userType = Class.forName("net.momirealms.craftengine.core.entity.player.Player", true, loader);
                ((CompletableFuture<?>) manager.getClass().getMethod("sendResourcePackAsync", userType).invoke(manager, user))
                        .exceptionally(error -> { plugin.getLogger().warning("入服资源包确认失败：" + error.getClass().getSimpleName()); return null; });
            } catch (ReflectiveOperationException | LinkageError error) {
                plugin.getLogger().warning("入服资源包确认接口不可用：" + error.getClass().getSimpleName());
            }
        }, null, 10L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { confirmationRequests.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Map<UUID, PlayerResourcePackStatusEvent.Status> player = statuses.computeIfAbsent(
                event.getPlayer().getUniqueId(), ignored -> new ConcurrentHashMap<>());
        if (player.size() >= 32 && !player.containsKey(event.getID())) player.clear();
        player.put(event.getID(), event.getStatus());
        // 手工 /ce reload pack 也可能改变最终包的 SHA-1。回读最终包后重新匹配，不能信任任意包的成功事件。
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED && loader != null) {
            Threads.async(plugin, () -> {
                try {
                    Path pack = packFile();
                    if (plugin.ui() == null || plugin.ui().verifyMapPack(pack)) published(pack);
                }
                catch (Exception ignored) { }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        expected.remove(id);
        statuses.remove(id);
        confirmationRequests.remove(id);
    }
}
