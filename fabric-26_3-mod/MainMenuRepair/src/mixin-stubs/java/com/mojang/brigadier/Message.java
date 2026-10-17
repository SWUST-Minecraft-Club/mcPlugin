package com.mojang.brigadier;

/**
 * 编译期桩：Minecraft 的 {@code net.minecraft.network.chat.Component} 继承自 Brigadier 的
 * {@code Message}，而 brigadier 是独立库、不在客户端 jar 里。没有这个桩，任何用到
 * {@code Component}（例如 {@code Screen#getTitle()}）的代码都会报“无法访问 Message”。
 * 只参与编译，不会打进模组 jar。
 */
public interface Message {

    String getString();
}
