package com.example.town.arcui;

import com.example.town.TownPlugin;
import com.fentai.arcmenu.api.ArcMenuPointerButton;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

/**
 * 界面基类：负责元素命中测试（点击 / 悬停）、按钮绘制与公共边框。
 * 子类实现 {@link #draw()}；坐标全部是 canvas 设计像素（480×270）。
 */
public abstract class Screen {

    /** 可点击区域（含高亮面板句柄）。 */
    protected static final class Hotspot {
        final double x;
        final double y;
        final double w;
        final double h;
        final boolean leftOnly;
        final Runnable action;
        Renderer.PanelHandle panel;

        Hotspot(double x, double y, double w, double h, boolean leftOnly, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.leftOnly = leftOnly;
            this.action = action;
        }

        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    protected final UiSession ui;
    protected final Renderer r;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private Hotspot hovered;

    protected Screen(UiSession ui) {
        this.ui = ui;
        this.r = ui.renderer();
    }

    protected TownPlugin plugin() {
        return ui.plugin();
    }

    /** 绘制界面（redraw 时调用；此时画布已清空）。 */
    public abstract void draw();

    /** 离开界面（清栈 / 被覆盖）时的清理。 */
    public void onLeave() {
    }

    /** 周期刷新（默认 1 秒）。 */
    public void tick() {
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    public void activate(double px, double py, ArcMenuPointerButton button, boolean sneaking) {
        for (Hotspot hotspot : hotspots) {
            if (hotspot.contains(px, py)) {
                if (!hotspot.leftOnly || button == ArcMenuPointerButton.LEFT) {
                    hotspot.action.run();
                }
                return;
            }
        }
    }

    public void hover(double px, double py) {
        Hotspot hit = null;
        for (Hotspot hotspot : hotspots) {
            if (hotspot.contains(px, py)) {
                hit = hotspot;
                break;
            }
        }
        if (hit == hovered) {
            return;
        }
        if (hovered != null && hovered.panel != null) {
            hovered.panel.setHovered(false);
        }
        hovered = hit;
        if (hovered != null && hovered.panel != null) {
            hovered.panel.setHovered(true);
        }
    }

    // ------------------------------------------------------------------
    // 绘制辅助
    // ------------------------------------------------------------------

    /** 注册一个按钮：面板 + 居中文字 + 点击动作。 */
    protected void button(double x, double y, double w, double h,
                          String miniText, double fontPx, Runnable action) {
        Renderer.PanelHandle panel = r.panel(x, y, w, h, Material.GRAY_CONCRETE);
        r.label(x + w / 2, y + h / 2, fontPx, mini(miniText));
        Hotspot hotspot = new Hotspot(x, y, w, h, true, action);
        hotspot.panel = panel;
        hotspots.add(hotspot);
    }

    /** 静态文字。 */
    protected Renderer.LabelHandle label(double centerX, double centerY, double fontPx, String miniText) {
        return r.label(centerX, centerY, fontPx, mini(miniText));
    }

    /** 静态面板。 */
    protected Renderer.PanelHandle panel(double x, double y, double w, double h, Material material) {
        return r.panel(x, y, w, h, material);
    }

    /** 注册热点（无面板，命中区域）。 */
    protected void hotspot(double x, double y, double w, double h,
                           boolean leftOnly, Runnable action) {
        hotspots.add(new Hotspot(x, y, w, h, leftOnly, action));
    }

    protected void bindPanel(double x, double y, double w, double h, Renderer.PanelHandle panel,
                             Runnable action) {
        Hotspot hotspot = new Hotspot(x, y, w, h, false, action);
        hotspot.panel = panel;
        hotspots.add(hotspot);
    }

    /** 公共边框：标题 + 返回 / 关闭。 */
    protected void chrome(String titleMini) {
        r.panel(4, 4, UiMetrics.CANVAS_W - 8, UiMetrics.CANVAS_H - 8, Material.BLACK_CONCRETE);
        label(UiMetrics.CANVAS_W / 2, 18, 14, titleMini);
        button(10, 10, 56, 20, "<gray>← 返回", 10, ui::back);
        button(UiMetrics.CANVAS_W - 66, 10, 56, 20, "<red>✕ 关闭", 10, ui::close);
    }

    protected Component mini(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }

    protected List<Hotspot> hotspots() {
        return hotspots;
    }
}
