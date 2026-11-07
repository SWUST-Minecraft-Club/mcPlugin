package org.jetbrains.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** 编译期桩，见 {@link Nullable} 的说明。 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NotNull {
}
