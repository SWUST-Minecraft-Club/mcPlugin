package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/** 编译期桩，见 {@code org.spongepowered.asm.mixin.Mixin} 的说明（保留级别必须与真实注解一致）。 */
@Target(ElementType.METHOD)
public @interface Inject {

    String[] method();

    At[] at();

    boolean cancellable() default false;

    int require() default -1;

    int expect() default 1;

    boolean remap() default true;
}
