# HandbookCore Beta_0_1_1 安装

适用：Minecraft / Folia 26.3、Java 25。书本外观需要 CraftEngine 和客户端成功加载服务器资源包。

1. 停服，把安装包内的 `./plugins/` 合并到服务端根目录。已有 HandbookCore jar 应移出，避免重复加载。
2. 更新时先备份 `plugins/CraftEngine/resources/handbookcore/`，再替换该资源目录；保留自己增加的图片配置。
   手册正文 `plugins/HandbookCore/content/` 与 `config.yml` 不在安装包中。
   从 Beta_0_1_0 升级会先备份原 `content/server.yml` 到 `plugins/HandbookCore/backups/`，再移除三个旧玩法示例；
   更新日志和其他自定义正文保留，自定义文章仍引用的分类也保留。其他插件通过 API 注册的内容不受影响。
3. 首次使用 CraftEngine，应先让它独立启动一次、释放 `resources/internal/`，再合并手册资源。
4. 开服后执行 `/ce reload all`，通过当前 CE 的托管方式把新资源包发给玩家，重新进服确认加载完成。
5. 执行 `/handbook status`、`/handbook`。底部显示「当前页 / 总页数」，每次翻动显示左右两张纸。

客户端可选安装 `MainMenuRepair` 的 `Beta_1_2_0` 或后续版本：移除点书本外层出现的原生白色焦点框，
整体居中并隐藏重复的灰色「合上手册」按钮。没有该模组也可使用翻页与检索，但保留原生 Dialog 的外框和布局。
客户端模组不包含在本服务端安装包中。

缺少 CE 或玩家拒绝资源包时使用原生文字目录。`/handbook reload` 重载正文；配置出错保留上次成功的内容。
首次安装只带一条更新日志，其余章节由插件贡献。若需要完全空的核心正文，删除
`plugins/HandbookCore/content/server.yml` 后执行 `/handbook reload`；重启不会补回该文件。
不要删除 `plugins/HandbookCore/.content-initialized`，它记录正文已经完成初始化。

API jar 供其他插件 `compileOnly` 使用，不应放进服务端 `plugins/`。

`SHA256SUMS.txt` 是包内逐文件校验表；在解压目录可执行 `sha256sum -c SHA256SUMS.txt`。
完整 YAML、API、图片与指令示例见仓库 `./Minecraft-26_3-plugin/HandbookCore/README.md`。
