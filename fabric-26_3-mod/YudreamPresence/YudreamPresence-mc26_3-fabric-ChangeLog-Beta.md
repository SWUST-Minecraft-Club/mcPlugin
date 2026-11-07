[Beta_0_1_0] 2026.11.07 20:21:09
1.新增 Fabric 26.3 服务端在线情况上报：进服 / 退服 / AFK 起止 / 定时全量在线名单。
2.新增直连 YuDream Admin 的 HTTP 上报、失败重试与待发事件落盘补发（进程重启不丢）。
3.新增服务端生命周期接入：首次 tick 接管、停服前空快照与队列 flush。
4.新增无 fabric-loom 的服务端模组构建：官方 server bundler 解包 + 服务端编译库 + Mixin 注解桩。
5.修正服务端 Mixin 注解桩保留级别与 Mixin 类的 Java 13 字节码目标，确保 Fabric 26.3 的 Mixin 0.8.7 能实际注入。
