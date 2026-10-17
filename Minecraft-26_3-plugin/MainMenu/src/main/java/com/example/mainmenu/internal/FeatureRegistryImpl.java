package com.example.mainmenu.internal;

import com.example.mainmenu.api.FeaturePlacement;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import com.example.mainmenu.menu.MenuLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 功能注册表实现：负责位置冲突判定与功能区排序、分页。
 *
 * <p>排布规则（见 docs/功能插件接入规范.md）：
 * <ol>
 *     <li>自定义位置优先，但必须是功能区以外的格子，且不能是固定功能位、也不能已被别的功能占用；
 *         冲突时该功能改排到功能区，并在悬停文本末尾追加提示。</li>
 *     <li>其余功能按注册顺序填进功能区（第 3~5 行、第 2~8 列），每页 21 个。</li>
 * </ol>
 */
public final class FeatureRegistryImpl implements MenuFeatureRegistry {

    private final List<MenuFeature> features = new ArrayList<>();
    private final Map<String, FeaturePlacement> placements = new LinkedHashMap<>();
    private int pageCount = 1;

    @Override
    public synchronized void register(final MenuFeature feature) {
        Objects.requireNonNull(feature, "feature");
        if (feature.id().isBlank()) {
            throw new IllegalArgumentException("功能 id 不能为空");
        }
        if (feature.icon() == null || feature.icon().getType().isAir()) {
            throw new IllegalArgumentException("功能 " + feature.id() + " 的图标不能为空");
        }
        for (final MenuFeature existing : features) {
            if (existing.id().equals(feature.id())) {
                throw new IllegalArgumentException(
                        "功能 id 重复：" + feature.id() + "（已由 " + existing.owner() + " 注册）");
            }
        }
        features.add(feature);
        rebuild();
    }

    @Override
    public synchronized boolean unregister(final String id) {
        final boolean removed = features.removeIf(feature -> feature.id().equals(id));
        if (removed) {
            rebuild();
        }
        return removed;
    }

    @Override
    public synchronized List<MenuFeature> features() {
        return List.copyOf(features);
    }

    @Override
    public synchronized Optional<FeaturePlacement> placement(final String id) {
        return Optional.ofNullable(placements.get(id));
    }

    @Override
    public synchronized List<FeaturePlacement> placements() {
        return List.copyOf(placements.values());
    }

    @Override
    public synchronized int pageCount() {
        return pageCount;
    }

    @Override
    public int pageSize() {
        return MenuLayout.PAGE_SIZE;
    }

    /** 某一页功能区里的功能（page 从 1 开始）；自定义位置的功能不在功能区，不会出现在这里 */
    public synchronized List<MenuFeature> featuresOnPage(final int page) {
        final List<MenuFeature> result = new ArrayList<>();
        for (final MenuFeature feature : features) {
            final FeaturePlacement placement = placements.get(feature.id());
            if (placement != null && placement.page() == page) {
                result.add(feature);
            }
        }
        return result;
    }

    /** 自定义位置的功能（每页都会显示） */
    public synchronized List<MenuFeature> customPositionFeatures() {
        final List<MenuFeature> result = new ArrayList<>();
        for (final MenuFeature feature : features) {
            final FeaturePlacement placement = placements.get(feature.id());
            if (placement != null && placement.customPosition()) {
                result.add(feature);
            }
        }
        return result;
    }

    /** 某个格子当前显示的功能（自定义位置的功能在所有页都命中） */
    public synchronized Optional<MenuFeature> featureAt(final int slot, final int page) {
        for (final MenuFeature feature : features) {
            final FeaturePlacement placement = placements.get(feature.id());
            if (placement == null || placement.slot() != slot) {
                continue;
            }
            if (placement.page() == 0 || placement.page() == page) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------

    private void rebuild() {
        placements.clear();

        final Set<Integer> occupied = new HashSet<>(MenuLayout.FIXED_SLOTS);
        final Set<String> conflictMoved = new LinkedHashSet<>();
        final List<MenuFeature> pending = new ArrayList<>();

        for (final MenuFeature feature : features) {
            final OptionalInt preferred = feature.preferredSlot();
            if (preferred.isEmpty()) {
                pending.add(feature);
                continue;
            }
            final int slot = preferred.getAsInt();
            final boolean usable = MenuLayout.isFreeSlot(slot) && !occupied.contains(slot);
            if (usable) {
                placements.put(feature.id(),
                        new FeaturePlacement(feature.id(), slot, 0, true, false));
                occupied.add(slot);
            } else {
                // 位置冲突：固定功能位 / 已被别的功能占用 / 不在允许范围（含功能区）
                pending.add(feature);
                if (MenuLayout.isFixed(slot) || occupied.contains(slot)) {
                    conflictMoved.add(feature.id());
                }
            }
        }

        for (int index = 0; index < pending.size(); index++) {
            final MenuFeature feature = pending.get(index);
            final int page = index / MenuLayout.PAGE_SIZE + 1;
            final int slot = MenuLayout.FUNCTION_AREA.get(index % MenuLayout.PAGE_SIZE);
            placements.put(feature.id(), new FeaturePlacement(
                    feature.id(), slot, page, false, conflictMoved.contains(feature.id())));
        }

        pageCount = Math.max(1, (pending.size() + MenuLayout.PAGE_SIZE - 1) / MenuLayout.PAGE_SIZE);
    }
}
