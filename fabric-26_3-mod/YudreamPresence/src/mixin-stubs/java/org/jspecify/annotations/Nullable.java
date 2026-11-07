package org.jspecify.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 编译期桩：Minecraft 客户端 jar 的签名把它用在类型位置上（字段类型、泛型参数…），
 * 所以这里把可能用到的目标全列上，否则 javac 读类文件时会报
 * “无法将类型批注附加到 …”。运行期不会用到本类。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.TYPE_USE,
        ElementType.TYPE_PARAMETER,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.LOCAL_VARIABLE,
        ElementType.RECORD_COMPONENT
})
public @interface Nullable {
}
