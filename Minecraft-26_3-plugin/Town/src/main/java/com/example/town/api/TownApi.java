package com.example.town.api;

import com.example.town.model.ChunkKey;
import com.example.town.model.TownSnapshotView;
import com.example.town.service.TownService;
import java.util.List;
import java.util.UUID;

/**
 * Town 公开 API（静态门面，仿 WalletApi）。
 * 用法：其他插件 build.gradle 加 {@code compileOnly project(':Town')}，
 * paper-plugin.yml 声明 {@code Town: {load: BEFORE, required: false}}，
 * 调用前先 {@link #isAvailable()}。事件见 {@code com.example.town.api.event}。
 */
public final class TownApi {

    private static volatile TownService service;

    private TownApi() {
    }

    public static void install(TownService townService) {
        service = townService;
    }

    public static void uninstall() {
        service = null;
    }

    public static boolean isAvailable() {
        return service != null;
    }

    /** 玩家所在小镇（无则 null）。 */
    public static TownSnapshotView townOf(UUID playerId) {
        TownService current = service;
        return current == null ? null : current.townOf(playerId);
    }

    /** 区块所属小镇（无则 null）。 */
    public static TownSnapshotView townAt(ChunkKey key) {
        TownService current = service;
        return current == null ? null : current.townAt(key);
    }

    /** 全部小镇快照。 */
    public static List<TownSnapshotView> towns() {
        TownService current = service;
        return current == null ? List.of() : current.listTowns();
    }
}
