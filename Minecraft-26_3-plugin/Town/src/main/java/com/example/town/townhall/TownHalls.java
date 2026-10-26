package com.example.town.townhall;

import com.example.town.TownPlugin;
import com.example.town.TownRegistry;
import com.example.town.hook.CraftEngineHook;
import com.example.town.model.ChunkKey;
import com.example.town.model.Town;
import com.example.town.util.Threads;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 镇政厅道具：CE 自定义物品优先，缺失回落原版材质；统一打 PDC 标记便于识别。
 * 放置 / 破坏交互见 {@code TownHallListener}。
 */
public final class TownHalls {

    /** PDC 标记：值为 1 的物品是镇政厅道具。 */
    public final NamespacedKey hallKey;
    /** 放置冷却：防止一次右键触发两次事件。 */
    private final TownPlugin plugin;
    private final CraftEngineHook craftEngine;

    public TownHalls(TownPlugin plugin, CraftEngineHook craftEngine) {
        this.plugin = plugin;
        this.craftEngine = craftEngine;
        this.hallKey = new NamespacedKey(plugin, "hall_item");
    }

    /** 发一枚镇政厅给玩家（背包满则掉在脚下）。 */
    public void give(Player player) {
        if(plugin.market()!=null && plugin.market().frozen(player.getUniqueId())) {
            player.sendPlainMessage("[小镇] 物品交付处理中，请完成后再领取政厅核心。");return;
        }
        ItemStack item = build();
        var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover.get(0));
        }
        player.sendMessage(plugin.messages().render("hall-received"));
    }

    /** 构建镇政厅道具。 */
    public ItemStack build() {
        String ceId = plugin.config().craftEngineId();
        ItemStack stack = craftEngine != null ? craftEngine.buildItem(ceId) : null;
        if (stack == null) {
            stack = new ItemStack(plugin.config().hallMaterial());
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(hallKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(MiniMessage.miniMessage().deserialize(
                "<!italic><gold>镇政厅 <gray>(右键方块放置)"));
        meta.lore(java.util.List.of(
                MiniMessage.miniMessage().deserialize("<!italic><gray>放置后成为小镇中心："),
                MiniMessage.miniMessage().deserialize("<!italic><gray>· 设定小镇传送点"),
                MiniMessage.miniMessage().deserialize("<!italic><gray>· 免费圈下初始领地"),
                MiniMessage.miniMessage().deserialize("<!italic><gray>仅镇长可放置，迁址请使用规划服务")));
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isHallItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .has(hallKey, PersistentDataType.BYTE);
    }

    /** 消耗手中一枚镇政厅道具。 */
    public void consumeOne(PlayerInteractEvent event) {
        var stack = event.getPlayer().getInventory().getItemInMainHand();
        if (stack == null) {
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        event.getPlayer().getInventory().setItemInMainHand(stack);
    }

    public boolean place(org.bukkit.block.Block block) {
        String id=plugin.config().craftEngineId();
        if(craftEngine!=null && !id.isBlank()) return craftEngine.placeBlock(block,id);
        block.setType(plugin.config().hallMaterial(),false);return true;
    }

    /**
     * 该方块是否是某个小镇的镇政厅本体；是则返回小镇。
     * 供监听器高频调用，内部持注册表锁。
     */
    public Town hallAt(org.bukkit.block.Block block) {
        ChunkKey key = ChunkKey.of(block.getLocation());
        try (TownRegistry.Guard ignored = plugin.registry().guard()) {
            Town town = plugin.registry().byChunk(key);
            if (town == null || !town.isHallSet()) {
                return null;
            }
            if (town.hallWorld().equals(block.getWorld().getName())
                    && town.hallX() == block.getX()
                    && town.hallY() == block.getY()
                    && town.hallZ() == block.getZ()) {
                return town;
            }
            return null;
        }
    }

    /** 挖掉镇政厅方块后的落地方块清理（服务层负责数据与道具）。 */
    public void clearBlock(org.bukkit.block.Block block) {
        block.setType(org.bukkit.Material.AIR);
    }
}
