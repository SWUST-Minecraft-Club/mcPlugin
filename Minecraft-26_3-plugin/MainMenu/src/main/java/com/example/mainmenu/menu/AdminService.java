package com.example.mainmenu.menu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.FeaturePlacement;
import com.example.mainmenu.api.MenuFeature;
import com.example.mainmenu.internal.PlaceholderFeature;
import com.example.mainmenu.util.Schedulers;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 功能插件管理界面：管理列表 → 属性页 → 位置选择界面。
 *
 * <ul>
 *     <li>管理列表：左 4 列管非固定位置功能插件、右 4 列管固定位置功能插件，各自翻页，第 6 行第 5 格返回主菜单</li>
 *     <li>属性页：第 3 行第 3 格羊毛（显示 / 隐藏）、第 3 行第 5 格空地图（排序值）/ 末影之眼（更改位置）、
 *         第 3 行第 7 格拉杆（非固定位 → 转成固定位置功能插件），第 6 行第 5 格返回</li>
 *     <li>位置选择：跟主菜单一样的界面，只显示各功能的名称；点非功能区、非翻页位置设置位置，
 *         管理入口那格放屏障表示取消</li>
 * </ul>
 */
public final class AdminService {

    private final MainMenuPlugin plugin;

    public AdminService(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ 打开界面

    public void openManage(final Player player) {
        openManage(player, 1, 1);
    }

    private void openManage(final Player player, final int leftPage, final int rightPage) {
        final AdminHolder holder = new AdminHolder(player.getUniqueId(), AdminHolder.Mode.MANAGE, null);
        final Inventory inventory = Bukkit.createInventory(holder, MenuLayout.SIZE,
                plugin.miniRaw(plugin.string("menu.admin.title", "<dark_gray>插件管理</dark_gray>")));
        holder.attach(inventory);
        holder.leftPage(leftPage);
        holder.rightPage(rightPage);
        fillManage(player, holder);
        player.openInventory(inventory);
    }

    private void openAttribute(final Player player, final String featureId, final boolean fixed) {
        if (plugin.features().isPlaceholder(featureId)) {
            openPlaceholderAttribute(player, featureId);
            return;
        }
        final AdminHolder.Mode mode = fixed
                ? AdminHolder.Mode.ATTRIBUTE_FIXED
                : AdminHolder.Mode.ATTRIBUTE_FUNCTION;
        final AdminHolder holder = new AdminHolder(player.getUniqueId(), mode, featureId);
        final Inventory inventory = Bukkit.createInventory(holder, MenuLayout.SIZE,
                plugin.miniRaw(plugin.string("menu.admin.attribute-title", "<dark_gray>功能属性</dark_gray>")));
        holder.attach(inventory);
        fillAttribute(player, holder);
        player.openInventory(inventory);
    }

    /** 占位符属性页：只有排序值和删除两个操作。 */
    private void openPlaceholderAttribute(final Player player, final String featureId) {
        final AdminHolder holder = new AdminHolder(player.getUniqueId(),
                AdminHolder.Mode.ATTRIBUTE_PLACEHOLDER, featureId);
        final Inventory inventory = Bukkit.createInventory(holder, MenuLayout.SIZE,
                plugin.miniRaw(plugin.string("menu.admin.placeholder-title", "<dark_gray>占位符属性</dark_gray>")));
        holder.attach(inventory);
        fillPlaceholderAttribute(player, holder);
        player.openInventory(inventory);
    }

    /** 在功能区末尾添加占位符，并把左栏翻到它所在页。 */
    private void addPlaceholder(final Player player, final AdminHolder holder) {
        final String id = plugin.features().addPlaceholder();
        final List<MenuFeature> list = plugin.features().functionAreaFeatures();
        for (int index = 0; index < list.size(); index++) {
            if (list.get(index).id().equals(id)) {
                holder.leftPage(index / AdminLayout.PAGE_SIZE + 1);
                break;
            }
        }
        fillManage(player, holder);
        player.sendMessage(plugin.miniRaw(
                "<green>已添加功能占位符；点击红色玻璃板调整排序值或删除。</green>"));
    }

    /** 第 1 行第 3 格：弹出 dialog 输入名称，创建自定义功能。 */
    private void openCustomFeatureDialog(final Player player) {
        final TextDialogInput nameInput = DialogInput.text("name", Component.text("功能名称"))
                .maxLength(64)
                .build();
        final DialogBase base = DialogBase.builder(Component.text("添加自定义插件功能"))
                .body(List.of(DialogBody.plainMessage(
                        Component.text("输入功能名称；创建后在 plugins/MainMenu/custom-features.yml "
                                + "配置图标、描述与点击指令。"), 300)))
                .inputs(List.of(nameInput))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        final ActionButton confirm = ActionButton.builder(Component.text("创建"))
                .width(200)
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    final String name = response.getText("name");
                    // Dialog 回调线程不一定是玩家区域线程；创建与重开管理页统一回到玩家线程
                    Schedulers.runFor(plugin, p, () -> createCustomFeatureAndReopen(p, name));
                }, ClickCallback.Options.builder()
                        .uses(1)
                        .lifetime(Duration.ofMinutes(10))
                        .build()))
                .build();
        final DialogType type = DialogType.multiAction(List.of(confirm))
                .exitAction(null)
                .columns(1)
                .build();

        player.closeInventory();
        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private void createCustomFeatureAndReopen(final Player player, final String name) {
        final String id = plugin.createCustomFeature(name);
        if (id == null) {
            player.sendMessage(plugin.miniRaw("<red>功能名称不能为空。</red>"));
            openCustomFeatureDialog(player);
            return;
        }
        final List<MenuFeature> list = plugin.features().functionAreaFeatures();
        int page = 1;
        for (int index = 0; index < list.size(); index++) {
            if (list.get(index).id().equals(id)) {
                page = index / AdminLayout.PAGE_SIZE + 1;
                break;
            }
        }
        openManage(player, page, 1);
        player.sendMessage(plugin.miniRaw(
                "<green>已创建自定义功能；点击纸图标可像其它功能一样配置。</green>"));
    }

    private void fillPlaceholderAttribute(final Player player, final AdminHolder holder) {
        final Optional<MenuFeature> found = plugin.features().featureById(holder.featureId());
        if (found.isEmpty() || !(found.get() instanceof PlaceholderFeature)) {
            player.closeInventory();
            return;
        }
        final MenuFeature feature = found.get();

        // 只放两个操作位：排序值 / 删除；其余格子留空
        final ItemStack[] items = new ItemStack[MenuLayout.SIZE];
        final int order = plugin.features().orderOf(feature);
        items[AdminLayout.ATTR_MIDDLE] = named("MAP", "<yellow>排序值</yellow>", List.of(
                plugin.miniRaw("<gray>当前：<white>" + order + "</white></gray>"),
                plugin.miniRaw("<dark_gray>左键 +1，右键 -1</dark_gray>"),
                plugin.miniRaw("<dark_gray>按住 Shift 为 ±4</dark_gray>"),
                plugin.miniRaw("<dark_gray>排序值只影响功能区顺序</dark_gray>")));
        items[AdminLayout.ATTR_RIGHT] = named("TNT", "<red>删除该功能插件占位符</red>", List.of(
                plugin.miniRaw("<gray>从管理界面删除该占位符</gray>"),
                plugin.miniRaw("<dark_gray>不会影响已注册的功能插件</dark_gray>")));
        items[AdminLayout.BACK] = named("BARRIER", "<gray>返回管理列表</gray>", null);

        holder.getInventory().setContents(items);
    }

    private void openPositionPicker(final Player player, final String featureId) {
        final AdminHolder holder = new AdminHolder(player.getUniqueId(),
                AdminHolder.Mode.POSITION_PICKER, featureId);
        final Inventory inventory = Bukkit.createInventory(holder, MenuLayout.SIZE,
                plugin.miniRaw(plugin.string("menu.admin.position-title", "<dark_gray>选择新位置</dark_gray>")));
        holder.attach(inventory);
        fillPositionPicker(holder);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ 构建界面

    private void fillManage(final Player player, final AdminHolder holder) {
        final ItemStack[] items = new ItemStack[MenuLayout.SIZE];

        final ItemStack divider = named("BLACK_STAINED_GLASS_PANE", "<gray> </gray>", null);
        for (final int slot : AdminLayout.dividerSlots()) {
            items[slot] = divider.clone();
        }

        // 第 1 行第 1 格：添加功能区占位符
        items[AdminLayout.ADD_PLACEHOLDER] = named("HOPPER", "<green>添加功能占位符</green>", List.of(
                plugin.miniRaw("<gray>在功能区末尾添加一个占位符</gray>"),
                plugin.miniRaw("<dark_gray>主菜单不显示，仅管理界面可见</dark_gray>"),
                plugin.miniRaw("<yellow>点击添加</yellow>")));

        // 第 1 行第 3 格：添加自定义插件功能
        items[AdminLayout.ADD_CUSTOM] = named("WRITABLE_BOOK", "<green>添加自定义插件功能</green>", List.of(
                plugin.miniRaw("<gray>输入名称，创建一个可点击执行指令的功能项</gray>"),
                plugin.miniRaw("<dark_gray>默认图标为纸；图标 / 描述 / 指令在 custom-features.yml 配置</dark_gray>"),
                plugin.miniRaw("<yellow>点击输入名称</yellow>")));

        // 左栏：非固定位置功能插件
        final List<MenuFeature> functionFeatures = plugin.features().functionAreaFeatures();
        final int leftPages = pages(functionFeatures.size());
        holder.leftPage(Math.min(holder.leftPage(), leftPages));
        for (int index = 0; index < AdminLayout.PAGE_SIZE; index++) {
            final int featureIndex = (holder.leftPage() - 1) * AdminLayout.PAGE_SIZE + index;
            if (featureIndex >= functionFeatures.size()) {
                break;
            }
            final MenuFeature feature = functionFeatures.get(featureIndex);
            items[AdminLayout.LEFT_SLOTS.get(index)] = entryItem(player, feature,
                    "排序值 " + plugin.features().orderOf(feature));
        }
        items[AdminLayout.LEFT_PREVIOUS] = pageArrow(true, holder.leftPage(), leftPages);
        items[AdminLayout.LEFT_NEXT] = pageArrow(false, holder.leftPage(), leftPages);

        // 右栏：固定位置功能插件
        final List<MenuFeature> fixedFeatures = plugin.features().fixedFeatures();
        final int rightPages = pages(fixedFeatures.size());
        holder.rightPage(Math.min(holder.rightPage(), rightPages));
        for (int index = 0; index < AdminLayout.PAGE_SIZE; index++) {
            final int featureIndex = (holder.rightPage() - 1) * AdminLayout.PAGE_SIZE + index;
            if (featureIndex >= fixedFeatures.size()) {
                break;
            }
            final MenuFeature feature = fixedFeatures.get(featureIndex);
            final Integer position = plugin.features().fixedPositionOf(feature);
            items[AdminLayout.RIGHT_SLOTS.get(index)] = entryItem(player, feature,
                    position == null ? "未设置位置" : "位置 " + MenuLayout.describe(position));
        }
        items[AdminLayout.RIGHT_PREVIOUS] = pageArrow(true, holder.rightPage(), rightPages);
        items[AdminLayout.RIGHT_NEXT] = pageArrow(false, holder.rightPage(), rightPages);

        items[AdminLayout.BACK] = named("BARRIER", "<gray>返回主菜单</gray>", null);

        holder.getInventory().setContents(items);
    }

    private void fillAttribute(final Player player, final AdminHolder holder) {
        final Optional<MenuFeature> found = plugin.features().featureById(holder.featureId());
        if (found.isEmpty()) {
            player.closeInventory();
            return;
        }
        final MenuFeature feature = found.get();

        // 属性页只放三个操作位和返回键，其余格子保持空
        final ItemStack[] items = new ItemStack[MenuLayout.SIZE];

        final boolean hidden = plugin.features().isHidden(feature.id());

        // 第 3 行第 3 格：显示 / 隐藏（黄绿色 = 显示中，红色 = 已隐藏）
        final boolean fixed = holder.mode() == AdminHolder.Mode.ATTRIBUTE_FIXED;
        items[AdminLayout.ATTR_TOGGLE] = named(
                hidden ? "RED_WOOL" : "LIME_WOOL",
                hidden ? "<red>已隐藏</red>" : "<green>显示中</green>",
                List.of(
                        plugin.miniRaw("<gray>功能：<white>" + feature.id() + "</white></gray>"),
                        plugin.miniRaw("<gray>插件：<white>" + feature.owner() + "</white></gray>"),
                        plugin.miniRaw("<dark_gray>点击切换显示 / 隐藏</dark_gray>")));

        if (fixed) {
            // 第 3 行第 5 格：更改位置
            final Integer position = plugin.features().fixedPositionOf(feature);
            items[AdminLayout.ATTR_MIDDLE] = named("ENDER_EYE", "<yellow>更改位置</yellow>", List.of(
                    plugin.miniRaw("<gray>当前位置：<white>"
                            + (position == null ? "未设置" : MenuLayout.describe(position)) + "</white></gray>"),
                    plugin.miniRaw("<dark_gray>点击进入位置选择界面</dark_gray>")));
            // 第 3 行第 7 格：改回非固定位置功能插件
            items[AdminLayout.ATTR_RIGHT] = named("REDSTONE_TORCH", "<yellow>设为非固定位置</yellow>", List.of(
                    plugin.miniRaw("<gray>把该功能变回非固定位置功能插件</gray>"),
                    plugin.miniRaw("<dark_gray>之后按排序值排进功能区</dark_gray>")));
        } else {
            // 第 3 行第 5 格：排序值
            items[AdminLayout.ATTR_MIDDLE] = named("MAP", "<yellow>排序值</yellow>", List.of(
                    plugin.miniRaw("<gray>当前：<white>" + plugin.features().orderOf(feature) + "</white></gray>"),
                    plugin.miniRaw("<dark_gray>左键 +1，右键 -1</dark_gray>"),
                    plugin.miniRaw("<dark_gray>按住 Shift 为 ±4</dark_gray>")));
            // 第 3 行第 7 格：转成固定位置功能插件
            final Integer restore = plugin.features().restorePositionOf(feature.id());
            items[AdminLayout.ATTR_RIGHT] = named("LEVER", "<yellow>设为固定位置</yellow>", List.of(
                    plugin.miniRaw("<gray>把该功能变成固定位置功能插件</gray>"),
                    plugin.miniRaw(restore == null
                            ? "<dark_gray>没设置过位置，将默认放到第 1 行第 1 格</dark_gray>"
                            : "<dark_gray>改回原来的位置：" + MenuLayout.describe(restore) + "</dark_gray>")));
        }

        // 第 6 行第 5 格：返回管理列表
        items[AdminLayout.BACK] = named("BARRIER", "<gray>返回管理列表</gray>", null);

        holder.getInventory().setContents(items);
    }

    private void fillPositionPicker(final AdminHolder holder) {
        final ItemStack[] items = new ItemStack[MenuLayout.SIZE];

        // 跟主菜单一样铺玻璃板
        final ItemStack filler = plugin.configItem("menu.filler");
        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            if (MenuLayout.isFreeSlot(slot)) {
                items[slot] = filler.clone();
            }
        }

        // 各功能占用的位置显示名称（只显示名称，不显示功能本体）
        for (final FeaturePlacement placement : plugin.features().placements()) {
            final Optional<MenuFeature> feature = plugin.features().featureById(placement.id());
            if (feature.isEmpty() || placement.slot() < 0 || placement.slot() >= MenuLayout.SIZE) {
                continue;
            }
            items[placement.slot()] = named("NAME_TAG",
                    "<white>" + feature.get().id() + "</white>",
                    List.of(plugin.miniRaw("<dark_gray>插件：" + feature.get().owner() + "</dark_gray>")));
        }

        // 主菜单的翻页箭头位置：保留但不可选
        items[MenuLayout.PREVIOUS_PAGE_SLOT] = plugin.menus().renderer().pageArrow(true, 1);
        items[MenuLayout.NEXT_PAGE_SLOT] = plugin.menus().renderer().pageArrow(false, 1);
        // 原来管理入口的位置放屏障：取消更改
        items[MenuLayout.ADMIN_SLOT] = named("BARRIER", "<red>取消更改位置</red>", null);

        holder.getInventory().setContents(items);
    }

    // ------------------------------------------------------------------ 点击处理

    public void handleClick(final Player player, final AdminHolder holder, final int slot, final ClickType click) {
        switch (holder.mode()) {
            case MANAGE -> handleManage(player, holder, slot);
            case ATTRIBUTE_FUNCTION -> handleAttributeFunction(player, holder, slot, click);
            case ATTRIBUTE_FIXED -> handleAttributeFixed(player, holder, slot);
            case ATTRIBUTE_PLACEHOLDER -> handleAttributePlaceholder(player, holder, slot, click);
            case POSITION_PICKER -> handlePositionPicker(player, holder, slot);
        }
    }

    private void handleManage(final Player player, final AdminHolder holder, final int slot) {
        if (slot == AdminLayout.ADD_PLACEHOLDER) {
            addPlaceholder(player, holder);
            return;
        }
        if (slot == AdminLayout.ADD_CUSTOM) {
            openCustomFeatureDialog(player);
            return;
        }
        if (slot == AdminLayout.BACK) {
            plugin.menus().open(player);
            return;
        }
        if (slot == AdminLayout.LEFT_PREVIOUS || slot == AdminLayout.LEFT_NEXT) {
            holder.leftPage(holder.leftPage() + (slot == AdminLayout.LEFT_NEXT ? 1 : -1));
            fillManage(player, holder);
            return;
        }
        if (slot == AdminLayout.RIGHT_PREVIOUS || slot == AdminLayout.RIGHT_NEXT) {
            holder.rightPage(holder.rightPage() + (slot == AdminLayout.RIGHT_NEXT ? 1 : -1));
            fillManage(player, holder);
            return;
        }

        final int leftIndex = AdminLayout.LEFT_SLOTS.indexOf(slot);
        if (leftIndex >= 0) {
            final List<MenuFeature> list = plugin.features().functionAreaFeatures();
            final int index = (holder.leftPage() - 1) * AdminLayout.PAGE_SIZE + leftIndex;
            if (index < list.size()) {
                final MenuFeature selected = list.get(index);
                if (selected instanceof PlaceholderFeature) {
                    openPlaceholderAttribute(player, selected.id());
                } else {
                    openAttribute(player, selected.id(), false);
                }
            }
            return;
        }
        final int rightIndex = AdminLayout.RIGHT_SLOTS.indexOf(slot);
        if (rightIndex >= 0) {
            final List<MenuFeature> list = plugin.features().fixedFeatures();
            final int index = (holder.rightPage() - 1) * AdminLayout.PAGE_SIZE + rightIndex;
            if (index < list.size()) {
                openAttribute(player, list.get(index).id(), true);
            }
        }
    }

    private void handleAttributeFunction(final Player player, final AdminHolder holder,
                                         final int slot, final ClickType click) {
        final String id = holder.featureId();
        if (slot == AdminLayout.BACK) {
            openManage(player);
            return;
        }
        if (slot == AdminLayout.ATTR_TOGGLE) {
            plugin.features().setHidden(id, !plugin.features().isHidden(id));
            fillAttribute(player, holder);
            return;
        }
        if (slot == AdminLayout.ATTR_MIDDLE) {
            final Optional<MenuFeature> feature = plugin.features().featureById(id);
            if (feature.isEmpty()) {
                return;
            }
            final int step = switch (click) {
                case LEFT -> 1;
                case RIGHT -> -1;
                case SHIFT_LEFT -> 4;
                case SHIFT_RIGHT -> -4;
                default -> 0;
            };
            if (step != 0) {
                plugin.features().setOrder(id, plugin.features().orderOf(feature.get()) + step);
                fillAttribute(player, holder);
            }
            return;
        }
        if (slot == AdminLayout.ATTR_RIGHT) {
            // 转成固定位置功能插件，并切换到固定位置的属性界面
            plugin.features().makeFixed(id);
            openAttribute(player, id, true);
        }
    }

    private void handleAttributeFixed(final Player player, final AdminHolder holder, final int slot) {
        final String id = holder.featureId();
        if (slot == AdminLayout.BACK) {
            openManage(player);
            return;
        }
        if (slot == AdminLayout.ATTR_TOGGLE) {
            plugin.features().setHidden(id, !plugin.features().isHidden(id));
            fillAttribute(player, holder);
            return;
        }
        if (slot == AdminLayout.ATTR_MIDDLE) {
            openPositionPicker(player, id);
            return;
        }
        if (slot == AdminLayout.ATTR_RIGHT) {
            // 改回非固定位置功能插件，并切换到非固定位置的属性界面
            plugin.features().makeFunction(id);
            openAttribute(player, id, false);
        }
    }

    /** 占位符属性页：只允许调整排序值和删除。 */
    private void handleAttributePlaceholder(final Player player, final AdminHolder holder,
                                            final int slot, final ClickType click) {
        final String id = holder.featureId();
        if (slot == AdminLayout.BACK) {
            openManage(player);
            return;
        }
        if (slot == AdminLayout.ATTR_MIDDLE) {
            final Optional<MenuFeature> feature = plugin.features().featureById(id);
            if (feature.isEmpty()) {
                return;
            }
            final int step = switch (click) {
                case LEFT -> 1;
                case RIGHT -> -1;
                case SHIFT_LEFT -> 4;
                case SHIFT_RIGHT -> -4;
                default -> 0;
            };
            if (step != 0) {
                plugin.features().setOrder(id, plugin.features().orderOf(feature.get()) + step);
                fillPlaceholderAttribute(player, holder);
            }
            return;
        }
        if (slot == AdminLayout.ATTR_RIGHT) {
            if (plugin.features().removePlaceholder(id)) {
                player.sendMessage(plugin.miniRaw("<gray>已删除功能占位符。</gray>"));
            }
            openManage(player);
        }
    }

    private void handlePositionPicker(final Player player, final AdminHolder holder, final int slot) {
        if (slot == MenuLayout.ADMIN_SLOT) {
            // 屏障：取消更改
            openAttribute(player, holder.featureId(), true);
            return;
        }
        if (!AdminLayout.isSelectable(slot)) {
            // 功能区与翻页箭头不能选
            return;
        }
        plugin.features().setPosition(holder.featureId(), slot);
        openAttribute(player, holder.featureId(), true);
    }

    // ------------------------------------------------------------------ 物品构建

    private ItemStack entryItem(final Player viewer, final MenuFeature feature, final String stateLine) {
        if (feature instanceof PlaceholderFeature) {
            final ItemStack item = feature.icon(viewer);
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(
                        plugin.miniRaw("<gray>占位符：仅管理界面可见</gray>"),
                        plugin.miniRaw("<gray>" + stateLine + "</gray>"),
                        plugin.miniRaw("<yellow>点击调整排序值或删除</yellow>")));
                item.setItemMeta(meta);
            }
            return item;
        }
        final ItemStack item = feature.icon(viewer);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final List<Component> own = feature.description(viewer);
            final List<Component> lore = own == null ? new ArrayList<>() : new ArrayList<>(own);
            lore.add(plugin.miniRaw("<dark_gray>插件：<gray>" + feature.owner() + "</gray></dark_gray>"));
            lore.add(plugin.miniRaw("<dark_gray>id：<gray>" + feature.id() + "</gray></dark_gray>"));
            lore.add(plugin.miniRaw("<gray>" + stateLine + "</gray>"));
            if (plugin.features().isHidden(feature.id())) {
                lore.add(plugin.miniRaw("<red>已隐藏</red>"));
            }
            lore.add(plugin.miniRaw("<yellow>点击进入属性</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pageArrow(final boolean previous, final int page, final int pages) {
        final ItemStack item = named(previous ? "ARROW" : "ARROW",
                previous ? "<yellow>上一页</yellow>" : "<yellow>下一页</yellow>",
                List.of(plugin.miniRaw("<gray>第 <white>" + page + "</white>/<white>" + pages + "</white> 页</gray>")));
        return item;
    }

    private ItemStack named(final String materialName, final String name, final List<Component> lore) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) {
            material = Material.STONE;
        }
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.miniRaw(name).decorationIfAbsent(
                    TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int pages(final int size) {
        return Math.max(1, (size + AdminLayout.PAGE_SIZE - 1) / AdminLayout.PAGE_SIZE);
    }
}
