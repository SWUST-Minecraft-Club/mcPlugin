package com.example.mainmenu.internal;

import com.example.mainmenu.api.FeaturePlacement;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.api.MenuFeatureRegistry;
import com.example.mainmenu.menu.MenuLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * 功能注册表实现：负责位置冲突判定、功能区排序分页，以及管理界面要用的状态读写。
 *
 * <p>排布规则：
 * <ol>
 *     <li>被隐藏的功能不显示、也不占用位置（所以原来与它冲突的插件可以正常显示）；</li>
 *     <li>固定位置功能插件（声明了功能区以外的 {@code preferredSlot}，或被管理员用拉杆改成固定的）：
 *         位置可用就放在那里；位置冲突则<b>依然算固定位置功能插件</b>，但显示在功能区<b>最前面</b>；</li>
 *     <li>其余功能按 {@code order}（可被管理员覆盖）排进功能区，每页 21 个。</li>
 * </ol>
 */
public final class FeatureRegistryImpl implements MenuFeatureRegistry {

    private final FeatureStateStore states;
    private final List<MenuFeature> features = new ArrayList<>();
    private final Map<String, FeaturePlacement> placements = new LinkedHashMap<>();
    private int pageCount = 1;

    public FeatureRegistryImpl(final FeatureStateStore states) {
        this.states = states;
    }

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

    // ------------------------------------------------------------------ 渲染用查询

    /** 某一页功能区里的功能（page 从 1 开始）；放在固定位置的功能不在功能区，不会出现在这里 */
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

    /** 放在固定位置的功能（每页都会显示） */
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

    /** 某个格子当前显示的功能（固定位置的功能在所有页都命中） */
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

    public synchronized Optional<MenuFeature> featureById(final String id) {
        return features.stream().filter(feature -> feature.id().equals(id)).findFirst();
    }

    // ------------------------------------------------------------------ 管理界面用

    public synchronized boolean isHidden(final String id) {
        return states.stateOf(id).hidden();
    }

    /** 是否固定位置功能插件（声明了位置，或被管理员用拉杆改过） */
    public synchronized boolean isFixed(final MenuFeature feature) {
        return fixedPositionOf(feature) != null;
    }

    /** 固定位置功能插件的槽位；非固定位置功能插件返回 null */
    public synchronized Integer fixedPositionOf(final MenuFeature feature) {
        final FeatureState state = states.stateOf(feature.id());
        if (state.forcedFunction()) {
            // 被管理员改回了非固定位置：忽略插件自己声明的位置
            return null;
        }
        if (state.position() != null) {
            return state.position();
        }
        return declaredPosition(feature);
    }

    /**
     * 用拉杆改回固定位置时会用到的槽位（含"已记录但当前被忽略"的位置）；
     * 从来没设置过位置时返回 null，此时 {@link #makeFixed} 会默认放到第 1 行第 1 格。
     */
    public synchronized Integer restorePositionOf(final String id) {
        final Optional<MenuFeature> found = featureById(id);
        if (found.isEmpty()) {
            return null;
        }
        final Integer recorded = states.stateOf(id).position();
        if (recorded != null) {
            return recorded;
        }
        return declaredPosition(found.get());
    }

    /** 功能区排序值（管理界面可用空地图调整） */
    public synchronized int orderOf(final MenuFeature feature) {
        final Integer override = states.stateOf(feature.id()).order();
        return override != null ? override : feature.order();
    }

    /** 管理界面左栏：非固定位置功能插件（含被隐藏的），按 order 排序 */
    public synchronized List<MenuFeature> functionAreaFeatures() {
        final List<MenuFeature> result = new ArrayList<>();
        for (final MenuFeature feature : features) {
            if (fixedPositionOf(feature) == null) {
                result.add(feature);
            }
        }
        result.sort(Comparator.comparingInt(this::orderOf));
        return result;
    }

    /** 管理界面右栏：固定位置功能插件（含被隐藏的、位置冲突的），按位置排序 */
    public synchronized List<MenuFeature> fixedFeatures() {
        final List<MenuFeature> result = new ArrayList<>();
        for (final MenuFeature feature : features) {
            if (fixedPositionOf(feature) != null) {
                result.add(feature);
            }
        }
        result.sort(Comparator.comparingInt(feature -> {
            final Integer position = fixedPositionOf(feature);
            return position == null ? Integer.MAX_VALUE : position;
        }));
        return result;
    }

