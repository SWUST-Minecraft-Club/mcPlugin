package online.yudream.minecraft.bukkit.compat;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 调度器兼容层：同一份 Java 8 / Spigot 1.13.2 字节码，在 Bukkit / Paper 上走传统
 * {@link org.bukkit.scheduler.BukkitScheduler}，在 Folia 上通过反射走区域化调度器。
 *
 * <p>为什么用反射：本插件要保持“1.8.8+ 都能跑”的产品线，编译目标不能换成 Folia API
 * （Folia 只支持 26.x + Java 25）。Folia 的调度器类不在 1.13.2 API 里，只能在运行期探测。
 *
 * <p>线程约定：
 * <ul>
 *     <li>{@link #runGlobal} / {@link #runGlobalRepeating}：全局区域线程，适合遍历在线玩家、
 *         读玩家名 / UUID、广播这类不碰世界状态的任务；</li>
 *     <li>{@link #runAsync} / {@link #runAsyncRepeating}：异步线程，适合 HTTP / 文件 IO；</li>
 *     <li>{@link #runFor} / {@link #runForDelayed}：玩家实体所属区域线程，适合发插件消息、
 *         改玩家状态这类必须在线程归属内的任务；已经在该线程时直接执行。</li>
 * </ul>
 */
public final class FoliaSchedulerCompat {

    private static final boolean FOLIA = detectFolia();

    private static final Object GLOBAL_SCHEDULER;
    private static final Object ASYNC_SCHEDULER;
    private static final Method GLOBAL_RUN;
    private static final Method GLOBAL_RUN_DELAYED;
    private static final Method GLOBAL_RUN_AT_FIXED_RATE;
    private static final Method ASYNC_RUN_NOW;
    private static final Method ASYNC_RUN_AT_FIXED_RATE;
    private static final Method PLAYER_GET_SCHEDULER;
    private static final Method ENTITY_RUN;
    private static final Method ENTITY_RUN_DELAYED;
    private static final Method ENTITY_RUN_AT_FIXED_RATE;
    private static final Method OWNED_BY_CURRENT_REGION;
    private static final Method SCHEDULED_TASK_CANCEL;

    static {
        Object global = null;
        Object async = null;
        Method globalRun = null;
        Method globalRunDelayed = null;
        Method globalRunAtFixedRate = null;
        Method asyncRunNow = null;
        Method asyncRunAtFixedRate = null;
        Method playerGetScheduler = null;
        Method entityRun = null;
        Method entityRunDelayed = null;
        Method entityRunAtFixedRate = null;
        Method ownedByCurrentRegion = null;
        Method scheduledTaskCancel = null;

        if (FOLIA) {
            try {
                final Class<?> serverClass = Bukkit.getServer().getClass();
                global = serverClass.getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                async = serverClass.getMethod("getAsyncScheduler").invoke(Bukkit.getServer());

                final Class<?> globalClass = global.getClass();
                globalRun = globalClass.getMethod("run", Plugin.class, Consumer.class);
                globalRunDelayed = globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                globalRunAtFixedRate = globalClass.getMethod("runAtFixedRate",
                        Plugin.class, Consumer.class, long.class, long.class);

                final Class<?> asyncClass = async.getClass();
                asyncRunNow = asyncClass.getMethod("runNow", Plugin.class, Consumer.class);
                asyncRunAtFixedRate = asyncClass.getMethod("runAtFixedRate",
                        Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

                final Class<?> entitySchedulerClass =
                        Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                playerGetScheduler = Player.class.getMethod("getScheduler");
                entityRun = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                entityRunDelayed = entitySchedulerClass.getMethod("runDelayed",
                        Plugin.class, Consumer.class, Runnable.class, long.class);
                entityRunAtFixedRate = entitySchedulerClass.getMethod("runAtFixedRate",
                        Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

                ownedByCurrentRegion = Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
                scheduledTaskCancel = Class.forName(
                        "io.papermc.paper.threadedregions.scheduler.ScheduledTask").getMethod("cancel");
            } catch (Throwable error) {
                Bukkit.getLogger().warning("[YuDream] Folia scheduler detection failed; "
                        + "falling back to the Bukkit scheduler: " + error);
            }
        }

        GLOBAL_SCHEDULER = global;
        ASYNC_SCHEDULER = async;
        GLOBAL_RUN = globalRun;
        GLOBAL_RUN_DELAYED = globalRunDelayed;
        GLOBAL_RUN_AT_FIXED_RATE = globalRunAtFixedRate;
        ASYNC_RUN_NOW = asyncRunNow;
        ASYNC_RUN_AT_FIXED_RATE = asyncRunAtFixedRate;
        PLAYER_GET_SCHEDULER = playerGetScheduler;
        ENTITY_RUN = entityRun;
        ENTITY_RUN_DELAYED = entityRunDelayed;
        ENTITY_RUN_AT_FIXED_RATE = entityRunAtFixedRate;
        OWNED_BY_CURRENT_REGION = ownedByCurrentRegion;
        SCHEDULED_TASK_CANCEL = scheduledTaskCancel;
    }

    private FoliaSchedulerCompat() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    // ------------------------------------------------------------------ 全局区域 / 主线程

    /** 全局区域线程执行一次。 */
    public static Object runGlobal(final Plugin plugin, final Runnable task) {
        if (!FOLIA) {
            return Bukkit.getScheduler().runTask(plugin, task);
        }
        return invoke(GLOBAL_RUN, GLOBAL_SCHEDULER, "runGlobal", plugin, consumer(task));
    }

    /** 延迟 ticks 在全局区域线程执行一次。 */
    public static Object runGlobalDelayed(final Plugin plugin, final Runnable task, final long delayTicks) {
        if (!FOLIA) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
        }
        return invoke(GLOBAL_RUN_DELAYED, GLOBAL_SCHEDULER, "runGlobalDelayed",
                plugin, consumer(task), Math.max(1L, delayTicks));
    }

    /** 以固定周期在全局区域线程执行。 */
    public static Object runGlobalRepeating(final Plugin plugin, final Runnable task,
                                            final long initialDelayTicks, final long periodTicks) {
        if (!FOLIA) {
            return Bukkit.getScheduler().runTaskTimer(plugin, task,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        }
        return invoke(GLOBAL_RUN_AT_FIXED_RATE, GLOBAL_SCHEDULER, "runGlobalRepeating",
                plugin, consumer(task), Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
    }

    // ------------------------------------------------------------------ 异步

    /** 异步线程执行一次。 */
    public static Object runAsync(final Plugin plugin, final Runnable task) {
        if (!FOLIA) {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
        return invoke(ASYNC_RUN_NOW, ASYNC_SCHEDULER, "runAsync", plugin, consumer(task));
    }

    /** 以固定周期在异步线程执行；参数单位是 Minecraft tick（1 tick = 50ms）。 */
    public static Object runAsyncRepeating(final Plugin plugin, final Runnable task,
                                           final long initialDelayTicks, final long periodTicks) {
        if (!FOLIA) {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        }
        final long initialMillis = Math.max(1L, initialDelayTicks) * 50L;
        final long periodMillis = Math.max(1L, periodTicks) * 50L;
        return invoke(ASYNC_RUN_AT_FIXED_RATE, ASYNC_SCHEDULER, "runAsyncRepeating",
                plugin, consumer(task), initialMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ 玩家实体区域

    /** 在玩家实体所属区域线程执行一次；已经在该线程时直接执行。 */
    public static Object runFor(final Plugin plugin, final Player player, final Runnable task) {
        if (!FOLIA) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return null;
            }
            return Bukkit.getScheduler().runTask(plugin, task);
        }
        if (ownedBy(player)) {
            task.run();
            return null;
        }
        final Object scheduler = entityScheduler(player);
        return invoke(ENTITY_RUN, scheduler, "runFor", plugin, consumer(task), null);
    }

    /** 延迟 ticks 在玩家实体所属区域线程执行一次。 */
    public static Object runForDelayed(final Plugin plugin, final Player player,
                                       final Runnable task, final long delayTicks) {
        if (!FOLIA) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
        }
        final Object scheduler = entityScheduler(player);
        return invoke(ENTITY_RUN_DELAYED, scheduler, "runForDelayed",
                plugin, consumer(task), null, Math.max(1L, delayTicks));
    }

    /** 以固定周期在玩家实体所属区域线程执行。 */
    public static Object runForRepeating(final Plugin plugin, final Player player, final Runnable task,
                                         final long initialDelayTicks, final long periodTicks) {
        if (!FOLIA) {
            return Bukkit.getScheduler().runTaskTimer(plugin, task,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
        }
        final Object scheduler = entityScheduler(player);
        return invoke(ENTITY_RUN_AT_FIXED_RATE, scheduler, "runForRepeating",
                plugin, consumer(task), null, Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
    }

    // ------------------------------------------------------------------ 取消

    /** 取消返回的调度任务（BukkitTask 或 Folia ScheduledTask），null 安全。 */
    public static void cancel(final Object handle) {
        if (handle == null) {
            return;
        }
        if (handle instanceof BukkitTask) {
            ((BukkitTask) handle).cancel();
            return;
        }
        if (SCHEDULED_TASK_CANCEL != null) {
            try {
                SCHEDULED_TASK_CANCEL.invoke(handle);
            } catch (Throwable error) {
                Bukkit.getLogger().warning("[YuDream] Could not cancel a Folia scheduled task: " + error);
            }
        }
    }

    // ------------------------------------------------------------------ 内部

    private static boolean detectFolia() {
        try {
            final String serverName = Bukkit.getServer().getName();
            if (serverName != null && serverName.toLowerCase().contains("folia")) {
                return true;
            }
            final String version = Bukkit.getVersion();
            return version != null && version.toLowerCase().contains("folia");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean ownedBy(final Player player) {
        if (OWNED_BY_CURRENT_REGION == null) {
            return false;
        }
        try {
            final Object result = OWNED_BY_CURRENT_REGION.invoke(null, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object entityScheduler(final Player player) {
        try {
            return PLAYER_GET_SCHEDULER.invoke(player);
        } catch (Throwable error) {
            throw new IllegalStateException("Could not obtain the Folia entity scheduler for "
                    + player.getName(), error);
        }
    }

    private static Consumer<Object> consumer(final Runnable task) {
        return new Consumer<Object>() {
            public void accept(final Object ignored) {
                task.run();
            }
        };
    }

    private static Object invoke(final Method method, final Object target, final String name,
                                 final Object... args) {
        if (method == null || target == null) {
            throw new IllegalStateException("Folia scheduler is unavailable for " + name);
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable error) {
            throw new IllegalStateException("Folia scheduler call failed for " + name, error);
        }
    }
}
