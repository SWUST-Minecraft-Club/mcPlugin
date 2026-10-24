package com.example.town.arcui;

import com.example.town.TownPlugin;
import com.fentai.arcmenu.api.ArcMenuApplicationContext;
import com.fentai.arcmenu.api.ArcMenuSurface;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 极简界面渲染器：所有界面元素 = 私有 display 实体。
 * - 面板 / 按钮：BlockDisplay（混凝土方块压扁成纯色矩形，尺寸精确、零字体度量依赖）；
 * - 文字：TextDisplay（服务端动态内容，无占位符体系依赖）；
 * - 图标：ItemDisplay。
 * 全部实体经由 ArcMenu 上下文生成（每玩家私有、会话结束自动回收），所有变更走 ctx.execute。
 */
public final class Renderer {

    /** 面板层 Z（设计像素，正值更靠近玩家）。 */
    public static final double Z_PANEL = 0;
    /** 内容层 Z。 */
    public static final double Z_CONTENT = 1.5;
    /** 文字层 Z。 */
    public static final double Z_TEXT = 3;

    private final TownPlugin plugin;
    private final ArcMenuApplicationContext ctx;
    private ArcMenuSurface surface;
    private float yaw;
    private float pitch;
    private final List<Entity> spawned = new ArrayList<>();

    public Renderer(TownPlugin plugin, ArcMenuApplicationContext ctx) {
        this.plugin = plugin;
        this.ctx = ctx;
    }

    /** 绑定屏幕平面（onSurfaceChanged 时调用）。 */
    public void attach(ArcMenuSurface newSurface) {
        this.surface = newSurface;
        var normal = newSurface.normal();
        this.yaw = (float) Math.toDegrees(Math.atan2(-normal.getX(), normal.getZ()));
        this.pitch = (float) Math.toDegrees(-Math.asin(normal.getY()));
    }

    /** 清空全部实体。 */
    public void clear() {
        ctx.execute(() -> {
            for (Entity entity : spawned) {
                try {
                    entity.remove();
                } catch (Exception ignored) {
                    // 会话关闭竞态下实体可能已被 ArcMenu 回收
                }
            }
            spawned.clear();
        });
    }

    // ------------------------------------------------------------------
    // 基础绘制
    // ------------------------------------------------------------------

    /** 纯色矩形面板。 */
    public PanelHandle panel(double x, double y, double w, double h, Material material) {
        PanelHandle handle = new PanelHandle(ctx, material);
        ctx.execute(() -> {
            Location loc = world(x + w / 2, y + h / 2, Z_PANEL);
            BlockDisplay display = ctx.spawnPrivate(loc, BlockDisplay.class, disp -> {
                disp.setBlock(material.createBlockData());
                disp.setTransformation(new Transformation(
                        new Vector3f((float) -UiMetrics.px(w) / 2,
                                (float) -UiMetrics.px(h) / 2, 0),
                        new Quaternionf(),
                        new Vector3f((float) UiMetrics.px(w),
                                (float) UiMetrics.px(h), 0.02f),
                        new Quaternionf()));
                common(disp);
            });
            if (display != null) {
                handle.bind(display);
                spawned.add(display);
            }
        });
        return handle;
    }

