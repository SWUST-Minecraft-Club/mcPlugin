package com.example.town.hook;

import com.example.town.TownPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** 在启动阶段释放 Town 自有资源包，已有文件仅迁移明确的旧默认值。 */
public final class TownAssets {
    private TownAssets() { }
    private static final List<String> FILES=List.of("pack.yml","configuration/town.yml",
            "resourcepack/assets/town/models/block/town_hall.json",
            "resourcepack/assets/town/textures/block/town_hall_front.png",
            "resourcepack/assets/town/textures/block/town_hall_side.png",
            "resourcepack/assets/town/textures/block/town_hall_top.png",
            "resourcepack/assets/town/textures/item/surveyor.png");
    public static void install(TownPlugin plugin) throws IOException {
        Path root=plugin.getDataFolder().toPath().getParent().resolve("CraftEngine/resources/town");
        for(String name:FILES) {
            Path file=root.resolve(name);if(Files.exists(file)) continue;
            try(InputStream input=plugin.getResource("craftengine/"+name)) {
                if(input==null) throw new IOException("缺少小镇资产："+name);
                Files.createDirectories(file.getParent());Files.copy(input,file);
            }
        }
        if(migrateSurveyorMaterial(root.resolve("configuration/town.yml")))
            plugin.getLogger().info("测绘工具基物已由木棍迁移为燧石，原 CE 定义已备份；请重新领取工具。");
    }

    /** 只替换旧木棍基物，保留运营者的其他定义和非木棍的自定义选择。 */
    static boolean migrateSurveyorMaterial(Path file) throws IOException {
        String original=Files.readString(file);
        YamlConfiguration yaml=new YamlConfiguration();
        yaml.options().parseComments(true);
        try {yaml.loadFromString(original);}
        catch(InvalidConfigurationException error) {throw new IOException("CE 小镇定义不是有效的 YAML",error);}
        String key="items.town:surveyor.material";
        String material=yaml.getString(key,"");
        if(!material.equalsIgnoreCase("stick") && !material.equalsIgnoreCase("minecraft:stick")) return false;
        Path backup=file.resolveSibling("town.yml.before-surveyor-flint.bak");
        if(!Files.exists(backup)) Files.copy(file,backup);
        yaml.set(key,"flint");
        Path temporary=Files.createTempFile(file.getParent(),"town-surveyor-",".tmp");
        try {
            Files.writeString(temporary,yaml.saveToString());
            try {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored) {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
        return true;
    }
}
