package net.fabricmc.loader.api;
import java.nio.file.Path;
/** 仅编译，运行时由 Fabric Loader 提供。 */
public interface FabricLoader {
    static FabricLoader getInstance() { throw new UnsupportedOperationException("compile-only stub"); }
    Path getConfigDir();
}
