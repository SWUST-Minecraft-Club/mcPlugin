# ArcMenu 物品图标摆放角度：UltimateShop-ArcMenu 参考

## 只设置 `GUI` 会让部分方块从下方看，不能统一猜欧拉角

- **现象**：MainMenu 手机图标直接使用 `GUI` 时，唱片机露出底部、头颅视角偏下；改成 `NONE` 又可能看到头颅背面。负 `scale.z` 的实验出现面剔除和缺面。
- **根因**：`context` 选择的是客户端物品模型中的显示变换；物品节点的额外旋转、深度缩放和模型自身的显示变换共同决定最终投影。不能把“所有物品用 GUI”或 `Material.isBlock()` 当成完整角度方案。
- **规避/解决**：固定图标按下述 JAR 的两类参数设置，动态列表采用经过截图核对的统一库存投影；保持正的深度值。外部实现是技术参考，本条没有将其分类清单升级为仓库硬规范。
- **来源**：2026-10-26，用户提供的 `UltimateShop-ArcMenu-1.0.0.jar`；静态字节码核对及独立 Folia 26.3 / ArcMenu 3.2 客户端截图。MainMenu 实现见 `./Minecraft-26_3-plugin/MainMenu/assets/generate_menus.py`，截图见 `./Minecraft-26_3-plugin/MainMenu/assets/VALIDATION.md`。

## 1. 样本与可复核证据

| 项目 | 核对结果 |
| --- | --- |
| 插件 | `UltimateShop-ArcMenu`，`1.0.0` |
| JAR SHA-256 | `8ecd919e4e04290d0f2245d682f7ae01145f3cecdaefd185f4439433bcca325e` |
| POM 声明 | Java 21、Paper `26.2.build.55-alpha`、ArcMenu API `3.2.0`、UltimateShop `5.2.0` |
| 图标节点生成 | `com.fentai.ushop.arcmenu.OreScene.item(String, Material, double, double, double, double, boolean)` |
| 悬停缩放 | 同类的 `scaleOf(String, double)` |
| 分类依据 | JAR 根目录 `block-items.txt`，768 个不重复条目；文件注释注明依据 Minecraft 26.1.2 客户端库存模型 |
| 特殊预览 | `OreScreens` 的 `md-icon`，强制使用第二类参数并添加 Y 轴旋转轨道 |

使用 JDK 的 `javap` 即可复核，不必启动附件插件，也不必依赖反编译器：

```bash
javap -p -c -classpath '<JAR 文件路径>' com.fentai.ushop.arcmenu.OreScene
javap -p -c -classpath '<JAR 文件路径>' com.fentai.ushop.arcmenu.OreScreens
```

关键字节码位置（是方法内指令偏移，不是源码行号）：

- `item` 的 `0..28`：仅在最后布尔参数为 `false` 且材质 key 命中 `BLOCKS` 时进入第一类。
- `item` 的 `62..79`：第一类 `GUI`，第二类 `FIXED`。
- `item` 的 `120..169`：X/Y 都为传入尺寸 `s`；第一类 Z 为 `0.1`，第二类 Z 为 `s`。
- `item` 的 `179..214`：仅第一类额外写 `rotation.y = 180.0`。
- 简化 `item(String, Material, double, double, double)`：传入 Z 层 `2.0`、布尔参数 `false`。
- `scaleOf` 的 `20..42` 与 `80..92`：检查已生成节点的 context，GUI 节点在悬停放大时仍保持 Z=`0.1`。

附件中的说明文字只作为样本信息，未当作执行指令。未提交附件二进制或反编译源码。

## 2. JAR 中的真实参数

`s` 表示节点的 ArcMenu 原始尺寸值，不是屏幕像素；适配其它画布时要单独换算 X/Y 尺寸。

