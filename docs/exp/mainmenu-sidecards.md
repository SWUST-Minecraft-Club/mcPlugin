# MainMenu 多侧卡：原生会话平面与动态布局

## 当前 API 不能把原生 YAML 当作按玩家动态增删的卡片列表

- **现象**：侧卡需要随权限、注册数量和内容高度改变位置，而目标 API 的原生前端坐标、可见性和模型 context 仍是静态字段；同名 3.2.0 的其它插件示例存在本地 API 没有的注册签名。
- **根因**：目标 premium JAR、API JAR 与公开源码快照的能力不能仅凭版本名称视为完全一致。会话 META 可更新文本，但不提供动态前端结构。
- **规避/解决**：保留手机原生 YAML，使用当前 `ArcMenuApi.surface(player)` 的实际平面放置 MainMenu 私有显示实体；YAML 与 API 卡片共享过滤、稳定排序、按高度分栏与分页。渲染结构变化重建，分数变化复用。关闭、隐藏和注销同步清理实体和点击区域。实体创建、刷新与点击在查看者所属区域线程，异地实体清理由其自身调度器完成；线程契约参照 [Paper/Folia 官方说明](https://docs.papermc.io/paper/dev/folia-support/)。
- **来源**：2026-10-26，MainMenu `Beta_1_8_0`；本地目标 API 的 `javap` 核对、独立 Folia 26.3 / ArcMenu 3.2 premium 实跑；`./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/arcmenu/ArcMenuSidecards.java`、`SidecardScene.java`。

## 鼠标模式的实际画布不是模板中的 distance 与 ppb 原值

- **现象**：模板保持 800×450、distance=2、ppb=42.7，进入鼠标模式后直接比较 ppb 的兼容检查仍拒绝显示。
- **根因**：ArcMenu 鼠标模式拉近菜单平面，并按相反比例放大 ppb，投影比例保持等效。
- **规避/解决**：核对当前画布宽高及 `ppb × designDistance = 85.4` 的等效关系，渲染基准使用 API 实际平面和实际 ppb，不修改模板的 42.7。显示层沿用本项目已量测的缩放与透视补偿；三张截图 14 个卡片外边界的最大误差为 0.866 设计单位。此数值仅适用于记录的版本和画布，不能推导到其它版本。
- **来源**：2026-10-26，默认三卡、六卡示例及侧卡第二页的实际 1280×720 截图；`./Minecraft-26_3-plugin/MainMenu/assets/sidecards-validation-results.json`。

## 长按测试请求返回时按键已经释放，潜行也会改变准星射线

- **现象**：普通左右键回调成功，串行调用 Shift 长按后再点击仍收到普通点击，改成并发后又可能点空。
- **根因**：MinecraftMcp `input.key` 在长按完成并释放后才返回；潜行降低玩家眼高，原站立姿态计算的准星不再命中同一位置。
- **规避/解决**：在测试线程中保持 Shift，请求期间从实际潜行眼高重新计算目标视角，再通过客户端发送真实攻击/使用；最后释放全部按键。实测 LEFT、RIGHT、SHIFT_LEFT、SHIFT_RIGHT 及 API 按钮 action。未安装 ArcMenu 客户端扩展，不将中键通道的静态实现视为已完成客户端实测。
- **来源**：2026-10-26，独立 Minecraft 26.3 + MinecraftMcp 联调；`./fabric-26_3-mod/MinecraftMcp/versions/mc26_3/src/main/java/com/example/minecraftmcp/mc26_3/InputController.java`。
