package org.spongepowered.asm.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/** 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明（保留级别必须与真实注解一致）。 */
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Unique {
}
