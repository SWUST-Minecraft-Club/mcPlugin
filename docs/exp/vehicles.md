# Vehicles：Folia 双人载具与原版模型

适用范围：Minecraft / Folia **26.3 build 5**、Vehicles **Beta_0_1_0**，2026-10-27 实测。

### 普通乘客退服也可能让整个小车消失

- **现象**：车主仍在线驾驶，副驾驶断开连接后小车全部消失。
- **根因**：两个座位是独立 ItemDisplay。原版 `PlayerList.remove` 会检查退服玩家的根载具；根载具只有该玩家时，先下车，再以 `UNLOADED_WITH_PLAYER` 原因删除载具。副驾驶座位被删除后，其退休回调触发整车回收。
- **规避/解决**：`PlayerQuitEvent` 中若玩家正在乘坐托管座位，先在当前玩家区域线程调用 `leaveVehicle()`，之后再回收该玩家作为车主拥有的车。真实踢出副驾驶后，驾驶者和车保留，继续行驶 7.932 格并刹停。
- **来源**：2026-10-27，Folia 26.3 本地运行 JAR 的 `PlayerList.remove` 字节码与双客户端回归；`./Minecraft-26_3-plugin/Vehicles/src/main/java/com/example/vehicles/Vehicles.java`。

### 停服线程报告拥有实体，不代表世界 tick 上下文仍可用

- **现象**：常规行驶和收车正常；服务器 `stop` 时，在 `onDisable` 中删除实体，`ServerLevel.getCurrentWorldData()` 返回 null，触发异常。
- **根因**：Folia 停服已停止区域调度并退休世界区域上下文，停服线程的 `Bukkit.isOwnedByCurrentRegion(entity)` 仍可能返回 true。所有权判断不能证明停服阶段仍可按常规实体路径操作。
- **规避/解决**：实体生成时全部 `setPersistent(false)`；服务器 `isStopping()` 时只关闭车辆逻辑和内存索引，由服务端处理实体清理。最终有车停服无该异常；重启加载原有车辆区块，9 个组件全部消失。热换 JAR 不属于支持的更新方式。
- **来源**：2026-10-27，Vehicles 停服异常、修复后的停服/重启日志；`./Minecraft-26_3-plugin/Vehicles/src/main/java/com/example/vehicles/TownCar.java`，实测见 `./Minecraft-26_3-plugin/Vehicles/assets/VALIDATION.md`。

### 多个展示实体各自跟随自己的 Folia 调度器

- **现象**：把一辆车的全部展示实体都在驾驶者任务中移动，跨区块/区域时可能触碰不属于当前线程的实体。
- **根因**：一个逻辑车辆不意味着其中所有实体永远归同一区域线程。异步传送完成也不能视为仍在原来的实体线程。
- **规避/解决**：驾驶座任务生成不可变物理快照，各组件通过自己的 `EntityScheduler` 读取快照并 `teleportAsync`；为传送设置在途标记，避免重复提交。异步回调只更新并发状态，不直接操作实体，不在 tick 线程 `.join()`。26.3 本次实测传送保留了座位上的玩家；跨区块行驶 46.48 格，两位乘员位置一致。读取远处方块仍需独立检查区域所有权；未归属/未加载区域按障碍处理，并异步预取区块。
- **来源**：2026-10-27，`./Minecraft-26_3-plugin/Vehicles/src/main/java/com/example/vehicles/TownCar.java`、`VehicleTerrain.java`；[Paper Folia 支持](https://docs.papermc.io/paper/dev/folia-support/)、[实体传送](https://docs.papermc.io/paper/dev/entity-teleport/)。

### 方块 VoxelShape 需要先转换到世界坐标

- **现象**：车辆静止时缓慢下沉，或在明显有地面的地方无法落地、穿过障碍。
- **根因**：方块碰撞形状的包围盒使用方块局部坐标，直接与世界坐标的车辆包围盒比较，位置不在原点时就会漏判。
- **规避/解决**：每个碰撞包围盒加上方块 X/Y/Z 后再比较；读取前确认区块加载和区域归属。对移动分步求解，避免最高速度穿过薄墙。本次砖墙位于 Z=78，车中心停在 Z=76.603。
- **来源**：2026-10-27，`./Minecraft-26_3-plugin/Vehicles/src/main/java/com/example/vehicles/VehicleTerrain.java`、`DrivePhysics.java` 与实机撞墙记录。

### 模型、实体偏航与座位挂点要分别实测

- **现象**：预览图正确，ItemDisplay 实机车头却与前进方向相反；座位实体 Y 看似合理，但玩家姿态明显偏低或穿顶。
- **根因**：物品模型 `display.fixed` 的旋转参与最终方向；玩家乘坐 ItemDisplay 时还会叠加原版玩家挂点偏移，玩家脚底坐标不是座椅坐垫高度。
- **规避/解决**：当前模型 `fixed.rotation` 设 `[0,180,0]`，实体 yaw=0 对应世界 +Z 前进。座位根位置为车底基准 `+.48`，玩家脚底约为基准 `−.12`，这是本模型正确坐姿的实测结果。用第一人称视线和第三人称双人截图检查，不能仅看玩家位置数值调整。
- **来源**：2026-10-27，原创 `./Minecraft-26_3-plugin/Vehicles/assets/generate_models.py`、`TownCar.java`；[展示实体](https://docs.papermc.io/paper/dev/display-entities/)，截图见 `./Minecraft-26_3-plugin/Vehicles/assets/screenshots/`。
