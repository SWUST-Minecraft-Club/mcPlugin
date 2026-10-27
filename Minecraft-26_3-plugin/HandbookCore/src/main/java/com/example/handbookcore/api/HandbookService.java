package com.example.handbookcore.api;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 注册操作线程安全；打开界面可从任意线程调用，核心切回玩家所属 Folia 区域。
 * 内容仅存于内存，贡献者应在 onEnable 注册，核心也会在贡献插件停用时自动清理。
 */
public interface HandbookService {
    void registerCategory(Plugin owner, HandbookCategory category);
    void registerEntry(Plugin owner, HandbookEntry entry);
    void replace(Plugin owner, HandbookContribution contribution);
    void unregister(Plugin owner, NamespacedKey id);
    void unregisterAll(Plugin owner);
    void openHome(Player player);
    /** false 表示分类不存在；权限与存活状态在玩家线程再次检查。 */
    boolean openCategory(Player player, NamespacedKey id);
    boolean openEntry(Player player, NamespacedKey id);
    void search(Player player, String query);
}
