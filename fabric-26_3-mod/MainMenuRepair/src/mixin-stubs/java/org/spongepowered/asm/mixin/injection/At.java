package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明。真实注解：@At(String value, String target, ...) */
@Retention(RetentionPolicy.RUNTIME)
public @interface At {

    String id() default "";

    String value();

    String target() default "";

    String[] args() default {};
}
