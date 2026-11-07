package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明。
 *
 * <p>保留级别必须与真实注解一致：{@code @Inject} 是 {@code RUNTIME}（不是 CLASS）。
 * 写成 CLASS 时 Mixin 读不到注入注解，会静默地只做类合并、不插入任何调用。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inject {

    String[] method();

    At[] at();

    boolean cancellable() default false;

    int require() default -1;

    int expect() default 1;

    boolean remap() default true;
}
