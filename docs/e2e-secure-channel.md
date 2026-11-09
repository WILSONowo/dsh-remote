# 端到端安全通道（Secure Channel v1）

适用版本：App / 插件 4.0.0，后端 3.0.0。本文档是三个仓库共同遵守的规范；改动其中任何一项都需要同时更新三端和本文件。

## 1. 目标与威胁模型

| 角色 | 之前（≤ 3.x） | 之后（4.0） |
| --- | --- | --- |
| 中转服务器 | 终止 TLS，能读取请求、响应、设备长期令牌和配对秘密，可冒充手机 | 只转发不透明字节；看不到令牌、路径和内容 |
| 局域网 / 公共 Wi-Fi 上的旁观者 | 直连默认为明文 HTTP，可嗅探令牌 | 只能看到加密流量 |
| 主动中间人（局域网） | 可篡改 | 扫码配对：无法篡改；手填地址或 mDNS：首次使用信任（TOFU），可通过安全码人工比对 |

目标：手机与电脑插件之间建立一条 **固定电脑公钥的 TLS 1.3 通道**。所有 `/api` 请求（RPC、SSE、WebSocket、文件、配对）都只在这条通道里传输。中转和直连共用同一套机制。

不在范围内（残余风险）：
- 中转服务器仍能看到元数据：主机 ID、来源 IP、连接时间、流量大小和时序。
- 电脑本身被攻破，或已配对的手机被攻破。
- 拒绝服务：服务器可以随时断开或丢弃流量，但不能伪造内容。

## 2. 电脑密钥与指纹

- 插件第一次启动时生成 ECDSA P-256 密钥对，保存在 `$DSH_HOME/remote-access/secure-channel.json`（权限 0600），同时自签一张 X.509 v3 证书（CN=`DSH Remote`，有效期到 9999-12-31）。证书只是用来携带公钥，**手机不检查证书链、主机名和有效期**，只校验公钥指纹。
- 指纹 `pin = SHA-256(SubjectPublicKeyInfo DER)`，共 32 字节，以 64 位小写十六进制保存。
- 安全码：`pin` 前 12 个十六进制字符，大写，按 4 位一组显示，例如 `3FA9-0C1D-77B2`。电脑的配对页面和手机的配对对话框都会显示它，用户可以人工比对。
- 密钥与安装身份 `identity.json` 一一对应，不可复制到其他电脑。删除这个文件会生成新密钥，所有手机都必须重新配对。

## 3. 传输

所有 4.0 连接的形式都是：**WebSocket（二进制帧）承载原始 TLS 记录，TLS 里面跑普通 HTTP/1.1**。

```
手机 OkHttp(HTTP/1.1) ─ TLS 1.3（固定 pin）─ WebSocket 二进制帧 ─┬─ 直连：DSH 端口 /api/remote-access/secure
                                                                  └─ 中转：wss://服务器/relay/v2/connect/<hostId>
                                                                            └─ 隧道 v1 通道（字节透传）
电脑插件：通道字节 → tls.TLSSocket(服务端, 电脑密钥) → 内部网关（打上 SECURE 标记）→ /api 路由
```

- WebSocket 子协议：`dsh-e2e.1`。每个 WebSocket 连接对应一个 TLS 会话和一条 HTTP/1.1 keep-alive 连接；OkHttp 连接池会复用它，SSE 和事件流 WebSocket 各占一条。
- 单个二进制帧最大 32 KiB（与隧道 `CHUNK` 一致）。出现文本帧时直接关闭连接。
- TLS：插件端 `minVersion: 'TLSv1.3'`，ALPN 固定为 `http/1.1`。
- 中转：后端在 `/relay/v2/connect/<hostId>` 完成 WebSocket 握手后，打开一条隧道通道，双向透传字节，不解析内容。电脑与服务器之间的隧道 v1 协议**保持不变**。
- 直连：插件在宿主 DSH 端口注册升级路由 `/api/remote-access/secure`。这个端点本身不需要令牌，鉴权在 TLS 通道里面完成；它受“允许连接”开关和并发上限（64）约束。

## 4. 信任建立

| 入口 | pin 来源 | 强度 |
| --- | --- | --- |
| 服务器连接二维码 / 粘贴连接信息（v3） | 二维码内的 32 字节 pin | 强（二维码由电脑本地显示） |
| 直连地址二维码（`dshremote://direct#1.…`） | 二维码内的 pin | 强 |
| 手填地址、旧地址二维码、mDNS 发现 | 首次连接时记录（TOFU） | 首次连接可能被局域网中间人冒充；显示安全码供比对 |

- pin 记录后保存在连接记录中（`HostProfile.serverPin`），之后每次连接都严格校验。不匹配时连接失败，提示“电脑身份已改变”；**不会自动接受新 pin**，用户需要删除记录后重新配对。
- mDNS TXT 增加 `pin` 字段，作为首次信任的初始值（仍属于 TOFU 级别）。

### 4.1 服务器连接信息 v3

`dshremote://pair#3.<base64url>`，字段均为大端序：

