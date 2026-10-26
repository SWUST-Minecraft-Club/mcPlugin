package com.example.minecraftmcp.mc26_3;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import java.lang.reflect.Field;
import java.util.*;
import static com.example.minecraftmcp.mc26_3.Jsons.*;

final class StateReader {
    private final Minecraft client;
    private final Field chatMessages;

    StateReader(Minecraft client) {
        this.client = client;
        try {
            chatMessages = ChatComponent.State.class.getDeclaredField("messages");
            chatMessages.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("26.3 聊天结构不匹配", e); }
    }

    void requirePlayer() {
        if (client.player == null || client.level == null || client.gameMode == null)
            throw new IllegalStateException("尚未进入世界或已断开连接");
    }

    JsonObject player() {
        requirePlayer();
        var player = client.player;
        return object("name", player.getPlainTextName(), "uuid", player.getUUID(), "position", vector(player.position()),
                "velocity", vector(player.getDeltaMovement()), "yaw", player.getYRot(), "pitch", player.getXRot(),
                "health", player.getHealth(), "maxHealth", player.getMaxHealth(), "alive", player.isAlive(),
                "food", player.getFoodData().getFoodLevel(), "saturation", player.getFoodData().getSaturationLevel(),
                "experienceLevel", player.experienceLevel, "experienceProgress", player.experienceProgress,
                "onGround", player.onGround(), "sprinting", player.isSprinting(), "sneaking", player.isShiftKeyDown(),
                "gameMode", client.gameMode.getPlayerMode().getName(), "selectedHotbar", player.getInventory().getSelectedSlot(),
                "mainHand", item(player.getMainHandItem(), false, client), "offHand", item(player.getOffhandItem(), false, client));
    }