    /** 文字（给定中心点与像素字号）。 */
    public LabelHandle label(double centerX, double centerY, double fontPx, Component text) {
        LabelHandle handle = new LabelHandle(ctx);
        ctx.execute(() -> {
            Location loc = world(centerX, centerY + UiMetrics.TEXT_Y_NUDGE, Z_TEXT);
            TextDisplay display = ctx.spawnPrivate(loc, TextDisplay.class, disp -> {
                disp.text(text);
                disp.setBillboard(Display.Billboard.FIXED);
                disp.setAlignment(TextDisplay.TextAlignment.CENTER);
                disp.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                disp.setDefaultBackground(false);
                disp.setShadowed(false);
                disp.setSeeThrough(false);
                float scale = UiMetrics.textScale(fontPx);
                disp.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0), new Quaternionf(),
                        new Vector3f(scale, scale, scale), new Quaternionf()));
                common(disp);
            });
            if (display != null) {
                handle.bind(display);
                spawned.add(display);
            }
        });
        return handle;
    }

    /** 物品图标（给定中心点与像素尺寸）。 */
    public ItemHandle item(double centerX, double centerY, double sizePx, ItemStack stack) {
        ItemHandle handle = new ItemHandle(ctx);
        ctx.execute(() -> {
            Location loc = world(centerX, centerY, Z_CONTENT);
            ItemDisplay display = ctx.spawnPrivate(loc, ItemDisplay.class, disp -> {
                disp.setItemStack(stack);
                disp.setBillboard(Display.Billboard.FIXED);
                float scale = (float) UiMetrics.px(sizePx);
                disp.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0), new Quaternionf(),
                        new Vector3f(scale, scale, scale), new Quaternionf()));
                common(disp);
            });
            if (display != null) {
                handle.bind(display);
                spawned.add(display);
            }
        });
        return handle;
    }

    private void common(Display display) {
        display.setInterpolationDuration(2);
        display.setTeleportDuration(1);
        display.setPersistent(false);
    }

    /** 平面坐标 → 世界坐标（含朝向）。 */
    private Location world(double x, double y, double z) {
        World world = surface.getWorld();
        Location loc = surface.toWorld(x, y, z);
        if (loc == null) {
            loc = surface.origin().clone();
        }
        loc.setWorld(world);
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        return loc;
    }

    // ------------------------------------------------------------------
    // 句柄
    // ------------------------------------------------------------------

    /** 面板句柄：换材质（悬停高亮）用。 */
    public static final class PanelHandle {
        private final ArcMenuApplicationContext ctx;
        private final Material normal;
        private final Material hovered;
        private BlockDisplay entity;

        private PanelHandle(ArcMenuApplicationContext ctx, Material material) {
            this.ctx = ctx;
            this.normal = material;
            this.hovered = hoverVariant(material);
        }

        void bind(BlockDisplay display) {
            this.entity = display;
        }

        private static Material hoverVariant(Material material) {
            if (material == Material.LIGHT_GRAY_CONCRETE) {
                return Material.WHITE_CONCRETE;
            }
            if (material == Material.GRAY_CONCRETE) {
                return Material.LIGHT_GRAY_CONCRETE;
            }
            if (material == Material.BLUE_CONCRETE) {
                return Material.LIGHT_BLUE_CONCRETE;
            }
            if (material == Material.CYAN_CONCRETE) {
                return Material.LIGHT_BLUE_CONCRETE;
            }
            return material;
        }

        public void setHovered(boolean hoveredState) {
            ctx.execute(() -> {
                if (entity != null && entity.isValid()) {
                    entity.setBlock((hoveredState ? hovered : normal).createBlockData());
                }
            });
        }

        /** 换成指定材质（状态着色用）。 */
        public void setMaterial(Material material) {
            ctx.execute(() -> {
                if (entity != null && entity.isValid()) {
                    entity.setBlock(material.createBlockData());
                }
            });
        }
    }

    /** 文字句柄：更新内容用。 */
    public static final class LabelHandle {
        private final ArcMenuApplicationContext ctx;
        private TextDisplay entity;

        private LabelHandle(ArcMenuApplicationContext ctx) {
            this.ctx = ctx;
        }

        void bind(TextDisplay display) {
            this.entity = display;
        }

        public void update(Component text) {
            ctx.execute(() -> {
                if (entity != null && entity.isValid()) {
                    entity.text(text);
                }
            });
        }
    }

    /** 图标句柄。 */
    public static final class ItemHandle {
        private final ArcMenuApplicationContext ctx;
        private ItemDisplay entity;

        private ItemHandle(ArcMenuApplicationContext ctx) {
            this.ctx = ctx;
        }

        void bind(ItemDisplay display) {
            this.entity = display;
        }

        public void update(ItemStack stack) {
            ctx.execute(() -> {
                if (entity != null && entity.isValid()) {
                    entity.setItemStack(stack);
                }
            });
        }
    }
}
