package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.Target;

/** 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明（保留级别必须与真实注解一致）。 */
@Target({})
public @interface At {

    String id() default "";

    String value();

    String target() default "";

    String[] args() default {};
}
