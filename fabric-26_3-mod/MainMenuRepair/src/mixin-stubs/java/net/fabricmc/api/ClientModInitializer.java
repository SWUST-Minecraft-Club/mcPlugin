package net.fabricmc.api;

/** 编译期桩：真实接口由 Fabric Loader 提供（fabric-loader jar）。 */
public interface ClientModInitializer {

    void onInitializeClient();
}
