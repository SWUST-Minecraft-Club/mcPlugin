# JoinAnnounce

进服公告插件：玩家进服后按配置延迟弹出一个 **26.3 dialog 弹窗**（默认），也可以切回聊天栏或两者同时显示；
附带首次进服文案、进出服全服广播，以及主菜单里的「服务器公告」入口。

| 能力 | 配置段 | 说明 |
| --- | --- | --- |
| 进服公告 | `announce` | 延迟 N tick（默认 20 = 1 秒）发送；`display` 决定 channel |
| 公告弹窗 | `announce.dialog` | dialog 的标题、正文宽度、按钮文字、ESC 关闭开关；正文就是 `announce.lines` |
| 徽章图标 | `announce.dialog.icon` | 弹窗顶部的徽章图标：`head`（皮肤头颅像素画）或 `pack`（资源包高清贴图） |
| 资源包 | `resource-pack` | 进服自动下发社团资源包（高清徽章），可强制安装；带 SHA-1 时按 hash 缓存 |
| 标题 / 音效 | `announce.title` / `announce.sound` | 可选，默认关闭；音效键写错只告警并跳过 |
| 首次进服文案 | `first-join` | 接在正文后面，只对第一次进服的玩家生效 |
| 进服 / 退服广播 | `broadcast.join` / `broadcast.quit` | 默认关闭；打开后才替换原版 join / quit 消息 |
| 主菜单入口 | `menu` | 主菜单里的「服务器公告」，点击按 `announce.display` 显示；软依赖 MainMenu |

文本全部使用 MiniMessage，占位符：`<player>` 玩家名、`<online>` 当前在线人数、`<max>` 最大人数、`<world>` 所在世界。
单行解析失败时按纯文本显示，不会让整条公告丢失。

## 显示方式

`announce.display` 三选一，默认 `dialog`：**进服公告只在弹窗里显示，插件不往聊天栏发任何消息**。

| 值 | 效果 |
| --- | --- |
| `dialog`（默认） | 只弹 dialog，公告正文进弹窗，不进聊天栏 |
| `chat` | 只把 `announce.lines` 逐行发到聊天栏 |
| `both` | 弹窗 + 聊天栏都来一遍 |

dialog 用的是 26.3 的运行时 API（`Dialog.create(...)` + `player.showDialog(...)`），
**不需要 bootstrapper、内置数据包或注册表项**，也不会出现在 ESC 暂停菜单里。

## 徽章 / 图标

dialog 里只能放**文字**和**物品图标**，不能直接贴图片。徽章有两种来源（`announce.dialog.icon.source`）：

### `pack`：资源包贴图（推荐，高清）

dialog 的物品正文**永远按原版 16 像素绘制**（`width`/`height` 只是布局框），所以资源包把
`structure_void` 的模型放大了 4 倍并用 GUI display 平移（26.3 里 translation 单位是 **1 像素**）
居中：`scale [4,4,4]` + `translation [24, -24, 0]`，
弹窗里放一个 `STRUCTURE_VOID` 物品就是 **64 像素**的高清徽章（256x256、圆形裁剪、四角透明）：

```yaml
announce:
  dialog:
    icon:
      enabled: true
      source: pack
      size: 64          # 固定 64：资源包模型已放大 4 倍，改大会错位
resource-pack:
  enabled: true
  url: "https://.../swust-badge-pack.zip"
  sha1: "aec757f40c075a70024c483520c5d0d2573e3ab7"   # 可选，40 位十六进制
  force: true                                        # 强制安装：必须接受才能进服
  prompt: "<gray>社团资源包，请接受下载。</gray>"
```

> 弹窗里出现的白色方框是物品控件获得**键盘焦点**时的原版描边（`outline`），不是我们的贴图；
> 点一下别处 / 按 Tab 移走焦点就没了。

- 资源包 zip 源文件在 `./assets/pack/`（`pack.mcmeta` 用 26.3 的资源包 format **97**），
  产物 `./assets/swust-badge-pack.zip`，构建后 SHA-1 如上。
- 插件在玩家进服时调用 `Player#setResourcePack(uuid, url, sha1, prompt, force)` 下发；
  带 `sha1` 时客户端按 hash 缓存，之后进服不会重复下载。
