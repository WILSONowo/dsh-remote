# 协议与迁移

手机通过 HTTP RPC 和 WebSocket/SSE 事件流连接电脑插件。中转模式在 API 路径前添加 `/h/<hostId>`，服务器与插件使用 v1 多路复用隧道；协议实现位于 `dsh-remote-access/src/tunnel.ts`。
后端独立维护于 [dsh-remote-backend](https://github.com/WILSONowo/dsh-remote-backend)。协议变化需运行两仓库联调测试。

`modern.ts` 在 `typertGateway` 和 `connection` 服务就绪时挂载，将手机 RPC 映射到 `0.2.1-rc.5` 的命名参数网关。历史通过 `session/follow` 取得一致游标，再用 `session/page` 翻页；审批和问答通过 `$events/result` 应答。每个事件连接使用独立 scoped Peer，断线和设备撤销释放 Peer。原生桌面浏览器继续使用宿主鉴权。

旧版共享 `channel-token` 和按设备 ID 恢复的配对信息不再接受，升级后手机需要重新配对。独立设备凭证在电脑只保存摘要；人工配对必须凭请求方随机秘密领取。配对码十分钟有效、五次错误锁定、使用后作废。

插件配置位于 `$DSH_HOME/remote-access/`，中转配置为 `relay.json`，设备摘要为 `devices-v2.json`。旧会话和其他 DeepSeek Harness 数据不会自动迁移或删除。第三方保险库等扩展功能仍要求电脑安装对应扩展。

应用内图标为 `app/src/main/res/drawable-nodpi/ic_dsh_desktop.png`，启动器与通知使用同一轮廓的矢量资源。可用 `scripts/import-desktop-icon.py` 从指定 PNG 重建，依赖 Pillow、NumPy 和 OpenCV；不自动读取任何本机安装目录。