    JsonObject inventory(JsonObject arguments) {
        requirePlayer();
        String scope = text(arguments, "scope", "player");
        boolean includeEmpty = bool(arguments, "includeEmpty");
        JsonArray slots = new JsonArray();
        if (scope.equals("player")) {
            var inventory = client.player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (includeEmpty || !stack.isEmpty()) slots.add(object("slot", i, "item", item(stack, false, client)));
            }
            return object("scope", scope, "size", inventory.getContainerSize(), "selectedHotbar", inventory.getSelectedSlot(), "slots", slots);
        }
        AbstractContainerMenu menu = menu();
        for (Slot slot : menu.slots) {
            if (includeEmpty || slot.hasItem()) slots.add(object("slot", slot.index, "inventorySlot", slot.getContainerSlot(),
                    "active", slot.isActive(), "item", item(slot.getItem(), false, client)));
        }
        return object("scope", scope, "menuId", menu.containerId, "menuClass", menu.getClass().getName(),
                "stateId", menu.getStateId(), "size", menu.slots.size(), "slots", slots,
                "carried", item(menu.getCarried(), false, client), "creative", client.gui.screen() instanceof CreativeModeInventoryScreen);
    }

    JsonObject inspectItem(JsonObject arguments) {
        requirePlayer();
        int slot = integer(arguments, "slot", -1);
        boolean container = text(arguments, "scope", "player").equals("container");
        int size = container ? menu().slots.size() : client.player.getInventory().getContainerSize();
        if (slot < 0 || slot >= size) throw new IllegalArgumentException("槽位超出范围：0.." + (size - 1));
        ItemStack stack = container ? menu().getSlot(slot).getItem() : client.player.getInventory().getItem(slot);
        return object("scope", container ? "container" : "player", "slot", slot, "item", item(stack, true, client));
    }

    JsonObject clickSlot(JsonObject arguments) {
        requirePlayer();
        if (client.gui.screen() instanceof CreativeModeInventoryScreen)
            throw new IllegalStateException("创造背包使用独立客户端槽位，请使用 ui.click 点击 ui.inspect 返回的槽位坐标");
        AbstractContainerMenu menu = client.player.containerMenu;
        if (integer(arguments, "menuId", -1) != menu.containerId) throw new IllegalStateException("菜单已变化，请重新 inventory.list");
        int slot = integer(arguments, "slot", -1), button = integer(arguments, "button", 0);
        ContainerInput action = ContainerInput.valueOf(text(arguments, "action", "PICKUP"));
        if (slot != -999 && (slot < 0 || slot >= menu.slots.size())) throw new IllegalArgumentException("容器槽位越界");
        if (slot == -999 && action != ContainerInput.PICKUP) throw new IllegalArgumentException("-999 仅支持 PICKUP 丢出光标物品");
        if (action == ContainerInput.SWAP ? button > 8 : action == ContainerInput.CLONE ? button != 2 : button > 1)
            throw new IllegalArgumentException("button 与 action 不匹配；SWAP 为 0..8，CLONE 为 2，其他为 0/1");
        client.gameMode.handleContainerInput(menu.containerId, slot, button, action, client.player);
        return object("sent", true, "menuId", menu.containerId, "slot", slot, "action", action.name(),
                "verification", "本地预测已更新；请用 test.wait_for 等待服务端同步");
    }

    AbstractContainerMenu menu() {
        requirePlayer();
        return client.gui.screen() instanceof AbstractContainerScreen<?> container ? container.getMenu() : client.player.containerMenu;
    }

    JsonObject world() {
        requirePlayer();
        JsonObject data = object("dimension", client.level.dimension().identifier(), "gameTime", client.level.getGameTime(),
                "raining", client.level.isRaining(), "thundering", client.level.isThundering(),
                "difficulty", client.level.getDifficulty().getSerializedName(), "entityCount", client.level.getEntityCount());
        HitResult target = client.hitResult;
        JsonObject hit = object("type", target == null ? "NONE" : target.getType().name());
        if (target != null) hit.add("position", vector(target.getLocation()));
        if (target instanceof BlockHitResult blockHit) {
            hit.addProperty("face", blockHit.getDirection().getName());
            hit.add("block", block(blockHit.getBlockPos()));
        }
        if (target instanceof EntityHitResult entityHit) hit.add("entity", entity(entityHit.getEntity()));
        data.add("target", hit);
        return data;
    }

    JsonObject block(BlockPos position) {
        requirePlayer();
        if (!client.level.hasChunkAt(position)) throw new IllegalStateException("目标区块尚未加载");
        var state = client.level.getBlockState(position);
        JsonObject properties = new JsonObject();
        state.getValues().forEach(value -> properties.addProperty(value.property().getName(), value.value().toString()));
        return object("x", position.getX(), "y", position.getY(), "z", position.getZ(),
                "id", BuiltInRegistries.BLOCK.getKey(state.getBlock()), "air", state.isAir(), "properties", properties);
    }

    JsonObject entities(JsonObject arguments) {
        requirePlayer();
        double radius = number(arguments, "radius", 32);
        int limit = integer(arguments, "limit", 64);
        List<Entity> found = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != client.player && entity.distanceToSqr(client.player) <= radius * radius) found.add(entity);
        }
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player)));
        JsonArray results = new JsonArray();
        found.stream().limit(limit).forEach(entity -> results.add(entity(entity)));
        return object("entities", results, "totalWithinRadius", found.size(), "truncated", found.size() > limit);
    }

    JsonObject chat(int limit) {
        JsonArray messages = new JsonArray();
        try {
            Object data = chatMessages.get(client.gui.hud.getChat().storeState());
            if (!(data instanceof List<?> history)) throw new IllegalStateException("聊天数据结构不匹配");
            for (Object entry : history) {
                if (messages.size() >= limit) break;
                if (entry instanceof GuiMessage message) messages.add(object("text", bounded(message.content().getString(), 8192), "addedTick", message.addedTime()));
            }
        } catch (IllegalAccessException e) { throw new IllegalStateException("聊天历史访问失败", e); }
        return object("order", "newest_first", "messages", messages);
    }

    static JsonObject item(ItemStack stack, boolean details, Minecraft client) {
        JsonObject data = object("empty", stack.isEmpty(), "id", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                "count", stack.getCount(), "name", bounded(stack.getHoverName().getString(), 2048), "maxStackSize", stack.getMaxStackSize());
        if (stack.isDamageableItem()) {
            data.addProperty("damage", stack.getDamageValue()); data.addProperty("maxDamage", stack.getMaxDamage());
        }
        if (details && !stack.isEmpty()) {
            JsonArray tooltip = new JsonArray();
            var lines = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.ADVANCED);
            lines.stream().limit(256).forEach(line -> tooltip.add(bounded(line.getString(), 4096)));
            data.add("tooltip", tooltip);
            data.addProperty("tooltipTruncated", lines.size() > 256);
            JsonObject components = new JsonObject();
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (var component : stack.getComponents()) {
                var encoded = component.encodeValue(ops).result();
                JsonElement value = encoded.orElseGet(() -> object("serializable", false));
                if (value.toString().length() > 65536) value = object("truncated", true);
                components.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).toString(), value);
            }
            data.add("components", components);
        }
        return data;
    }
    private JsonObject entity(Entity entity) {
        JsonObject result = object("id", entity.getId(), "uuid", entity.getUUID(), "type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                "name", bounded(entity.getName().getString(), 2048), "position", vector(entity.position()), "alive", entity.isAlive(),
                "distance", Math.sqrt(entity.distanceToSqr(client.player)));
        if (entity instanceof LivingEntity living) { result.addProperty("health", living.getHealth()); result.addProperty("maxHealth", living.getMaxHealth()); }
        return result;
    }
    static JsonObject vector(Vec3 vector) { return object("x", vector.x, "y", vector.y, "z", vector.z); }
    private static String bounded(String text, int max) { return text.length() <= max ? text : text.substring(0, max) + "…"; }
}
