package com.example.town.arcui;

import com.example.town.model.ChunkKey;
import com.example.town.model.TownSnapshotView;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * 领地圈地地图：9×9 网格，以玩家所在区块为中心（可一键回到脚下）。
 * 左键可买的区块 → 扣费圈地；右键本镇区块 → 确认放弃。必须与已有领地相连。
 * 悬停格子在底部状态栏显示该格信息；每秒刷新状态着色。
 */
public final class ClaimMapScreen extends Screen {

    private static final int N = 9;
    private static final double CELL = 24;
    private static final double GAP = 2;
    private static final double GRID_X = 14;
    private static final double GRID_Y = 34;

    private ChunkKey center;
    private final Renderer.PanelHandle[][] cellPanels = new Renderer.PanelHandle[N][N];
    private final String[][] cellStates = new String[N][N];
    private Renderer.LabelHandle status;
    private int hoverI = -1;
    private int hoverJ = -1;

    public ClaimMapScreen(UiSession ui) {
        super(ui);
    }

    @Override
    public void draw() {
        chrome("<green>领地圈地");
        if (center == null) {
            center = chunkOf(ui.ctx().getPlayer());
        }
        drawGrid();
        drawSidePanel();
    }

    private void drawGrid() {
        // 中心格金色描边（玩家所在区块）
        double cx = GRID_X + (N / 2) * (CELL + GAP);
        double cy = GRID_Y + (N / 2) * (CELL + GAP);
        panel(cx - 1.5, cy - 1.5, CELL + 3, CELL + 3, Material.YELLOW_CONCRETE);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double x = GRID_X + i * (CELL + GAP);
                double y = GRID_Y + j * (CELL + GAP);
                String state = stateOf(i, j);
                cellStates[i][j] = state;
                cellPanels[i][j] = panel(x, y, CELL, CELL, materialOf(state));
            }
        }
        status = label(GRID_X + (N * (CELL + GAP) - GAP) / 2, GRID_Y + N * (CELL + GAP) + 8, 9,
                "<dark_gray>左键圈地 · 右键放弃本镇区块");
    }

    private void drawSidePanel() {
        var view = ownTown();
        double px = GRID_X + N * (CELL + GAP) + 10;
        panel(px, GRID_Y, 442 - px, 168, Material.GRAY_CONCRETE);
        label((px + 442) / 2, GRID_Y + 16, 11, "<white>" + (view == null ? "?" : view.name()));
        label((px + 442) / 2, GRID_Y + 40, 9,
                "<gray>扩张单价：<white>" + trim(plugin().config().pricePerChunk())
                        + " <gray>" + currencySymbol());
        label((px + 442) / 2, GRID_Y + 58, 9,
                "<gray>我的领地：<white>" + (view == null ? 0 : view.claimCount())
                        + maxChunksText());
        label((px + 442) / 2, GRID_Y + 76, 9,
                "<gray>中心：<white>" + (center == null ? "?" : center.x() + ", " + center.z()));
        // 图例
        legend(px + 14, GRID_Y + 96, Material.LIME_CONCRETE, "本镇");
        legend(px + 14, GRID_Y + 112, Material.CYAN_CONCRETE, "可购买");
        legend(px + 110, GRID_Y + 96, Material.RED_CONCRETE, "他镇");
        legend(px + 110, GRID_Y + 112, Material.GRAY_CONCRETE, "不相连");
        legend(px + 14, GRID_Y + 128, Material.YELLOW_CONCRETE, "你所在区块");
        legend(px + 110, GRID_Y + 128, Material.BROWN_CONCRETE, "世界禁圈");
        button((px + 442) / 2 - 70, GRID_Y + 140, 140, 22, "<aqua>回到我的脚下", 9,
                () -> {
                    center = chunkOf(ui.ctx().getPlayer());
                    ui.redraw();
                });
    }

    private void legend(double x, double y, Material material, String text) {
        panel(x, y - 3, 8, 8, material);
        label(x + 40, y + 1, 8, "<gray>" + text);
    }

    // ------------------------------------------------------------------
    // 交互（格子不进通用 hotspot，需要区分左右键）
    // ------------------------------------------------------------------

    @Override
    public void activate(double px, double py, com.fentai.arcmenu.api.ArcMenuPointerButton button,
                         boolean sneaking) {
        int[] cell = cellAt(px, py);
        if (cell == null) {
            super.activate(px, py, button, sneaking);
            return;
        }
        ChunkKey key = keyAt(cell[0], cell[1]);
        String state = cellStates[cell[0]][cell[1]];
        var service = plugin().service();
        if (button == com.fentai.arcmenu.api.ArcMenuPointerButton.RIGHT) {
            if ("own".equals(state) || "hall".equals(state)) {
                if ("hall".equals(state)) {
                    setStatus("<red>镇政厅所在区块不能放弃。");
                    return;
                }
                service.unclaim(self(), key, deliver());
            }
            return;
        }
        switch (state) {
            case "buy" -> service.claim(self(), key, deliver());
            case "own" -> setStatus("<green>这是本镇领地。");
            case "hall" -> setStatus("<gold>这里是镇政厅（中心区块）。");
            case "other" -> setStatus("<red>这里属于其他小镇。");
            case "far" -> setStatus("<gray>必须与小镇现有领地相连（相邻一格）。");
            case "max" -> setStatus("<red>已达到本镇地块上限。");
            default -> setStatus("<red>这个世界不允许圈地。");
        }
    }

    @Override
    public void hover(double px, double py) {
        int[] cell = cellAt(px, py);
        int newI = cell == null ? -1 : cell[0];
        int newJ = cell == null ? -1 : cell[1];
        if (newI != hoverI || newJ != hoverJ) {
            hoverI = newI;
            hoverJ = newJ;
            if (cell != null) {
                ChunkKey key = keyAt(cell[0], cell[1]);
                setStatus("<gray>区块 <white>" + key.x() + ", " + key.z()
                        + "</white> · " + describe(cellStates[cell[0]][cell[1]]));
            }
        }
        super.hover(px, py);
    }

    @Override
    public void tick() {
        // 每秒刷新状态着色（他人圈地 / 自己购买后的变化）
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                String state = stateOf(i, j);
                if (!state.equals(cellStates[i][j]) && cellPanels[i][j] != null) {
                    cellStates[i][j] = state;
                    cellPanels[i][j].setMaterial(materialOf(state));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 状态计算
    // ------------------------------------------------------------------

    /** own / hall / other / buy / far / max / denied */
    private String stateOf(int i, int j) {
        ChunkKey key = keyAt(i, j);
        var service = plugin().service();
        TownSnapshotView owner = service.townAt(key);
        TownSnapshotView mine = ownTown();
        if (owner != null) {
            if (mine != null && owner.id().equals(mine.id())) {
                ChunkKey hall = mine.hallChunk();
                return hall != null && hall.equals(key) ? "hall" : "own";
            }
            return "other";
        }
        if (mine == null) {
            return "denied";
        }
        if (!service.worldAllowed(key.world())) {
            return "denied";
        }
        int maxChunks = plugin().config().maxChunksPerTown();
        if (maxChunks > 0 && mine.claimCount() >= maxChunks) {
            return "max";
        }
        boolean diagonal = plugin().config().diagonalConnected();
        for (ChunkKey claim : mine.claims()) {
            if (claim.isAdjacentTo(key, diagonal)) {
                return "buy";
            }
        }
        return "far";
    }

    private Material materialOf(String state) {
        return switch (state) {
            case "own", "hall" -> Material.LIME_CONCRETE;
            case "other" -> Material.RED_CONCRETE;
            case "buy" -> Material.CYAN_CONCRETE;
            case "max" -> Material.LIGHT_GRAY_CONCRETE;
            case "denied" -> Material.BROWN_CONCRETE;
            default -> Material.GRAY_CONCRETE;
        };
    }

    private String describe(String state) {
        return switch (state) {
            case "own" -> "本镇领地（右键放弃）";
            case "hall" -> "镇政厅中心";
            case "other" -> "他镇领地";
            case "buy" -> "可购买（左键）";
            case "far" -> "不相连";
            case "max" -> "已达上限";
            default -> "不可圈地";
        };
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private ChunkKey keyAt(int i, int j) {
        return new ChunkKey(center.world(), center.x() + i - N / 2, center.z() + j - N / 2);
    }

    private int[] cellAt(double px, double py) {
        double dx = px - GRID_X;
        double dy = py - GRID_Y;
        if (dx < 0 || dy < 0) {
            return null;
        }
        int i = (int) (dx / (CELL + GAP));
        int j = (int) (dy / (CELL + GAP));
        if (i >= N || j >= N || dx > i * (CELL + GAP) + CELL || dy > j * (CELL + GAP) + CELL) {
            return null;
        }
        return new int[]{i, j};
    }

    private void setStatus(String mini) {
        if (status != null) {
            status.update(mini(mini));
        }
    }

    private java.util.UUID selfId() {
        return ui.ctx().getPlayer().getUniqueId();
    }

    private TownSnapshotView ownTown() {
        return plugin().service().townOf(selfId());
    }

    private ChunkKey chunkOf(Player player) {
        try {
            return ChunkKey.of(player.getLocation());
        } catch (Exception e) {
            // ArcMenu 线程读取位置受限时退回原点
            return new ChunkKey(player.getWorld().getName(), 0, 0);
        }
    }

    private Player self() {
        return ui.ctx().getPlayer();
    }

    private Consumer<com.example.town.service.TownService.Result> deliver() {
        return result -> {
            plugin().service().deliver(self(), result);
            ui.ctx().execute(ui::redraw);
        };
    }

    private String maxChunksText() {
        int max = plugin().config().maxChunksPerTown();
        return max > 0 ? " <gray>/ " + max : "";
    }

    private String currencySymbol() {
        var wallet = plugin().service().wallet();
        if (wallet == null) {
            return "";
        }
        return wallet.symbolOf(plugin().config().currencyCode(),
                wallet.resolveCurrency(plugin().config().currencyCode()));
    }

    private static String trim(double amount) {
        return amount == Math.floor(amount)
                ? String.valueOf((long) amount)
                : String.valueOf(amount);
    }
}
