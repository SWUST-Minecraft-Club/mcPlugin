package net.fabricmc.fabric.api.event;

/**
 * 编译期桩：真实类由 Fabric API（fabric-api-base 模块）提供，运行期使用真实实现。
 * 只参与编译，不会打进模组 jar。
 */
public abstract class Event<T> {

    public abstract void register(T listener);
}
