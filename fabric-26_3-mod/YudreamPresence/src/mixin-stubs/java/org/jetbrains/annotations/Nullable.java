package org.jetbrains.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** 编译期桩：同上，Minecraft 的签名里也会引用 JetBrains 的注解。 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Nullable {
}
