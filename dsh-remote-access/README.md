# dsh-remote-access 3.1

桌面 DSH 的手机远程访问插件。支持独立设备配对、局域网直连、自建中转及 DeepSeek Harness `0.2.1-rc.5` 的 Typert 协议适配。服务器端只有后端，不需要管理网页。

设置页使用 DeepSeek Harness 的主题控件，中英文随宿主语言切换。

## 构建与打包

在整个仓库根目录运行：

```sh
npm ci --legacy-peer-deps
npm run build
npm test
npm pack -w dsh-remote-access
```

宿主提供 Cordis 和 DSH 服务，WebSocket 和本地二维码生成依赖随本包的 npm 依赖安装。`cordis.patch.yml` 注册宿主插件和客户端设置面板。安装需将生成的 tgz 加入目标 DSH profile。

## 使用流程

- 直连：手机填写可访问的电脑 DSH 地址。
- 中转：按 [后端说明](https://github.com/WILSONowo/dsh-remote-backend) 准备支持自动注册的服务器。插件“远程控制 → 服务器中转”填写 HTTPS 地址和电脑名称，开启中转开关并保存，等待连接成功。
- 点击“添加手机”，手机首页扫码或粘贴临时连接信息，然后在电脑确认。二维码两分钟有效、一次使用，重新生成会使旧二维码失效；每台手机单独配对，互不共用凭证。
- 主机 ID 和注册凭证自动创建并持久保存。电脑名称可以修改，随机安装身份不会随名称或网卡改变。不再接受手填主机 ID 或注册凭证；后端需更新至 2.0.0。旧手动中转须重新填写服务器地址并启用，手机删除旧记录后重新扫码。
- 直连支持电脑生成六位配对码或本机确认。服务器中转只接受有效临时连接信息，须经电脑确认；手机必须持有发起请求时生成的随机秘密才能领取凭证。
- 从旧版升级需要重新配对；旧共享通道令牌不再接受。

## 鉴权边界

每台手机使用独立随机令牌，桌面只保存令牌摘要。`pair/check` 只验证当前凭证，不能按设备 ID 领取令牌。直连配对码十分钟过期，五次错误锁定，使用后作废。撤销、重新配对和轮换令牌会关闭该设备已有 HTTP/WS/SSE 连接，其他设备不受影响。

生成配对码、批准请求、设备管理、公网域名白名单和中转配置只允许本机管理请求。检查 socket 来源、Host、Origin、跨站标记和代理头；中转内部请求有不可通过 HTTP 伪造的标记，不能凭 localhost 获得管理权限。原生桌面浏览器继续使用宿主鉴权。

中转是可信服务器，可看到终止 TLS 后的流量；当前不是端到端加密。配对的手机拥有远程控制权限，包括已有文件操作辅助功能，请只批准自己的设备。

## API 与持久化

配对：`/api/remote-access/pair/{request,status,respond,claim,check,list,revoke}` 和 `pair/code/{generate,current,verify}`。设备自己可调用 `token/{rotate,revoke,audit}`；管理员可撤销指定设备。

中转：本机 `GET/POST /api/remote-access/relay/config`。GET 不返回注册凭证。保存于 `$DSH_HOME/remote-access/relay.json`；设备摘要保存在 `devices-v2.json`，电脑身份和名称在 `identity.json`。不要复制这些私密文件给另一台电脑，克隆身份会造成连接冲突。配置写入失败不会返回成功凭证。

本机 `POST /api/remote-access/relay/pairing` 生成二维码和临时连接信息，不包含长期注册凭证。手机以一次性 ticket 调用 `pair/request`，电脑批准后再凭手机请求秘密领取独立凭证。仅允许电脑本机生成二维码，服务器不代替电脑批准手机。

业务适配由 `modern.ts` 实现，经宿主 Gateway 校验参数及创建调用作用域。每个手机事件连接有自己的 Peer，审批应答与设备和连接相关联，不能代答其他设备的请求。旧宿主不挂载此适配层。

`sync` 不执行离线任务，提交的离线操作明确返回 `offline_execution_not_supported`，避免把未执行的操作报告为成功。其他文件、设备信息及 MCP 辅助路由保留。

更多迁移信息见 [协议说明](https://github.com/WILSONowo/dsh-remote/blob/main/docs/protocol.md)，可选测试配置见 [测试说明](https://github.com/WILSONowo/dsh-remote/blob/main/docs/testing.md)。
