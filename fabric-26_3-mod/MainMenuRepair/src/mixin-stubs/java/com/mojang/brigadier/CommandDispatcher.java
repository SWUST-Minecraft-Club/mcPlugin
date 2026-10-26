package com.mojang.brigadier;

import com.mojang.brigadier.tree.RootCommandNode;

/**
 * 编译期桩：brigadier 是独立库、不在客户端 jar 里，而 {@code ClientPacketListener#getCommands()}
 * 的返回类型是它。只参与编译，不会打进模组 jar。
 */
public class CommandDispatcher<S> {

    public RootCommandNode<S> getRoot() {
        return null;
    }
}
