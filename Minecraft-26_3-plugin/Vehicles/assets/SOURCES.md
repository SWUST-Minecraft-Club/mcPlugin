# 模型来源与许可

## 实际使用的资产

小镇漫游车的车身、车轮、三种配色、像素纹理、叶片徽标与 Blockbench 源文件，
均由本项目使用 `./generate_models.py` 的原创几何和绘图代码生成，未包含下载模型或第三方贴图。
源文件和生成资产按仓库 MIT 许可证分发；制作辅助工具为 Codex。

模型由 83 个车身立方体与每轮 14 个立方体构成，四轮通过独立展示实体转动。
所有原版 JSON 元素均使用原版支持的立方体，未引入任意网格扩展。
资源包命名空间为 `vehicles`，物品模型通过 `item_model` 指定，不覆盖原版纸的模型。

## 本次筛选过的网上候选（未使用）

- [Blue Low Poly Pick-Up Truck / polyjo](https://blockbenchworkshop.com/model/polyjo/blue-pick-up-truck)：
  页面标注 CC BY-NC 4.0，使用通用网格；不作为本次可自由部署的原版载具素材。
- [Jurassic Park Jeep / 9greenworks](https://blockbenchworkshop.com/model/9greenworks/jurassic-park-jeep)：
  页面标注 CC BY 4.0，但下载接口在本次匿名访问时返回 HTTP 401，未取得或使用模型。
- [Free Low Poly Vehicles Pack / rgsdev](https://opengameart.org/content/free-low-poly-vehicles-pack)：
  页面标注 CC0，但为通用低多边形游戏资产；本次选择直接制作 Minecraft 原生立方体模型。

以上链接是筛选记录，未授权来源不明的转载，也没有将候选模型当作本项目原作。

## 格式与技术依据

- 目标资源格式取本机官方 Minecraft 26.3 客户端 `version.json`：resource **97.1**。
- [Paper 展示实体 API](https://docs.papermc.io/paper/dev/display-entities/)。
- [Paper 实体传送与保留乘客](https://docs.papermc.io/paper/dev/entity-teleport/)。
- [Paper / Folia 实体调度器](https://docs.papermc.io/paper/dev/folia-support/)。
