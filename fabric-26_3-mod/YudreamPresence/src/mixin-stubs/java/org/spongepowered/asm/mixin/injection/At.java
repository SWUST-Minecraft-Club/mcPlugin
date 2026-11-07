package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明。
 *
 * <p>真实 {@code @At} 也是 {@code RUNTIME} 保留；写成 CLASS 会让外层 {@code @Inject}
 * 的注入点信息在运行期丢失。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface At {

    String id() default "";

    String value();

    String target() default "";

    String[] args() default {};
}
