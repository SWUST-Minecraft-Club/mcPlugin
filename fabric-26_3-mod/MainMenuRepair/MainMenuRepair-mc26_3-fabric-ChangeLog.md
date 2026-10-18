# MainMenuRepair ChangeLog（正式版）

格式：`[正式版x_x_x] yyyy.mm.dd` 或 `[正式版x_x_x -> y_y_y] yyyy.mm.dd`，下面列本次改动。
测试期的逐版本改动见同目录的 `MainMenuRepair-mc26_3-fabric-ChangeLog-Beta.md`。

---

[正式版1_0_0] 2026.10.18
1.新增：移除 ESC 暂停菜单右下角 4 个原版图标按钮（报告 Bug / 给予反馈 / 好友 / 举报玩家）
2.新增：清空服务器自定义选项按钮上的“第三方内容”警告提示
3.新增：ESC →「主菜单」一步到位 —— 把暂停菜单里的按钮换成直接发 `/cd`，跳过中间的 dialog 页面
4.新增：补上“手持时钟右键空气”触发（服务端不会为这种点击产生交互事件，由客户端检测后上报 `/cd clockair`）
5.新增：状态文件 `mainmenu-repair-status.txt`，记录各条钩子是否生效，便于排查

依赖说明：第 3、4 条需要服务端装有 MainMenu（`/cd`、`/cd clockair` 由它提供）。
