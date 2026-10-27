# MainMenu 迁移到 ArcMenu 3.2 的运行期边界

## `item` 的空气占位能通过菜单校验，却会在打开时失败

- **现象**：8 页 YAML 均通过 `menus().validate()` / `reload()`，但首页实际打开时报 `Cannot build item minecraft:air`，整个页面失败。
- **根因**：材质占位符在查看者会话中才解析；静态校验不会构建最终的 AIR 物品。3.2 的 `context`、`visible` 是静态字段，前端也不支持 `condition`，不能用三种 context 加互斥 AIR 节点拼动态模型。
- **规避/解决**：默认首页直接在 YAML 中写明确的原版模型；手机应用抽屉和管理页统一用静态 `context: GUI`、Y=180°、正深度 0.1 配合动态材质，空位使用淡灰玻璃片并明确标注“空位”、禁用点击。角度依据见 [物品角度记录](./arcmenu-item-angles.md)。通用 `-material` 缺失时提供有效纸材质。功能 `icon(viewer)` 用于提取名称和材质数据，完整物品保留在背包回退界面；不能承诺所有 `ItemStack` 元数据无损转换。
- **来源**：2026-10-26，MainMenu `Beta_1_6_0`，独立 Folia 26.3 + ArcMenu 3.2 实际客户端打开测试；`./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/arcmenu/FeatureDisplay.java`、`./Minecraft-26_3-plugin/MainMenu/assets/generate_menus.py`。字段范围参照 [ArcMenu 前端文档](https://docs.myarc.icu/cai-dan-pei-zhi/menus/frontend)。

## `refresh()` 更新文本，但不会重新构建动态 item

- **现象**：修改 META 后，时间和文字立刻变化，已创建的物品仍显示旧模型。
- **根因**：3.2 当前刷新路径更新文字、图片与提示，不重新构建 item 节点。
- **规避/解决**：常规文本刷新继续用 `api.refresh(player)`；追踪功能身份、材质和 context，仅变化时重开当前页面。这样不必每秒重开，仍可让作者自行加入的动态物品在变化后更新。
- **来源**：2026-10-26，MainMenu 桥接实现与 ArcMenu 3.2 运行期验证；`./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/arcmenu/ArcMenuBridge.java`。不要直接套用官网新版本的 API 或注册菜单能力。

## 空区域的 condition 不会替你清理空白悬停框

- **现象**：动态空位的三个 tooltip 字段均为空，鼠标经过时仍出现窄长的空提示框。
- **根因**：后端 `condition` 守卫点击动作，空字符串列表仍然是提示内容。
- **规避/解决**：提供一个完整的 `-tooltip` 字段；有功能时包含名称和正文，空位则只显示一行“暂无功能”。管理列表在此基础上追加真实状态和排序，避免空格仍显示“点击管理”。
- **来源**：2026-10-26，MainMenu 全部功能和管理页真实截图；同上 `ArcMenuBridge.java` 和生成器。

## 不覆盖用户 YAML，需要把模板和运行期数据分开

- **现象**：如果每次启用或 reload 都从 Java 重建页面，管理员编辑的主页会消失；旧 `config.yml` 还可能缺少新增绑定段。
- **根因**：把布局当成运行期派生数据，或把“配置段存在”误当成“包含所有新增默认键”。
- **规避/解决**：模板按单文件仅在缺失时复制；主页布局与动作完全留给原生 YAML。Java 只提供玩家 META 和业务路由。内置绑定先提供默认值，再逐键覆盖，显式空字符串禁用；新绑定配置只在菜单库成功发布后切换。序列化测试验证已有文件字节不变，运行期再验证修改、reload 和重启。
- **来源**：2026-10-26，MainMenu 旧配置升级与 YAML 控制主页测试；`./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/arcmenu/MenuTemplates.java`、`./Minecraft-26_3-plugin/MainMenu/src/test/java/com/example/mainmenu/arcmenu/MenuTemplatesTest.java`。

## 旧版渲染补偿需要在 26.3 重新量

- **现象**：沿用旧经验的 10% 矩形高度损失时，大背景顶部多出约 12 像素，卡片宽度也略大。
- **根因**：ArcMenu 3.2 / Minecraft 26.3 的实测矩形高度约为声明值的 91.5%，宽度约有 1% 的增量。小形状不明显，大背景会放大误差。
- **规避/解决**：在独立客户端 1600×900、GUI 800×450、FOV 70、distance 2、ppb 42.7 下测校准图，再调整生成器的 `RECT_HEIGHT = .915` 和 `RECT_WIDTH = 1.01`；保留底边定位与图层透视补偿。该数值只代表这组已验证条件，换版本、视距或渲染模式需复核。
- **来源**：2026-10-26，`arcmenu-designing` 校准场景与 MainMenu 真实截图像素量测；`./Minecraft-26_3-plugin/MainMenu/assets/generate_menus.py`、`./Minecraft-26_3-plugin/MainMenu/assets/VALIDATION.md`。

## 子按钮与父面板同层会让按钮底色消失

- **现象**：属性页的按钮能点击，文字也存在，但背景只剩细线，部分区域与父面板互相覆盖。
- **根因**：父面板和子按钮底色都使用相同 z；圆角矩形带本身不重叠，也无法避免不同组件之间的深度冲突。
- **规避/解决**：属性面板使用 z=0、按钮底色 z=1、文字 z=2；分别量测背景边界，再执行实际按钮点击。只通过 YAML 校验或动作回归无法发现这种视觉问题。
- **来源**：2026-10-26，手机属性页最终截图审查与修复；`./Minecraft-26_3-plugin/MainMenu/assets/generate_menus.py`、`./Minecraft-26_3-plugin/MainMenu/assets/screenshots/attribute-function.png`。

## 禁用某个点击类型时，需要在关闭 ArcMenu 前检查

- **现象**：自定义功能将右键配置成空字符串后，如果只在 `onClick` 中直接返回，ArcMenu 仍会关闭。
- **根因**：桥接原先在调用功能回调前统一关闭会话；同时原来的单一 `command` 无法表达“按键缺省”和“按键明确禁用”的区别。
- **规避/解决**：新增兼容旧实现的 `clickable(ClickType)`，桥接和背包监听先检查具体点击，再执行关闭/回调。`commands` 中缺失的键使用默认 `command`，空字符串覆盖必须保留；管理页创建功能时序列化这些覆盖，避免保存后丢失禁用状态。
- **来源**：2026-10-27，MainMenu `Beta_1_9_0`；`./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/arcmenu/ArcMenuBridge.java`、`./Minecraft-26_3-plugin/MainMenu/src/test/java/com/example/mainmenu/internal/CustomFeatureCommandsTest.java` 与 `CustomFeatureStoreTest.java`。验证覆盖配置解析、禁用回调的提前返回及创建后保存/重载；本次未进行客户端实机测试。