    public synchronized void setHidden(final String id, final boolean hidden) {
        states.stateOf(id).hidden(hidden);
        rebuild();
        states.save();
    }

    public synchronized void setOrder(final String id, final int order) {
        states.stateOf(id).order(order);
        rebuild();
        states.save();
    }

    /**
     * 转成固定位置功能插件。
     *
     * <p>还原原来的固定位置：管理员设过的位置、或功能插件自己声明的 {@code preferredSlot} 都算"设置过"，
     * 直接沿用；只有从来没设置过位置的才默认放到第 1 行第 1 格。
     */
    public synchronized void makeFixed(final String id) {
        final FeatureState state = states.stateOf(id);
        state.forcedFixed(true);
        state.forcedFunction(false);
        if (state.position() == null && !hasDeclaredPosition(id)) {
            state.position(0);
        }
        rebuild();
        states.save();
    }

    /**
     * 改回非固定位置功能插件（按 order 排进功能区）。
     *
     * <p>插件自己声明的位置会被忽略；但已经记录的位置<b>保留</b>下来，
     * 之后再用拉杆改回固定位置时能回到原来的位置，而不是被当成"没设置过"。
     */
    public synchronized void makeFunction(final String id) {
        final FeatureState state = states.stateOf(id);
        state.forcedFunction(true);
        state.forcedFixed(false);
        rebuild();
        states.save();
    }

    /** 设置固定位置功能插件的位置（即使与其它固定位置功能插件冲突也照样记录） */
    public synchronized void setPosition(final String id, final int slot) {
        states.stateOf(id).position(slot);
        rebuild();
        states.save();
    }

    // ------------------------------------------------------------------

    /**
     * 功能插件自己声明的固定位置；没声明、或声明在功能区（含越界）都返回 null。
     *
     * <p>功能区本来就是"没声明位置"的功能的去处，所以声明在功能区里不算自定义位置，也不算冲突。
     */
    private static Integer declaredPosition(final MenuFeature feature) {
        final OptionalInt declared = feature.preferredSlot();
        if (declared.isEmpty()) {
            return null;
        }
        final int slot = declared.getAsInt();
        if (slot < 0 || slot >= MenuLayout.SIZE || MenuLayout.isFunctionArea(slot)) {
            return null;
        }
        return slot;
    }

    /** 功能插件自己是否声明过位置（{@code preferredSlot}） */
    private boolean hasDeclaredPosition(final String id) {
        return featureById(id).map(FeatureRegistryImpl::declaredPosition).isPresent();
    }

    private void rebuild() {
        placements.clear();

        final Set<Integer> occupied = new HashSet<>(MenuLayout.FIXED_SLOTS);
        final List<MenuFeature> functionPending = new ArrayList<>();
        final List<MenuFeature> fixedOverflow = new ArrayList<>();

        for (final MenuFeature feature : features) {
            if (states.stateOf(feature.id()).hidden()) {
                continue;
            }
            final Integer position = fixedPositionOf(feature);
            if (position == null) {
                functionPending.add(feature);
                continue;
            }
            if (MenuLayout.isFreeSlot(position) && !occupied.contains(position)) {
                placements.put(feature.id(), new FeaturePlacement(feature.id(), position, 0, true, false));
                occupied.add(position);
            } else {
                // 固定位置冲突：依然是固定位置功能插件，只是显示在功能区最前面
                fixedOverflow.add(feature);
            }
        }

        fixedOverflow.sort(Comparator.comparingInt(this::orderOf));
        functionPending.sort(Comparator.comparingInt(this::orderOf));

        final List<MenuFeature> pageOrder = new ArrayList<>(fixedOverflow);
        pageOrder.addAll(functionPending);

        for (int index = 0; index < pageOrder.size(); index++) {
            final MenuFeature feature = pageOrder.get(index);
            final int page = index / MenuLayout.PAGE_SIZE + 1;
            final int slot = MenuLayout.FUNCTION_AREA.get(index % MenuLayout.PAGE_SIZE);
            placements.put(feature.id(), new FeaturePlacement(
                    feature.id(), slot, page, false, fixedOverflow.contains(feature)));
        }

        pageCount = Math.max(1, (pageOrder.size() + MenuLayout.PAGE_SIZE - 1) / MenuLayout.PAGE_SIZE);
    }
}
