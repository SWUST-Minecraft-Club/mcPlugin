# 方块日常 · YudreamUI 视觉重构

版本：`Beta_0_2_0`。根据用户 2026-10-28 的修正，采用 Minecraft 日常生存场景与二次元人物，保留原版物品、世界背景、玩家状态和实际业务。

## 界面结构

- 主菜单：局部村庄插画、功能与描述、玩家位置/等级/生命/饱食信息、探索指南。功能来自 MainMenu 注册表，数量和权限随原插件变化。
- 商店：默认展示全部商品，本地分类筛选、紧凑物品陈列、余额和行囊/订单摘要、商品详情与独立付款确认。
- 城镇：原始标题与步骤、按字段对齐的资料、独立操作栏。圈地页用客户端本地读取的逐方块表面地形作底图，服务端 7×7 领地状态与购买/放弃按钮叠加在对应区块；没有地形能力时保留状态网格。
- 对话：底部二次元向导与半透明对话框，取消整屏遮罩；宽屏对话框高 240 像素，窄窗口最多占高度的 42%，立绘不越出该范围。正文与选项分栏滚动，记录在框内展开，上方场景保持可见。
- 854×480 时收起低优先级侧栏，商店/镇务隐藏封面，保留可滚动内容与固定关闭入口。

设计元素采用薄边框、清晰中文、小面积薄荷与暖金强调。插画不是整张 UI，全部文字、按钮、物品和表单都是独立交互元素。现有业务不提供活动流或新闻数据，因此不编造在线活动、排名或交易动态。

## 实现

`ui-density="2"` 使用 GUI 逻辑尺寸的两倍进行排版与字体栅格化，再在 Minecraft 适配层映射渲染、裁剪、点击和输入坐标。未指定时仍为 1。新功能通过 `atlas:2` 能力协商；旧客户端使用语义降级。

图片通过线性采样显示，原版物品仍走游戏物品渲染。封面采用 [RmlUi image decorator 的 cover](https://mikke89.github.io/RmlUiDoc/pages/rcss/decorators/image.html) 保持比例，隐藏的同源 img 声明参与现有资源生命周期管理。运行代码与测试固定 RmlUi 6.3。

默认两张插画随 Mod 内置，首次开页不访问外部网络。服务端 `presentation-assets` 可覆盖素材并用 `/yui theme` 更新当前页面，文档、状态和动作仍来自插件。安装配置见 [README](./README.md)。

`mc-terrain` 是独立原生组件。协议声明维度、中心区块和奇数边长，RmlUi 负责矩形与裁剪，26.3 适配器读取已加载区块。每客户端 tick 最多采样 4 个区块，达到 2ms 后停止本轮采样；单块采样为不可拆分的 256 列，因此这不是帧耗时硬上限。纹理仅在内容变化时上传，缓存最多 512 块和 4 个视口；离开世界时清空。未加载处显示棋盘格，不主动请求区块，不经 Folia 发送地形图，不触发资源包更新。它显示地表，洞穴与地狱顶层不会自动切片。

## 素材来源与最终提示词

方式：内置 `image_gen`，2026-10-28 生成；没有调用 CLI/API fallback。仅复制最终选定输出进入仓库，没有用脚本重绘或去背。写实电影风的早期尝试未接入。

- 村庄：`./fabric-26_3-mod/YudreamUI/runtime/src/main/resources/builtin/art/village.png`，1774×887。
- 向导：`./fabric-26_3-mod/YudreamUI/runtime/src/main/resources/builtin/art/guide.png`，1024×1536，RGBA，保留生成的透明通道。

### 村庄提示词

```text
Use case: stylized-concept. Asset type: SMALL supporting banner illustration for a dense Minecraft multiplayer server menu, NOT a full-page wallpaper. Pure Japanese 2D anime background art, clean thin ink outlines, cel shading, flat painted colors, bright relaxed slice-of-life atmosphere. A modest Minecraft oak-and-cobblestone village beside a small square wheat farm and a blocky cherry tree. Clearly recognizable cubic grass blocks, block-built roofs, pixel-grid window panes, one Minecraft crafting table and chest outside a cozy supplies shop. A tiny white cube-shaped sheep nearby. Eye-level view, warm daylight, soft mint and ivory with orange terracotta accents. Landscape 2:1 composition. Charming and grounded in ordinary Minecraft survival, humble local village scale. No cinematic realism, no 3D render, no photographic lighting, no epic castles, no giant fantasy mountains, no floating islands. No characters, no typography, no UI, no watermark. Beautiful hand-drawn anime quality with visible linework, not photorealism.
```

### 向导提示词

```text
Use case: stylized-concept. Asset type: original 2D anime NPC standing portrait for a Minecraft server dialogue UI. SINGLE young adult woman village guide, friendly expressive large anime eyes, small nose and simple anime mouth, short chestnut bob with a small white square hairpin. Clean fine ink contour lines, confident flat 2-tone CEL SHADING, pure hand-drawn Japanese anime visual-novel sprite. Mint green short hooded jacket over cream shirt, dark shorts and opaque leggings, simple brown leather adventurer belt with a recognizable square Minecraft emerald charm, holding a folded Minecraft map in one hand. 3/4 standing pose from head to knees, looking gently to the right of the canvas, relaxed. Pastel mint, ivory, warm brown with restrained terracotta details. Transparent background and clean silhouette, no scenery, no painted glow, no drop shadow, no floor. Absolutely NOT photorealistic, NOT semi-realistic, NOT 3D, NO skin pores, NO photographic fabric detail. No writing, no interface, no logos. Entire head, elbows and hands comfortably within the image. High quality crisp anime character art.
```

## 截图与验收

新版本实机记录见 [视觉验收](./VALIDATION-VISUAL.md)。素材是原创生成的通用村庄与向导形象，不表示测试服实际地形，也不读取玩家皮肤。
