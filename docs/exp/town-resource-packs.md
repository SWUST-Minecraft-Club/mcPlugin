# Town / CraftEngine / ArcMenu 资源投递

### 自建 GitLab 分发应接入 CE 原生 GitLab host

- **现象**：将资源分发迁至 `gitlab.yudream.online/yudream/mc-texture`，希望新地图合包后自动更新，玩家不必登录 GitLab。
- **根因**：Git 仓库网页不提供客户端所需的直接 ZIP 内容；Git 推送登录凭据、包仓库 Deploy Token 与 CE 使用的项目 Uploads API 也不是同一种认证。
- **规避/解决**：使用 CE 26.9.2 的 `type: gitlab`、实例 URL 和项目 ID `51`，通过 `use_environment_variables: true` / `CE_GITLAB_ACCESS_TOKEN` 提供项目 Developer + `api` 访问凭据，保留原工作流的 `upload`、`send_pack`。CE 从上传响应读取 `full_path`，保存 URL / SHA-1 缓存，并向客户端发送实际附件链接；无需把每个 ZIP 提交到 Git。分发包只含无凭据配置和示例文件，启动时从环境或独立令牌文件读取。匿名 GET 的 ZIP SHA-1 必须等于 CE 本地最终包，不能只看到仓库公开就判定可用。
- **来源**：2026-10-26，Town Beta_0_6_1 GitLab 部署配置；[CE GitLab 文档](https://xiao-momi.github.io/craft-engine-wiki/getting_start/set_up_host/gitlab/)、CE 26.9.2 `GitLabHost` 与项目 `51` 实际上传、匿名下载校验。配置见 `./Minecraft-26_3-plugin/Town/deploy/craftengine-gitlab.yml`。

### 服务器上有图片和本地 ZIP，不代表客户端能显示地图

- **现象**：干净服务端首次启动后 CE 核心材质缺失，地图出现超出面板的大白框；菜单文字、矩形与区块角标正常。
- **根因**：`arcmenu resources` 仅构建 ArcMenu 的本地 ZIP / 图片字体索引。CE 初次解析资源并不执行 `reload_pack` 工作流，原最小包里没有最终 ZIP 和托管缓存；客户端缺少图片字体时，把放大的字形显示为缺字框。旧检测只确认 ArcMenu ZIP 存在。
- **规避/解决**：等待 CE `isFullyLoaded`，在异步调度中调用 `packManager.triggerWorkflows("reload_pack")`，完成生成、合包、上传和发送；资源操作忙时有界重试。验证 CE 最终 ZIP 的字体 providers 和每张 PNG CRC 与 ArcMenu 当前包一致，再发布该批地图。
- **来源**：2026-10-26，Town Beta_0_6_1；交付 Beta_0_6_0 干净包实跑复现，`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/arcmenu/TownMapRenderer.java`。

### CE 配置阶段成功下载，不会触发普通玩家资源包事件

- **现象**：客户端日志已加载服务器包，物品材质正确，服务端 `PlayerResourcePackStatusEvent` 记录仍为空；只靠这个事件的地图一直等待。进入 PLAY 后 `/ce feature send-pack` 才得到 ACCEPTED、DOWNLOADED、SUCCESSFULLY_LOADED。
- **根因**：CE 在 1.20.2+ 的连接配置阶段自行处理资源包响应；此时还没有普通 Bukkit Player 的资源包事件。
- **规避/解决**：进入游戏后补发缓存包取得 PLAY 回执；尊重 CE 的 send-on-join 和玩家包选择。通过 CE `prepareResourcePacks` 取得当前最终 ZIP 的 SHA-1 对应的 UUID，只接受同一玩家、同一包的成功事件；旧包或无关包成功不能解除等待。缺少回执时采用无图片字形的可操作网格。手工 CE 重建后的回执也须重新校验最终包内容。
- **来源**：2026-10-26，CE 26.9.2 / Folia 26.3 / MinecraftMcp 客户端测试；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/hook/TownResourcePacks.java`。

### 方形 image 的字体左边距和基线需要独立补偿

- **现象**：正确加载资源包后，地形仍偏右、偏上，越过地图边框。
- **根因**：当前 ArcMenu 3.2 image 由单字形 TextDisplay 绘制，方形字体 height=8、advance=9、ascent=4。实际位图偏右宽度的 1/16、偏上高度的 1/4；只把 offset 当中心点会偏移。
- **规避/解决**：对 Town 不透明方形地图在设计坐标的中心上减去 `w/16` 和 `h/4`，随后再应用原有 G 和 z 补偿。保持 ppb=42.7，资源索引与图片尺寸不变。实机按地图边框检查，不可用没有纹理的缺字框校准图片。
- **来源**：2026-10-26，Town Beta_0_6_1，1280×720 实机截图；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/arcmenu/MenuBuilder.java`。

### 每个地图中心合整包的成本，需要靠缓存与合批控制

- **现象**：打开新地图或平移时有下载/重载画面，配置重载和重启也曾重复上传同样内容，连续平移最长额外等 30 秒。
- **根因**：当前 ArcMenu 地形为图片字体，不是独立传输的原版地图：7×7 区块采样 → 224×224 PNG → 字体索引 → CE 完整合包/上传/发送 → 客户端重载。旧渲染器每次配置重载重建，启动总重写占位图，`indexed` 仅在内存；原先还在所有玩家入服后补发同包取 PLAY 回执。只调下载超时无法减少这些步骤。
- **规避/解决**：Town Beta_0_9_0 校验源 PNG、ArcMenu 字体 providers 和 CE ZIP 内容后恢复地图缓存；配置重载保留渲染器。相邻视图共享有界区块采样 Future，区块所属区域线程取得 `ChunkSnapshot`，异步着色，避免直接在异步回调读世界。PNG 写完保留同 key 任务直到合包结束；1 秒短延迟合批，成功后 5 秒冷却，失败退避 30 秒。仅首次实际查看地图且无对应回执时补发缓存包，普通入服不额外重载；新包仍严格匹配玩家/包 UUID。地形为快照，不随建造实时更新；首次新位置仍有整包重载成本。
- **来源**：2026-10-27，85 项测试与 Folia 26.3 实机；从未缓存中心生成、GitLab 匿名 ZIP 与客户端加载、回切缓存和 `/town admin reload` 无额外上传。详见 Town `./assets/VALIDATION.md` Beta_0_9_0 记录；本条取代上文“全部玩家入服补发”的当前实现描述。
