package org.spongepowered.asm.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Mixin 注解的编译期桩（stub）。
 *
 * <p>26.3 时代 maven.fabricmc.net 在部分网络环境下被限速到 KB 级，为了让本工程**不依赖任何外部下载**
 * 也能编译，这里按真实注解（net.fabricmc:sponge-mixin）的签名声明了一份同名同结构的注解，
 * 只放在 {@code src/mixin-stubs/java} 这个独立 sourceSet 里参与编译，**不会打进模组 jar**。
 *
 * <p>运行期由 Fabric Loader 提供的真实 Mixin 注解生效，因此包名/成员名/类型/保留级别都要与真实注解一致：
 * 真实注解**没有**显式 {@code @Retention}（即默认 CLASS，编译为 RuntimeInvisibleAnnotations），
 * Mixin 也是按“不可见注解”去读的 —— 所以这里千万不要加 {@code @Retention(RUNTIME)}，
 * 否则 Mixin 会报 “is missing an @Mixin annotation”。
 */
@Target(ElementType.TYPE)
public @interface Mixin {

    Class<?>[] value() default {};

    String[] targets() default {};
}