- zip 需要放在玩家能访问的 http/https 地址（GitHub 直连慢就换图床 / 服务器网站）。
- 没装上资源包时 `pack` 图标显示为无贴图物品，等资源包生效后重新打开弹窗即可。
- 该物品模型是**全服生效**的：装了资源包后，任何 `structure_void` 物品都会显示徽章
  （生存服里这个物品本来就不可获得，无影响）。

### `head`：皮肤头颅（无需资源包）

把徽章画进一张 64x64 皮肤的「面部」区域（(8,8)-(16,16)，8x8 像素），用 `PLAYER_HEAD` 展示：

```yaml
announce:
  dialog:
    icon:
      enabled: true
      source: head
      texture-url: "https://.../swust-badge-skin.png"
      size: 48
```

- 皮肤贴图由**玩家客户端**从 `texture-url` 下载；地址不可达时图标回退为默认头颅。
- 物品在 dialog 里固定 16 像素，所以 `head` 来源的图标就是普通物品大小；要大图请用 `pack` 来源。
- 仓库内置了社团徽章皮肤 `./assets/swust-badge-skin.png`（量化成蓝 / 灰 / 白三色的像素画，
  中文环字展示不了；社团名等文字写在公告正文里）。

图标不需要时 `enabled: false` 即可；配置非法会在启动 / reload 时告警并跳过。

## 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/joinannounce reload` | `joinannounce.admin`（默认 OP） | 重载 `config.yml`，主菜单入口的开关 / 排序值同时生效 |
| `/joinannounce preview` | `joinannounce.admin`（默认 OP） | 按 `announce.display` 预览一遍（默认只弹窗）；控制台只打印文本 |

已进服的玩家不会补发公告，改完文案后可以用 `preview` 自己看效果。

## 配置要点

- `announce.delay-ticks`：进服后延迟多少 tick 发送，避开原版进服消息刷屏；`0` 表示下一 tick 就发。
- `announce.enabled: false`：关闭进服公告（弹窗、聊天栏、标题、音效一并关闭），但进出服广播与主菜单入口不受影响。
- `announce.lines`：弹窗正文与聊天栏文本共用；`first-join.lines` 排队追加在后面。
- `announce.dialog.width` 单位为像素（60~600，默认 260），过长的行建议自己拆成多行。
- `announce.dialog.icon.size`：`head` 来源 8~256（默认 48，但物品本身固定 16 像素）；`pack` 来源固定 64。
- `resource-pack.sha1` 是 zip 的 SHA-1（40 位十六进制，可用 `sha1sum` / PowerShell `Get-FileHash` 算）；填了才能按 hash 缓存。
- `broadcast.*` 默认关闭：进服公告不碰聊天栏；打开后才接管原版进服 / 退服消息，`message` 留空则关闭该条（原版消息也不再显示）。
- `menu.enabled: false`：不注册主菜单入口；`menu.order` 是功能区排序值（越小越靠前）。
- `menu.material` 无效时回退 `WRITTEN_BOOK` 并在控制台告警。

## 依赖

- 运行期**不需要** MainMenu：没装时进服公告、广播照常工作，只跳过菜单入口（启动日志会提示）。
- 装了 MainMenu 时通过 `compileOnly project(':MainMenu')` 编译、调用 `MainMenuApi` 注册入口，位置与排序由主菜单管理。

## Folia 说明

- `paper-plugin.yml` 声明 `folia-supported: true`，编译依赖 Folia API。
- 延迟公告走 `player.getScheduler().runDelayed(...)`，玩家在延迟期间退服时任务自动取消；弹窗也在该玩家区域线程里发送。
- 广播不遍历在线玩家：直接改写 `PlayerJoinEvent#joinMessage()` / `PlayerQuitEvent#quitMessage()`，由服务端统一广播。
- 配置在 `/joinannounce reload` 时整体替换为不可变快照（`volatile` 引用），不同区域线程读到的都是完整配置。
- 弹窗的关闭按钮复用同一个点击动作实例，避免每次进服都注册一条新的长效回调。

## 构建

```bash
./gradlew :JoinAnnounce:build
# 产物：./Minecraft-26_3-plugin/JoinAnnounce/build/libs/JoinAnnounce-mc26_3-plugin-Beta_0_4_2.jar
```

测试服：`./gradlew :JoinAnnounce:runServer`（共用仓库根目录的 `./run/`）。
