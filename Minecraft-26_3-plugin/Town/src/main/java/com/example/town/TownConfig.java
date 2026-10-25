package com.example.town;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

/**
 * 配置的不可变快照：reload 时整体替换 {@code Town#config}，读取侧拿到的永远是完整一致的配置。
 * 所有键读取都带兜底值，缺键 / 值非法时告警并回退默认（见 exp/paper-plugin.md「材质被旧配置带成石头」）。
 */
public record TownConfig(
        int minMembers,
        int maxMembers,
        int nameMinLength,
        int nameMaxLength,
        Pattern nameRegex,
        long pendingExpireMillis,
        long inviteExpireMillis,
        String currencyCode,
        double foundingCost,
        int freeClaimRadius,
        Set<String> claimWorlds,
        boolean diagonalConnected,
        int maxChunksPerTown,
        double pricePerChunk,
        String craftEngineId,
        Material hallMaterial,
        Protection protection,
        int buffIntervalTicks,
        List<BuffDef> buffs,
        int menuEntryOrder,
        Map<String, String> messages) {

    /** 领地保护开关集。 */
    public record Protection(
            boolean breakBlocks,
            boolean place,
            boolean container,
            boolean interact,
            boolean bucket,
            boolean explosion,
            boolean piston,
            boolean fire,
            boolean hanging,
            boolean entityGrief) {
    }

    /** 一个可购买的领地 buff。 */
    public record BuffDef(
            String id,
            String displayMini,
            double cost,
            long durationMillis,
            List<Effect> effects) {

        public boolean permanent() {
            return durationMillis < 0;
        }

        /** 药水效果；type 已解析为 PotionEffectType，解析失败的条目在加载期被丢弃。 */
        public record Effect(PotionEffectType type, int amplifier) {
        }
    }

    public String message(String key) {
        return messages.getOrDefault(key, "<red>缺少消息 " + key);
    }

    // ------------------------------------------------------------------
    // 加载
    // ------------------------------------------------------------------

    public static TownConfig load(FileConfiguration cfg, Logger log) {
        int minMembers = clamp(cfg.getInt("town.min-members", 2), 1, 1000);
        int maxMembers = Math.max(0, cfg.getInt("town.max-members", 0));
        int nameMin = Math.max(1, cfg.getInt("town.name-min-length", 2));
        int nameMax = Math.max(nameMin, cfg.getInt("town.name-max-length", 12));
        Pattern regex = Pattern.compile("[0-9A-Za-z_\\u4e00-\\u9fa5]+");
        try {
            regex = Pattern.compile(cfg.getString("town.name-regex", regex.pattern()));
        } catch (PatternSyntaxException e) {
            log.warning("配置 town.name-regex 不是合法正则，已回退默认：" + e.getMessage());
        }
        long pendingExpire = Math.max(0, cfg.getInt("town.pending-expire-hours", 72)) * 3600_000L;
        long inviteExpire = Math.max(1, cfg.getInt("town.invite-expire-minutes", 30)) * 60_000L;

        String currency = cfg.getString("economy.currency", "");
        double foundingCost = Math.max(0, cfg.getDouble("founding.cost", 1000.0));
        int freeRadius = clamp(cfg.getInt("founding.free-claim-radius", 1), 0, 8);

        Set<String> worlds = Set.copyOf(cfg.getStringList("claim.worlds"));
        boolean diagonal = cfg.getBoolean("claim.diagonal-connected", true);
        int maxChunks = Math.max(0, cfg.getInt("claim.max-chunks", 256));
        double price = Math.max(0, cfg.getDouble("expansion.price-per-chunk", 50.0));

        String ceId = cfg.getString("townhall.craftengine-id", "");
        Material hallMaterial = Material.LODESTONE;
        String rawMaterial = cfg.getString("townhall.fallback-material", "LODESTONE");
        Material parsed = Material.matchMaterial(rawMaterial == null ? "" : rawMaterial);
        if (parsed != null && parsed.isBlock()) {
            hallMaterial = parsed;
        } else {
            log.warning("配置 townhall.fallback-material「" + rawMaterial + "」不是可放置方块，回退 LODESTONE");
        }

        Protection protection = new Protection(
                cfg.getBoolean("protection.break", true),
                cfg.getBoolean("protection.place", true),
                cfg.getBoolean("protection.container", true),
                cfg.getBoolean("protection.interact", true),
                cfg.getBoolean("protection.bucket", true),
                cfg.getBoolean("protection.explosion", true),
                cfg.getBoolean("protection.piston", true),
                cfg.getBoolean("protection.fire", true),
                cfg.getBoolean("protection.hanging", true),
                cfg.getBoolean("protection.entity-grief", true));

        int buffInterval = Math.max(20, cfg.getInt("buffs.apply-interval-ticks", 40));
        List<BuffDef> buffs = loadBuffs(cfg.getConfigurationSection("buffs"), log);

        int entryOrder = cfg.getInt("menu.entry-order", 10);

        Map<String, String> messages = new LinkedHashMap<>();
        ConfigurationSection msg = cfg.getConfigurationSection("messages");
        if (msg != null) {
            for (String key : msg.getKeys(false)) {
                String value = msg.getString(key);
                if (value != null) {
                    messages.put(key, value);
                }
            }
        }

        return new TownConfig(minMembers, maxMembers, nameMin, nameMax, regex,
                pendingExpire, inviteExpire,
                currency == null ? "" : currency, foundingCost, freeRadius,
                worlds, diagonal, maxChunks, price,
                ceId == null ? "" : ceId, hallMaterial, protection,
                buffInterval, List.copyOf(buffs), entryOrder, Map.copyOf(messages));
    }

    private static List<BuffDef> loadBuffs(ConfigurationSection section, Logger log) {
        List<BuffDef> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        List<Map<?, ?>> entries = section.getMapList("entries");
        for (Map<?, ?> raw : entries) {
            Object idObj = raw.get("id");
            if (!(idObj instanceof String id) || id.isBlank()) {
                log.warning("buffs.entries 里有一条缺少 id 的配置，已跳过");
                continue;
            }
            double cost = raw.get("cost") instanceof Number n ? n.doubleValue() : 0;
            long duration = raw.get("duration-seconds") instanceof Number n
                    ? n.longValue() * 1000L
                    : -1L;
            String display = String.valueOf(raw.get("display") == null ? "<white>" + id : raw.get("display"));
            List<BuffDef.Effect> effects = new ArrayList<>();
            if (raw.get("effects") instanceof List<?> list) {
                for (Object effRaw : list) {
                    if (effRaw instanceof Map<?, ?> eff) {
                        PotionEffectType type = PotionEffectType.getByName(
                                String.valueOf(eff.get("type") == null ? "" : eff.get("type")).toUpperCase(Locale.ROOT));
                        if (type == null) {
                            log.warning("buff " + id + " 的效果类型「" + eff.get("type") + "」无法识别，已跳过");
                            continue;
                        }
                        int amplifier = eff.get("amplifier") instanceof Number n ? n.intValue() : 0;
                        effects.add(new BuffDef.Effect(type, Math.max(0, amplifier)));
                    }
                }
            }
            if (effects.isEmpty()) {
                log.warning("buff " + id + " 没有任何可用效果，已跳过");
                continue;
            }
            result.add(new BuffDef(id, display, cost, duration, List.copyOf(effects)));
        }
        return result;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
