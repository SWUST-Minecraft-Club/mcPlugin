# YudreamUI · 服务端驱动界面

`Beta_0_2_0` · Minecraft / Folia **26.3** · Java **25**。服务端插件提供文档、状态、素材清单与动作，Fabric Mod 用 **RmlUi 6.3** 在客户端排版和渲染。客户端留档仅包含 **Linux x86_64** 原生库。

- 自适应 XHTML / RCSS、中文输入、表单、标签页、滚动、模态框和原版物品渲染。
- 内置方块风格控件和中文字库，使用内置组件时 **0 个外部资源请求**。
- 图片、字体按 SHA-256 缓存和替换，不触发 Minecraft 资源包重载；坏资源保留原界面。
- RPG 分支对话、Wallet 真实扣款与物资交付、MainMenu / Town 的显示接管。
- 圈地页本地地形组件：读取已加载区块，逐方块底图与服务端领地状态对齐，包含玩家位置、群系颜色、水深/高差明暗；无地形能力保留状态网格。
- 二次元向导与 Minecraft 村庄插画、精细中文排版、高密度商品网格、本地分类、镇务资料/操作分栏；[设计与素材提示词](./DESIGN.md)、[新版本实机截图](./VALIDATION-VISUAL.md)。
- 无 Mod 时立即使用语义相同的箱子菜单；可选择原生 Dialog 或注册其它菜单插件适配器。

[构建与客户端边界](../../fabric-26_3-mod/YudreamUI/README.md) · [HTML / 协议 / 接口](../../fabric-26_3-mod/YudreamUI/PROTOCOL.md) · [迁移说明](./MIGRATION.md) · [实机验证](./VALIDATION.md)

## 安装

1. 把 `./release/YudreamUI/YudreamUI-mc26_3-plugin-Beta_0_2_0.jar` 安装到 Folia 的 `plugins/`。
2. 需要原生界面的客户端安装同版本 Fabric Mod 与 **26.3 对应的 Fabric API**。不安装 YudreamUI Mod 的玩家仍能使用箱子菜单。
3. 商店需要 Wallet；主菜单接管需要 MainMenu **Beta_1_10_0+**；Town 接管需要 Town **Beta_0_11_0+**。Town 当前仍使用 ArcMenu 3.2 的菜单源和 META API，因此须保留 ArcMenu。
4. 启动后检查 `plugins/YudreamUI/shop.yml` 的币种。默认 `coin` 必须与 Wallet 的本服 code 对应；不自动改名、兑换或迁入余额。

现有配置只在缺失时释放，不覆盖运营配置。完整重启应用商店、对话和接管配置；`/yui refresh` 仅供管理员刷新演示 HTML 和演示素材。

## 命令

| 命令 | 用途 |
| --- | --- |
| `/yui` | 内置组件演示，演示购买仅修改会话数字 |
| `/yui shop` | 真正通过 Wallet 购买商品 |
| `/yui dialogue [id]` | RPG 对话，默认 `guide` |
| `/yui status` | 当前客户端能力、显示后端和文档版本 |
| `/yui inventory` / `dialog` / `trmenu` | 强制指定演示降级后端 |
| `/yui assets` / `refresh` | 外部素材演示 / 重读演示定义，管理员使用 |
| `/yui theme` | 重读素材配置并热替换自己的当前页面，管理员使用 |
| `/yui orders` | 查看持久订单、玩家、金额与状态，管理员使用 |
| `/yui import-shop <Wallet币种>` | 导出经过校验的 Arc 商店购买目录，管理员使用 |

`yudreamui.use`、`yudreamui.shop` 默认所有玩家可用；`yudreamui.admin` 默认 OP。`invoke` 是可选降级适配器的会话入口，只接受对应玩家当前 TrMenu 后端的随机 ticket 和已声明控件 ID。

## RPG 对话

编辑 `plugins/YudreamUI/dialogues.yml`，每个会话包含说话人、物品头像、起始节点和分支。选项只能配置一个目标：`next`、玩家 `command`、`shop: true` 或 `close: true`；可附权限。`{player}` 替换查看者姓名。

当前有分支、最近 12 条记录和商店跳转。NPC 插件可调用 `YudreamUiApi.openDialogue(player, "guide")`；尚未提供实体点击绑定或打字机动画。文案、权限和分支均在服务端校验。

