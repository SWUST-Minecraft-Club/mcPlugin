# 小镇科研计时与外部经验加成

## 研究跨过政策到期时间时，需要分段累计工作量

- **现象**：如果只保存结束时间，或用当前速度乘全部离线时间，中途买政策会加速已经过去的时段；重启时政策已到期则可能丢失离线期间应有的加速。
- **根因**：真实时间区间内的科研速度会随政策开始、续购、到期而变化，当前状态无法还原历史速度。
- **规避/解决**：研究存档保存基础工作量、已完成工作量、结算时间、加速比例与各到期时间。跨到期点分段积分；购买/续购先结算旧记录，再替换未来的加速。完成与镇库扣费分开，研究完成不再次走付款。
- **来源**：2026-10-27，Town `Beta_0_10_0`；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/economy/Research.java`、`ResearchTest`，Folia 首次/重启夹具。

## 可选插件的经验接口需要共享类定义与不可变操作上下文

- **现象**：扩展插件若把核心接口类也打入自己的 JAR，`ServicesManager.load` 可能读不到同名服务；若只传玩家当前位置，会把远端收获、蟹笼或矿石奖励归入错误镇域。
- **根因**：服务键是实际的 `Class`，同名不同类加载器的类并不相等；操作位置和玩家位置也不总相同。
- **规避/解决**：Town 使用独立 API JAR 的 `compileOnly` 依赖，Paper 可选依赖配置 `join-classpath`，所有星露谷类型隔离在延迟加载的适配器。核心传 UUID、技能、世界 UUID、操作坐标与基础经验，不向提供器暴露可变实体；钓鱼按成功结算时玩家位置。提供器只读小镇快照，统一使用既有政策范围/到期规则。
- **来源**：2026-10-27，RptStardew `1.6.0-folia26.3-beta.1` / Town `Beta_0_10_0`；真实 Folia 双插件类加载器身份与有/无核心两种运行检查，官方 [ServicesManager](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/ServicesManager.html) 与 [Folia 调度](https://docs.papermc.io/paper/dev/folia-support/)。
