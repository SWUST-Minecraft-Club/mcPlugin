package com.mojang.brigadier.tree;

/**
 * 编译期桩：brigadier 是独立库、不在客户端 jar 里。
 * 只参与编译，不会打进模组 jar。
 */
public class RootCommandNode<S> extends CommandNode<S> {
}
