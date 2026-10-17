package com.example.mainmenu.playtime;

import com.example.mainmenu.MainMenuPlugin;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * 在线时长服务：带缓存 + 异步刷新。
 *
 * <p>渲染菜单是主线程操作，绝不能在主线程做 HTTP 请求，所以这里：
 * <ol>
 *     <li>{@link #get(Player)} 只读缓存并顺带触发一次异步刷新；</li>
 *     <li>异步结果回来后写入缓存，下一次渲染（菜单每秒会刷新时钟/在线时长格）就能看到。</li>
 * </ol>
 */
public final class PlaytimeService {

    /** 展示状态 */
    public enum Status {
        /** 已配置后端，但还没有数据 */
        LOADING,
        /** 有数据 */
        OK,
        /** 后端返回失败或查不到记录 */
        UNAVAILABLE,
        /** 没配置 YuDream 后端 */
        NOT_CONFIGURED
    }

    /**
     * 一次展示所需的结果。
     *
     * @param status 状态
     * @param info   有数据时的内容
     */
    public record Result(Status status, PlaytimeInfo info) {
    }

    private static final Result NOT_CONFIGURED = new Result(Status.NOT_CONFIGURED, null);
    private static final Result LOADING = new Result(Status.LOADING, null);
    private static final Result UNAVAILABLE = new Result(Status.UNAVAILABLE, null);

    private final MainMenuPlugin plugin;
    private final YuDreamPlaytimeClient client;
    private final Map<UUID, PlaytimeInfo> cache = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public PlaytimeService(final MainMenuPlugin plugin) {
        this.plugin = plugin;
        this.client = new YuDreamPlaytimeClient(plugin);
    }

    /** 是否配置了后端 */
    public boolean isConfigured() {
        return client.isConfigured();
    }

    /**
     * 取某个玩家的在线时长（不阻塞）。
     * 缓存过期时会返回旧值并触发后台刷新，保证悬停文本不会突然变成“加载中”。
     */
    public Result get(final Player player) {
        if (!client.isConfigured()) {
            return NOT_CONFIGURED;
        }
        final PlaytimeInfo cached = cache.get(player.getUniqueId());
        if (cached == null) {
            request(player);
            return inFlight.contains(player.getUniqueId()) ? LOADING : UNAVAILABLE;
        }
        if (isExpired(cached)) {
            request(player);
        }
        return new Result(Status.OK, cached);
    }

    /** 触发一次异步刷新（缓存未过期时不重复请求） */
    public void request(final Player player) {
        final UUID uuid = player.getUniqueId();
        final PlaytimeInfo cached = cache.get(uuid);
        if (cached != null && !isExpired(cached)) {
            return;
        }
        if (!inFlight.add(uuid)) {
            return;
        }
        final String name = player.getName();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                final Optional<PlaytimeInfo> info = client.fetch(name, uuid);
                if (info.isPresent()) {
                    cache.put(uuid, info.get());
                } else {
                    plugin.getLogger().fine("后端没有玩家 " + name + " 的在线时长记录");
                }
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (final Exception exception) {
                plugin.getLogger().warning("读取在线时长出错：" + exception.getMessage());
            } finally {
                inFlight.remove(uuid);
            }
        });
    }

    /** 配置重载后清缓存 */
    public void invalidateAll() {
        cache.clear();
    }

    private boolean isExpired(final PlaytimeInfo info) {
        final long ttl = Math.max(0L, plugin.getConfig().getLong("yudream.cache-seconds", 30L)) * 1000L;
        return System.currentTimeMillis() - info.fetchedAt() >= ttl;
    }
}
