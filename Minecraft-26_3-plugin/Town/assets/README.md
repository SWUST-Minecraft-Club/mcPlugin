# 小镇素材

风格：暖象牙石、胡桃木、黄铜和玉绿；核心采用方块贴图，测绘工具为透明手持物品。菜单主体由 ArcMenu 几何和原版物品模型生成，不使用整张界面贴图。

## 来源与生成

两次均使用内置 `image_gen.imagegen`，保留未经修改的原始输出和完整提示词：

| 原稿 | 用途 | 生成时间（UTC） |
| --- | --- | --- |
| [town-material-reference.png](./source/town-material-reference.png) | 2×2 材质概念，左上正面、右上侧面、左下顶面、右下工具概念 | 2026-10-26 11:49 |
| [surveyor-reference.png](./source/surveyor-reference.png) | 以上图为编辑目标，提取测绘工具并生成真实透明背景 | 2026-10-26 11:53 |

准确的最终提示词、透明选项和输入关系见 [prompts.json](./prompts.json)；未使用 CLI 或自行调用图片 API。资源及原稿 SHA-256、尺寸和颜色模式见 [manifest.json](./manifest.json)。

## 机械转换

[rebuild_textures.py](./rebuild_textures.py) 自定位模块目录，依赖 Pillow：

```bash
python3 ./Minecraft-26_3-plugin/Town/assets/rebuild_textures.py --check
# 重新生成：去掉 --check
```

- 方块：原稿 1254×1254，以中心 627 切四象限，取前三格；最近邻缩为 32×32，再量化为 32 色 RGB。
- 工具：按原生 alpha 非零范围 `(0,16,1218,1254)` 裁剪，最近邻缩进 30×30，居中放入 32×32 RGBA；透明边缘保留。
- 输出在 `./src/main/resources/craftengine/resourcepack/assets/town/textures/`，CE 配置和核心模型在同资源目录下；不是运行时依赖图片生成服务。

## 游戏实拍

[截图目录](./screenshots/) 保留 1280×720 的真实 Minecraft 客户端截图，包括主页、经营页、输入、确认、迁移预览与 CraftEngine 核心/工具。截图中的方块平台和样例玩家为联调场景。

[验证记录](./VALIDATION.md) 说明实际验证范围；[layout-audit.json](./layout-audit.json) 保存关键卡片中心、行距及按钮位置的像素测量。
