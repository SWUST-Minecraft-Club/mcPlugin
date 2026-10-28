# YudreamUI · Fabric 渲染端

`Beta_0_2_0` · Fabric / Minecraft **26.3** · Java **25**。真实 RmlUi **6.3** + JNI，服务端 Folia 插件负责文档、状态、动作和资源清单。没有嵌入 Chromium，也不运行 JavaScript。

[插件与安装](../../Minecraft-26_3-plugin/YudreamUI/README.md) · [协议和 HTML](./PROTOCOL.md) · [实机验证](../../Minecraft-26_3-plugin/YudreamUI/VALIDATION.md)

## 版本分离

```text
protocol/           Java 21；分片、XHTML 编译、语义降级模型；不引用游戏类
runtime/            Java 21；资源缓存、JNI、RmlUi 生命周期；不引用游戏类
native/             C++17；RmlUi 渲染/输入桥；与游戏版本无关
versions/mc26_3/    Java 25；Fabric 网络、SDL 输入、Screen、Minecraft GPU 与物品
```

升级游戏时以 `versions/` 中的独立适配器为改动边界，协议和布局引擎复用。26.3 适配器直接编译官方未混淆客户端，无 Loom/remap。尚未验证其它游戏版本；GPU、屏幕、IME API 改动仍需要实际适配。

## 构建

仓库根运行，先安装 Java 25、Python 3、CMake 3.20+、Ninja、C++17 编译器与 FreeType 开发包。Ubuntu 示例：

```bash
sudo apt-get install cmake ninja-build libfreetype6-dev
./gradlew :YudreamUI:build :MainMenu:build :Town:build
./gradlew -p ./fabric-26_3-mod/YudreamUI build
```

第一次从官方来源下载客户端与编译库，并从 [RmlUi 6.3](https://github.com/mikke89/RmlUi/releases/tag/6.3) 拉取固定提交 `ba95ffe8bfb6370efb2cdcca927eaad4710c5413`。完成缓存后可 `--offline` 重建；原生构建脚本也需要已缓存的 RmlUi 源码。输出留档：

- `./release/YudreamUI/YudreamUI-mc26_3-plugin-Beta_0_2_0.jar`
- `./release/YudreamUI/YudreamUI-mc26_3-fabric-Beta_0_2_0.jar`

`Beta_0_2_0` 新增 `atlas:2`：精细排版坐标、线性图片采样、可缩放原版物品、本地分类筛选与数量读数。默认二次元素材和最终提示词见[设计说明](../../Minecraft-26_3-plugin/YudreamUI/DESIGN.md)，新实机记录见[视觉验收](../../Minecraft-26_3-plugin/YudreamUI/VALIDATION-VISUAL.md)。

同版 `terrain:local-1` 提供 `mc-terrain` 本地地表组件：协议与 RmlUi 只传矩形/区块范围，Minecraft 区块读取、群系颜色和纹理缓存集中在 `./versions/mc26_3/`，升级游戏版本时适配该模块。它不主动加载区块、不发送地形到服务器，也不重载资源包。

## 平台与能力边界

当前留档只带 **Linux x86_64** `.so`，动态链接本机构建环境的 FreeType / C++ ABI。尚未建立跨发行版基线、Windows DLL、macOS 或 ARM 留档；不能当作这些平台的客户端发行包。加载失败时会回报服务端并使用降级界面。

已验证中文提交、Unicode 状态和 Emoji 码点保存；内置字库不覆盖所有 Emoji 字形。中文系统 IME 的真实候选窗/预编辑过程尚未实机验证。原版物品槽当前只携带 `material`，不等同于完整 ItemStack 组件或 CraftEngine 自定义模型。

原生标签页、开关、滚动、计数与模态框在本地处理，不等待服务器；购买、权限和资金操作由服务器确认。当前没有与箱子菜单在同硬件、同网络下的端到端延迟对照，不能宣称体感或延迟已达到箱子菜单。

## 内置字体与许可

`runtime/src/main/resources/builtin/ui-font.otf` 来自 **Noto Sans CJK SC 2.004**（TTC face 2），按 `fonts/subset.py` 固定字符范围裁剪，保留简体中文、拉丁字符、标点、箭头和菜单常用符号。使用 fontTools **4.67.0**，保留原始字体时间戳：

```bash
python3 -m pip install fonttools==4.67.0
python3 ./fabric-26_3-mod/YudreamUI/fonts/subset.py <NotoSansCJK-Regular.ttc>
```

原始 TTC SHA-256：`b76b0433203017ca80401b2ee0dd69350349871c4b19d504c34dbdd80541690a`。
当前裁剪输出 SHA-256：`1c87df4db085c15f909f8d9a7507bbff48db20beb1360dce7cc48668e38e3060`。
[Noto 上游与下载](https://github.com/notofonts/noto-cjk) · [OFL 许可](./runtime/src/main/resources/licenses/Noto-CJK-OFL.txt)。RmlUi MIT 许可随构建产物分发，仓库代码沿用根 LICENSE。

## 隔离联调

`integration/` 提供 Folia、真实 Fabric/MinecraftMcp、缩放、素材和业务验证脚本，输出只在 `build/integration/`。当前启动器复用本仓 Town 已准备的测试客户端 launch JSON、官方游戏缓存及测试 Folia，**不是全新机器一键启动器**；本机路径和凭证不入库。

环境就绪后：

```bash
python3 ./fabric-26_3-mod/YudreamUI/integration/verify.py native
python3 ./fabric-26_3-mod/YudreamUI/integration/verify.py fallback
python3 ./fabric-26_3-mod/YudreamUI/integration/verify.py assets
python3 ./fabric-26_3-mod/YudreamUI/integration/verify.py business
```

`business` 会修改隔离测试玩家背包、测试 Wallet 余额并创建测试小镇，不能对运营实例执行。完整记录见插件 [VALIDATION.md](../../Minecraft-26_3-plugin/YudreamUI/VALIDATION.md)。
