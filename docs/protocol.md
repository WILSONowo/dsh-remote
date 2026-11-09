# 协议与迁移

手机通过 HTTP RPC 和 WebSocket/SSE 事件流连接电脑插件。自 4.0 起，这些请求全部在端到端安全通道内传输：外层 WebSocket（子协议 `dsh-e2e.1`）承载固定电脑公钥的 TLS 1.3，直连入口为电脑端口上的 `/api/remote-access/secure`，中转入口为后端的 `/relay/v2/connect/<hostId>`。规范见 [端到端安全通道](e2e-secure-channel.md)。
服务器与插件之间仍使用 v1 多路复用隧道，只透传字节；协议实现位于 [电脑插件的 tunnel.ts](https://github.com/WILSONowo/dsh-remote-access/blob/main/src/tunnel.ts)，后端保存同一份副本。旧的 `/h/<hostId>/api/...` 明文转发已移除。
后端独立维护于 [dsh-remote-backend](https://github.com/WILSONowo/dsh-remote-backend)。协议变化需运行三端联调测试（插件测试设置 `DSH_RELAY_BACKEND`）。

`modern.ts` 在 `typertGateway` 和 `connection` 服务就绪时挂载，将手机 RPC 映射到 `0.2.1-rc.5` 的命名参数网关。历史通过 `session/follow` 取得一致游标，再用 `session/page` 翻页；审批和问答通过 `$events/result` 应答。每个事件连接使用独立 scoped Peer，断线和设备撤销释放 Peer。原生桌面浏览器继续使用宿主鉴权。

旧版共享 `channel-token` 和按设备 ID 恢复的配对信息不再接受，升级后手机需要重新配对。独立设备凭证在电脑只保存摘要；人工配对必须凭请求方随机秘密领取。直连配对码十分钟有效、五次错误锁定、使用后作废。

插件配置位于 `$DSH_HOME/remote-access/`，中转配置为 `relay.json`，设备摘要为 `devices-v2.json`，电脑随机安装身份和显示名称为 `identity.json`，安全通道密钥为 `secure-channel.json`（删除后所有手机需重新配对）。旧会话和其他 DeepSeek Harness 数据不会自动迁移或删除。第三方保险库等扩展功能仍要求电脑安装对应扩展。

应用内图标为 `app/src/main/res/drawable-nodpi/ic_dsh_desktop.png`，启动器与通知使用同一轮廓的矢量资源。可用 `scripts/import-desktop-icon.py` 从指定 PNG 重建，依赖 Pillow、NumPy 和 OpenCV；不自动读取任何本机安装目录。

## 自动注册与扫码

后端一次性配置 `registration.enabled` 和 SQLite 数据库路径后，电脑可通过 `POST /relay/v1/register` 自动注册；后端 2.0.0 起拒绝静态 `hosts` 字段，注册数据库为必填配置；3.0.0 起只提供字节透传的 `/relay/v2/connect`。
插件在联网前持久保存 32 字节随机凭证，请求使用 Bearer 头；后端仅保存凭证摘要。主机 ID 为 `SHA256("dsh-remote-host-v1:" + credential)` 的前 32 个十六进制字符，凭证由密码学随机源产生，不取自机器名或硬件。重复请求返回相同 ID，改名不更换身份，切换服务器会生成新的注册凭证。

扫码和粘贴使用 App 内的 `dshremote://pair#…` 连接格式，不依赖网页或 Android 系统深链接。4.0 插件生成 `dshremote://pair#3.<base64url>`，在 v2 字段后加入 32 字节电脑公钥指纹；4.0 App 只接受 v3，v1/v2 没有指纹，会提示更新插件。直连地址二维码为 `dshremote://direct#1.<base64url>`（指纹、地址、名称）。

v3 解码后的字节顺序为：8 字节有效期（Unix 毫秒）、16 字节主机 ID、16 字节电脑 UUID、32 字节完整 ticket、32 字节公钥指纹，随后为服务器地址和电脑名称；两个字符串均使用 2 字节长度前缀及 UTF-8 编码，所有整数为大端序。地址最多 2048 字节，名称最多 256 字节且校验不超过 64 个字符，不接受尾随数据。协议跨端测试共用合成向量 `app/src/test/resources/pairing-compact.json`。v1 字段为 `v`、`server`、`hostId`、`computerId`、`name`、`ticket`、`expiresAt`（毫秒）。二维码在电脑本地生成，不调用第三方二维码服务。ticket 为 32 字节随机秘密，仅在电脑内存保存摘要，两分钟过期；它只能创建待确认请求，不能直接取得控制权。

手机生成独立随机请求秘密，通过 `X-DSH-Pairing-Secret` 发送；请求携带 ticket，在电脑确认后经 `pair/claim` 领取手机凭证。临时连接信息不进入手机持久配置。二维码/连接信息不包含电脑注册凭证和手机长期令牌。多个手机分别配对、分别撤销。

新插件 `device/info` 返回随机 `computerId` 和显示名称，不再采集硬件 MAC/机型；保留空 `mac` 和名称形式的 `model` 字段兼容旧客户端。新手机在持有有效设备凭证后记录和校验电脑身份；旧记录升级时清空 MAC 字段。中转不再支持旧手动配置、六位配对码或无 ticket 的配对请求。旧手动配置停止连接，须重新启用自动注册，手机删除旧中转记录后扫码；直连及有效的自动注册记录不受影响。
