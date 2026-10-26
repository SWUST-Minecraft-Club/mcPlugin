package com.example.town.hook;

import com.example.town.TownPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.List;

/** 在启动阶段释放 Town 自有资源包，已有文件留给运营者编辑。 */
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
    }
}
