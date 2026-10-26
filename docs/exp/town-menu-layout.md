# Town 菜单图标、余额与布局校准

适用实测环境：2026-10-26，Town `Beta_0_7_1`、ArcMenu 3.2.0、Minecraft / Folia 26.3 build 5、1280×720 客户端，画布 800×450、窗口比例 70%、ppb 42.7、distance 2。

### 按库存模型选择图标朝向

- **现象**：全用 GUI、Z 深度固定 1 时，箱子朝向和普通物品的展示方式不一致，铃铛、门等图标效果欠佳。
- **根因**：Bukkit 的方块分类不等同于 Minecraft 库存模型分类；GUI 与 FIXED 的变换不同。
- **规避/解决**：参照 [物品角度记录](./arcmenu-item-angles.md)。Town 的普通图标用 FIXED、三轴同尺度；目前用到的箱子和玩家头颅用 GUI、Y 180°、正 Z=0.1。铃铛与木门按普通物品处理。仅为 Town 实际固定材质维护明确集合，新增材质时检查其库存模型。
- **来源**：用户要求按既有记录修正；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/arcmenu/MenuBuilder.java` 与 [主页实拍](../../Minecraft-26_3-plugin/Town/assets/screenshots/town-style-home.png)。

### ArcMenu 3.2 的形状尺寸需要重新校准

- **现象**：旧版矩形高度补偿 0.9 在当前环境偏高；卡片外缘与后台命中区域有小幅误差。
- **根因**：当前实际投影与旧版测量存在差异。无补偿校准场景中，600 单位矩形实际宽 679 px，预期 672 px；z=-2 的 100 单位高矩形实际约 101 px，透视后的未损失高度约 109.4 px。
- **规避/解决**：ppb 保持 42.7，窗口缩放同时作用于视觉与后台。矩形采用宽 /1.01、高 /0.915、底边固定；frame 两轴 /1.01，描边保留 1 单位以避免低于一个屏幕像素后边缘断续。七层 Z 透视补偿保留。地图 image 的字体边距和基线补偿独立保留，不能套用矩形规则。
- **来源**：本轮独立校准场景与真实像素测量；[布局审计](../../Minecraft-26_3-plugin/Town/assets/style-layout-audit.json)。这是当前运行环境的测量值，升级后应重新测量。

### 动态余额用有边界的展示值，完整值放悬停

- **现象**：原余额中心位于卡片右侧，金额与币种同一行；长币名、大额余额溢出。Vault 的内部来源标识也会撑长镇库流水和报价。
- **根因**：ArcMenu 动态文本以中心锚点绘制，未知宽度不能按左边缘定位；账本币种用于稳定绑定来源，不适合作为界面币名。
- **规避/解决**：共用顶栏使用金额、币种两行，金额居中，标签和币种占各自区域。按字体宽度限制金额和币名；金额缩写带 `≈`，长币名省略，完整金额、币名及来源保留在悬停。镇库使用同样规则，流水和报价解析可读币名。失败显示“暂不可用”并间隔 5 秒重试，避免失败后立即刷新造成连续查询。展示转换不修改结算金额、币种标识或来源绑定。
- **来源**：`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/arcmenu/BalanceDisplay.java`、`TownUi.java`、`TownOperations.java`；[边界场景截图](../../Minecraft-26_3-plugin/Town/assets/screenshots/town-style-large-balance.png) 与 [完整值悬停](../../Minecraft-26_3-plugin/Town/assets/screenshots/town-style-balance-tooltip.png)。

### 立即完成的预热回调不能无条件刷新

- **现象**：Vault 的 Economy 服务断开时，币种一直未解析，预热 Future 仍能立即成功完成。
- **根因**：预热完成后无条件刷新，而玩家区域内的调度封装会直接执行；刷新再次预热，形成同步重入。
- **规避/解决**：用 `whenComplete` 在成功或失败时都释放预热锁；仅在预热成功且币种已解析后主动刷新。未解析时等待正常 UI 周期重试，避免回调递归。
- **来源**：本轮余额不可用状态检查；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/arcmenu/TownUi.java`。

### 导航卡片上的重复悬停提示会遮住其他卡片

- **现象**：视线位于导航卡片时，重复标题和说明的原生 tooltip 挡住旁边入口。
- **根因**：普通卡片已完整显示标题和说明，额外 tooltip 没有提供新信息。
- **规避/解决**：导航卡片保留可点击区域，移除重复提示；余额等确有隐藏信息的位置继续提供悬停。184 单位宽的成员操作卡片改为图标在上、文字在下，避免横排溢出。
- **来源**：`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/arcmenu/TownMenus.java` 与本轮实机检查。