原生对话固定在屏幕下方，左侧小立绘与半透明对话框不遮住上方场景，也不添加整屏遮罩。正文、选项各自滚动，对话记录在同一框内展开；宽屏高度 240 像素，缩窗最多占窗口高度的 42%。

## 展示素材热更新

默认村庄与向导素材内置于 Mod，0 个外部请求。运营方可配置 HTTPS 图片覆盖；旧配置缺少此键时使用内置素材：

```yaml
presentation-assets:
  - id: atlas-village
    url: https://cdn.example/ui/village.png
    hash: <图片内容的64位小写SHA256>
  - id: atlas-guide
    url: https://cdn.example/ui/guide.png
    hash: <图片内容的64位小写SHA256>
```

更新内容和哈希后，管理员在当前界面执行 `/yui theme`。新素材校验成功后替换，保留分类、输入、滚动和服务器状态；失败保留旧页面。不触发 Minecraft 资源包重载。当前命令只刷新执行者页面，其他玩家下次打开会使用最新配置。覆盖素材仍受协议中的尺寸/容量限制。

新页面使用 `atlas:2` 能力，需 `Beta_0_2_0` 客户端；旧客户端自动走箱子菜单。内置村庄图为示意美术；圈地页地形来自客户端已加载区块，领地状态来自 Town。棋盘格表示本地未加载的区域，平移地图不主动请求服务器加载区块。地形最多约每两秒重新采样一次，批次受预算限制；方块变化逐步反映，远处和地下切片未提供。

## Wallet 商店

`shop.yml` 支持 1～64 个原版物品商品、分类、每份数量、十进制单价、币种及可选权限。选择份数 → 服务器封存报价 → 明确确认 → 异步 Wallet 扣款 → 背包交付。首次安装的默认目录是原 Arc 商店迁移的 25 项购买商品；已有 shop.yml 不覆盖，四项开发演示另在 examples/。

- 先校验币种、金额精度和背包空间，再创建订单和扣款。每位玩家同一时间只处理一笔未完成订单。
- 业务号为 `yudreamui:shop:<订单UUID>`；未知网络结果重试同一个业务号，不生成新扣款。
- 已支付但背包不足时保留领取入口；重启后可继续原订单。更换 Wallet 来源后阻止原订单自动重试。
- 订单保存在 `plugins/YudreamUI/shop-orders/`。`PAYMENT_PENDING → PAID → DELIVERING → DELIVERED`；明确余额不足为 `DECLINED`；交付结果不明确为 `REVIEW`。

**崩溃边界：** Minecraft 玩家背包文件与 Wallet 账本不能原子提交。写入背包期间崩溃的 `DELIVERING` 在重启后变成 `REVIEW`，不会自动再发一次。管理员用 `/yui orders` 对照 Wallet 同业务号流水和玩家背包核对；当前没有在线裁决或退款命令。需要人工恢复时停服备份整个订单目录及相关玩家数据，确认未交付且扣款成功后将该订单改为 `PAID`，确认已交付后改为 `DELIVERED`；无法确认时保留 `REVIEW`。不要删除订单来解除购买限制。

生产前使用测试 Wallet 和隔离玩家核对本服目录、币种、精度与权限；本仓验证没有使用官网账户或生产余额。

## 降级与接管

`config.yml` 默认：

```yaml
takeover:
  enabled: true
fallback:
  backend: inventory
  trmenu-id: yudreamui
assets: []
```

原生文档同时生成 `FallbackPlan`：正文、类型化输入、按钮、图标与动作 ID。箱子菜单会话内更新物品；需要输入时打开 Dialog 表单后返回原菜单。原生 Dialog 后端保留相同字段和服务端动作。

这是 **YudreamUI 自己文档的语义降级**。任意第三方箱子菜单只有槽位、物品和点击包，无法从协议自动还原布局、动态表达式、权限和交易逻辑。其它插件可通过 `FallbackProvider` 注册适配器；当前 TrMenu 桥为实验状态，实际依赖启动未完成，不能宣称已支持其完整 Folia 26.3 运行链路。

关闭 `takeover.enabled` 后 MainMenu / Town 沿用原显示入口。接管范围、未支持的 Arc 动作与地图边界见 [迁移说明](./MIGRATION.md)。

TrMenu 随包 YAML 仅覆盖 showcase 演示的 buy/save/slow 控件，不是通用语义计划转译器；其它业务界面会主动回到 inventory。完整第三方菜单接入应实现自己的 FallbackProvider。
