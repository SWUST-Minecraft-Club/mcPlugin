package com.example.mainmenu.api;

import java.util.List;
import java.util.Optional;

/**
 * 功能注册表。功能插件在主菜单启用后（用 {@code dependencies.server.MainMenu} 保证加载顺序）
 * 通过 {@link MainMenuApi#registry()} 或 Bukkit 服务系统拿到本接口的实例。
 *
 * <p>注册时机建议放在自己插件的 {@code onEnable()}：主菜单只保存引用，渲染时才会调用
 * {@link MenuFeature#icon()} 等方法。
 */
public interface MenuFeatureRegistry {

    /**
     * 注册一个功能项。
     *
     * @throws IllegalArgumentException id 重复或字段不合法
     */
    void register(MenuFeature feature);

    /**
     * 注销一个功能项（例如你的插件被禁用时）。
     *
     * @return 是否真的移除了
     */
    boolean unregister(String id);

    /** 当前所有功能项（注册顺序） */
    List<MenuFeature> features();

    /** 某个功能项的最终位置 */
    Optional<FeaturePlacement> placement(String id);

    /** 所有功能项的最终位置（固定位置在前，其余按功能区顺序） */
    List<FeaturePlacement> placements();

    /** 功能区总页数（至少 1 页） */
    int pageCount();

    /** 功能区每页可放的功能数 */
    int pageSize();
}
