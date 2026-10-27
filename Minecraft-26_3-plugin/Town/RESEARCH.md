# 科研与星露联动

Town `Beta_0_10_0`；RptStardew `1.6.0-folia26.3-beta.1`；目标 Folia 26.3 / Java 25。

## 配置

`./Minecraft-26_3-plugin/Town/src/main/resources/config.yml` 给出完整默认值。

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `civic.research-base-seconds` | 1800 | 一级研究的基础秒数，乘目标等级 |
| 科技项 `research-seconds` | 不设置 | 覆盖该科技的基础秒数，仍乘目标等级 |
| `civic.research-speed-cap-percent` | 100 | 科研加速叠加上限 |
| `civic.skill-xp-cap-percent` | 100 | 每个技能经验加成叠加上限 |
| 政策项 `research-speed-percent` | 0 | 全镇科研速度增加百分比 |
| 政策项 `farming-xp-percent` | 0 | 耕种经验增加百分比 |
| 政策项 `fishing-xp-percent` | 0 | 钓鱼、蟹笼经验增加百分比 |
| 政策项 `mining-xp-percent` | 0 | 采矿经验增加百分比 |

科研按工作量推进，速度 +25% 对应耗时除以 1.25。研究存档包含目标等级、所需工作量、已完成工作量、结算时间和各加速的比例与到期时间；跨过到期时间按段计算。中途买入/续购政策会先结算旧时段，再保存新加速，避免追溯加速。已开始的研究保留当时记录，基础耗时修改仅影响新研究。

每镇同时研究一项；研究启动与镇库扣费沿用同一持久交易，按交易 ID 去重。完成时只更新等级，不再次扣费；保存失败恢复内存旧状态并重试。旧存档的科技等级直接保留，缺少研究记录表示没有在研项目。

## 可选接口

RptStardew 使用 Bukkit `ServicesManager` 注册 `org.rptStardew.api.StardewProgressionApi`。Town 仅编译依赖 `./libs/rptstardew-progression-api-1.6.0.jar`，不将接口类打入自己的 JAR；运行时通过可选 `RptStardew` 依赖共享同一份类。

接口消费示例：

```java
var api = Bukkit.getServicesManager().load(StardewProgressionApi.class);
if (api != null) {
    api.registerBonusProvider(plugin, context -> {
        // 使用 UUID、技能和结算坐标读取线程安全快照；0.25 表示经验 +25%。
        return 0.25;
    });
}
// 插件关闭时：api.unregisterBonusProvider(plugin);
```

同一插件再次注册会替换提供器。回调可被 Folia 多区域并发调用，必须快且线程安全，不能访问背包、方块或玩家实体。非法、负数及非有限返回值忽略；提供器异常不打断奖励。停用插件的提供器不参与计算，全部来源相加后由核心封顶 +100%，经验四舍五入；基础经验为 0 时保持 0。

Town 提供器根据操作位置找到镇域，复用 `MEMBERS`、`RESIDENTS`、`ALL`、有效租约和到期规则。不改星露谷等级门槛、经验表或防刷条件。缺少新接口时技能政策采购会被拒绝，科研功能仍可运行。安装与升级均完整重启。

API JAR 来自本工作区 RptStardew 本次新增的四个公开接口类；在其工程执行 `apiJar` 可重建。Town 构建可通过 `-PstardewApiJar=<接口JAR>` 指定替代位置。测试夹具另需 `-PstardewJar=<完整星露谷JAR>`；它不随生产包发布。

## 复现验证

```bash
./gradlew :Town:build :Town:researchIntegrationJar
python3 ./scripts/test-town-research.py \
  --java <Java25路径> --template <既有隔离Folia测试服> \
  --directory ./run/new-research-test
# 第二个新目录加 --without-stardew，验证缺少核心时的启动和拒绝扣款。
```

模板只读复制已接受的 EULA、Folia、CraftEngine、ArcMenu 与依赖缓存。脚本仅接受新空目录，测试监听本机 25641，使用秒级配置，并依次启动、正常关服、重启。报告在 `./Minecraft-26_3-plugin/Town/build/reports/research-*.json`。测试夹具名为 `TestPluginTownResearch`，只允许安装在可丢弃隔离服。

依据：[Bukkit 服务接口](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/ServicesManager.html)、[Folia 调度契约](https://docs.papermc.io/paper/dev/folia-support/)。实际兼容以 Folia 26.3 的编译及运行结果为准。
