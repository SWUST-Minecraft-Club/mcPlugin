package com.mojang.brigadier.tree;

import java.util.Collection;

/**
 * 编译期桩：brigadier 是独立库、不在客户端 jar 里，这里只声明探测服务端命令所需的方法。
 * 只参与编译，不会打进模组 jar。
 */
public class CommandNode<S> {

    public CommandNode<S> getChild(final String name) {
        return null;
    }

    public Collection<CommandNode<S>> getChildren() {
        return null;
    }
}