| 场景 | context | rotation X / Y / Z | scale X / Y / Z |
| --- | --- | --- | --- |
| 命中 `block-items.txt`，布尔参数为 false | `GUI` | `0° / 180° / 0°`，代码只显式写 Y | `s / s / 0.1` |
| 未命中清单，或布尔参数为 true | `FIXED` | 未额外写 rotation，默认 `0° / 0° / 0°` | `s / s / s` |
| 商品详情中的旋转预览 `md-icon` | 强制 `FIXED` | Y 在 `0°、90°、180°、270°、360°` 间线性循环 | `s / s / s` |

`md-icon` 的旋转轨道为 100 tick、`repeat`、`linear`、打开时启动。普通列表图标没有这条旋转轨道，不要把详情预览的动画当成默认摆放角度。

常见材质在该附件中的实际分类：

| 使用 GUI + Y=180° + Z=0.1 | 使用 FIXED + 三轴等比 |
| --- | --- |
| `stone`、`jukebox`、`chest`、`player_head`、`creeper_head` | `emerald`、`gold_ingot`、`clock`、`compass` |
| `oak_planks`、`crafting_table`、`anvil` | `bell`、`comparator`、`writable_book`、`barrier` |
| `glass`、`light_gray_stained_glass` | `glass_pane`、`light_gray_stained_glass_pane`、`torch`、`oak_sign` |

清单表示**库存图标采用方块模型**，不等同于 Bukkit 的“是方块”或“遮光方块”。例如铃铛、比较器、玻璃板没有命中该清单；头颅命中。清单的客户端版本也早于本仓当前的 26.3，新增材质和资源包自定义模型需要另核对。

## 3. 可直接参考的原生 YAML

方块库存图标，例如唱片机：

```yaml
music-icon:
  type: item
  material: minecraft:jukebox
  context: GUI
  rotation: {x: 0, y: 180, z: 0}
  scale: {x: 8, y: 8, z: 0.1}
  offset: {x: 0, y: 0, z: 1}
```

普通物品，例如绿宝石：

```yaml
town-icon:
  type: item
  material: minecraft:emerald
  context: FIXED
  rotation: {x: 0, y: 0, z: 0}
  scale: {x: 8, y: 8, z: 8}
  offset: {x: 0, y: 0, z: 1}
```

这些是局部节点示例，不能替代菜单的画布和后端区域。`scale.z: 0.1` 是该 GUI 图标方案的固定正值；不要把它写成负数，也不要在放大时自动改为与 X/Y 相同。

## 4. MainMenu 的应用与验证边界

- 首页的静态物品按两类分别配置：唱片机、头像使用 GUI + Y=180° + Z=0.1；绿宝石、金锭、铃铛、指南针、书与笔等使用 FIXED + 三轴等比。
- 应用抽屉、管理和属性页的材质随玩家和分页变化。目标 ArcMenu 3.2 的原生 YAML 中 `context`、旋转和数字变换不能直接读取 META，因此采用统一 `GUI` + Y=180° + Z=0.1。实际核对过头颅、唱片机、平面图标和玻璃板；这是本次原生模板的折中，不是声称 JAR 对所有材质都用 GUI。
- 动态列表遇到改变显示变换的资源包、自定义物品模型时仍需截图复核。不能保证所有物品与附件的分类型结果完全相同。
- 本机 26.3 原版客户端 `models/item/generated.json` 的 `fixed.rotation` 是 `[0, 180, 0]`，没有单独的 GUI 变换。因而普通 generated 平面图标使用 GUI 再额外转 Y=180°，与其 FIXED 朝向一致；这不自动适用于所有模型父级或自定义模型。
- 比较器的 `items/comparator.json` 指向 `models/item/comparator.json`，后者继承 `item/generated` 并使用 `textures/item/comparator.png`。实测小底座、长火把就是这张平面贴图的内容，不是 3D 模型角度错了。手机管理入口最终采用更清晰的书与笔，附件原分类仍如实保留。
- 附件的 `OreRenderer` 还调用动态 `menus.register(...)`；本仓目标 API jar 未提供相同签名。不要因 POM 同写 `3.2.0` 就直接搬用其动态注册代码。这里复用的是已经实测的节点参数。
- 本次修正替代了试验阶段 `NONE` + X=25° / Y=35° 的唱片机角度。后续截图与文档以本条记录为准。