| 偏移 | 长度 | 字段 |
| ---: | ---: | --- |
| 0 | 8 | 有效期（Unix 毫秒） |
| 8 | 16 | 主机 ID |
| 24 | 16 | 电脑 UUID |
| 40 | 32 | ticket |
| 72 | 32 | pin |
| 104 | 2+n | 服务器地址（UTF-8，n ≤ 2048） |
| … | 2+n | 电脑名称（UTF-8，n ≤ 256，≤ 64 字符） |

不接受尾随数据。v1（JSON）和 v2 不含 pin，4.0 App 会拒绝并提示“请更新电脑插件”。

### 4.2 直连地址二维码 v1

`dshremote://direct#1.<base64url>`：32 字节 pin，随后是地址（2+n，n ≤ 2048，必须是 `http`/`https` 基础地址）和电脑名称（2+n，n ≤ 256）。扫描后仍需输入六位配对码或在电脑上确认。

## 5. 插件端强制

- 新增内部标记 `SECURE_CHANNEL`，只有安全通道的内部网关会设置它，HTTP 头无法伪造。中转通道额外设置原有的 `RELAY_REQUEST`。
- 远程请求（非本机管理请求）访问 `/api/*` 时，只有带 `SECURE_CHANNEL` 标记才会被授权。例外：
  - `/api/remote-access/secure`：安全通道入口；
  - `/api/remote-access/discovery/info`：局域网探测，不含秘密。
- 明文远程请求返回 `426 {"error":"secure_channel_required"}`。旧版 App 因此无法连接，需要升级。
- 宿主原生桌面浏览器的鉴权逻辑不变。

## 6. 后端变化（3.0.0）

- 新增 `GET /relay/v2/connect/<hostId>`（WebSocket，子协议 `dsh-e2e.1`）。
- **移除** `/h/<hostId>/api/...` 的 HTTP 和 WebSocket 转发，因为它会接触明文。
- 资源控制改为流级别：`maxRequests` / `maxRequestsPerHost` 表示并发通道数；`streamIdleTimeout` 表示通道空闲时限；新增可选的 `streamBytesPerSecond`（每个通道每个方向，0 表示不限）。
- 以下旧配置项已失效：`maxFiles`、`maxFilesPerHost`、`fileBytesPerSecond`、`fileIdleTimeout`、`maxBodyBytes`。后端读到时会忽略并在启动时警告，不会拒绝启动。
- `/healthz` 返回 `{"ok":true,"protocol":2}`。

## 7. App 实现要点

- `HostProfile.transportUrl()`：外层传输地址（直连为 `ws(s)://…/api/remote-access/secure`，中转为 `wss://…/relay/v2/connect/<hostId>`）。
- `HostProfile.connectionUrl()`：内层合成地址 `https://<16位hex>.e2e.dsh.invalid`，16 位 hex 由传输地址的哈希得到。所有 RPC、SSE、WebSocket 和配对代码继续使用它，因此原有的比较逻辑仍能检测连接目标变化。
- `OkHttpClientFactory`：内层客户端的配置为：
  - 自定义 `SocketFactory`，每个 Socket 背后是一条外层 WebSocket；
  - DNS 返回占位地址，固定 `Proxy.NO_PROXY`；
  - `PinnedTrustManager`；
  - 只用 HTTP/1.1。

  外层客户端沿用连接记录里的代理、CA 和自签名设置。
- 诊断和断线探测改用外层传输地址。

## 8. 迁移

| 情况 | 处理 |
| --- | --- |
| 直连记录（无 pin） | 首次连接时 TOFU 记录 pin，令牌和配对保持有效 |
| 中转记录（无 pin） | 拒绝连接，提示在电脑重新生成二维码扫码。原因：经过不可信服务器做 TOFU 等于把信任交给服务器 |
| 旧 App + 新插件 | 被拒绝（426），需要升级 App |
| 新 App + 旧插件 | 无法建立安全通道，提示升级插件 |
| 新插件 + 旧后端（2.x） | 手机连接 `/relay/v2/connect` 得到 404，提示升级后端 |

兼容矩阵：App 4.0 ⇔ 插件 4.0 ⇔ 后端 3.0（中转）。

## 9. 发布完整性

- App Release 同时发布 `SHA256SUMS`；App 内更新先下载校验文件，SHA-256 不一致时拒绝安装。Android 本身还会校验覆盖安装时签名是否一致。
- 插件 tgz 在 CI 中同样生成 `SHA256SUMS`。

## 10. 测试

- 插件：证书生成、pin 计算、二维码 v3 编解码、明文拒绝、直连 / 中转安全通道的端到端测试（Node TLS 客户端，固定 pin）。
- 后端：`/relay/v2/connect` 字节透传、并发上限、空闲超时、旧 `/h/` 路径返回 404。
- App：二维码 v3 / 直连 v1 解码（共用 `pairing-compact.json` 向量），`PinnedTrustManager`，WebSocket Socket 适配器（回环 TLS）。
