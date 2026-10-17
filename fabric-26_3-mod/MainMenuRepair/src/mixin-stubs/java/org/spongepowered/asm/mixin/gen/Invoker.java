package org.spongepowered.asm.mixin.gen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/** 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明。 */
@Target(ElementType.METHOD)
public @interface Invoker {

    String value() default "";
}
