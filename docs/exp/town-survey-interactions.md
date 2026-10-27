# Town 测绘工具与区域反馈

### CE 发放的同款物品没有 Town 的 PDC

- **现象**：`/town select` 发的工具能选点，而 CE 物品指令/物品栏取得的 `town:surveyor` 左右键都无反应，左键还能破坏方块。
- **根因**：旧监听只识别 Town 发放时追加的 `town:surveyor` PDC 字节标记，未读取 CE 自定义 ID；只处理点击方块，远处准星或空气点击没有反馈。
- **规避/解决**：兼容 Town 标记与 CE `CraftEngineItems.getCustomItemId`，共用左右键选点入口；只认主手，菜单交互不进入测绘。空气点击按 64 格准星查询，空目标有提示。CE 自定义方块事件也接入并按玩家/点位/按键短时去重；Bukkit/CE 挖掘都保护工具目标，镇政厅监听跳过测绘尺，避免右键选点同时打开镇务界面。不要以调高事件优先级代替物品身份核查。
- **来源**：2026-10-27，旧 Beta_0_8_0 与新 Beta_0_9_0 同客户端对照；CE 26.9.2 公开 API、Folia 26.3 实机普通玩家、草方块与政厅核心选点。交互可能已取消且区分两只手，参见 [PlayerInteractEvent API](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerInteractEvent.html)。

### 轮廓淡也可能是埋在地里、长边采样不足

- **现象**：选完地块几乎看不到轮廓，选区越大越稀；确认区块每两秒闪一下。
- **根因**：原底边放在所点方块的 Y+0.05，常位于方块内部；长边最多 24 点、旧色较暗；区块预览绑定两秒 UI 刷新。
- **规避/解决**：XZ 轮廓另在玩家高度显示，保留建筑真实上下沿与角点；提高颜色/尺寸，对线段先裁剪到玩家周围 72 格再按固定间距采样，粒子量有界。选区和区块预览采用 8 tick 周期，隐藏被菜单遮住的普通选区。近距离截图复核密度，避免过密遮住地形。
- **来源**：Town Beta_0_9_0，长边/反向/竖线裁剪单测与 1280×720 实机截图；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/selection/BoundaryParticles.java`。
