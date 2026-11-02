# dsh-remote

DeepSeek Harness 的非官方 Android 远程客户端，包含电脑插件 `dsh-remote-access`。
支持直连与服务器中转、会话和工作区管理、流式消息、文件传输、审批与问答、通知和主题。
电脑负责执行任务，手机负责操作和查看结果。

客户端可在「连接 / 会话 / 设置」之间切换，未连接时也能使用本地设置。
可保存多台电脑的记录，一次连接一台；返回连接页不会断开当前电脑，会话缓存和置顶记录按电脑区分。

服务器后端单独维护于 [dsh-remote-backend](https://github.com/WILSONowo/dsh-remote-backend)，配置方法见其 README。

## 连接

1. 在电脑的 DeepSeek Harness 中安装本仓库构建的插件。插件兼容 `0.2.1-rc.5` 的协议接口。
2. 直连：电脑点击「添加 → IP 直连」，设备扫码或填写可访问的电脑地址后配对；也可选择「配对码直连」发现局域网电脑并输入六位码。
3. 中转：服务器维护者按后端说明一次性启用自动注册。电脑「远程控制 → 服务器」填写 HTTPS 地址，保存服务器设置，身份和注册凭证由插件自动保存。
4. 电脑开启「允许连接」，点击「添加 → 服务器连接」，手机首页扫码或粘贴临时连接信息，随后在电脑确认。每台手机分别配对。六位配对码和不扫码的本机确认仅用于直连。

新增电脑无需逐台修改服务器配置或重启服务；多台手机可分别控制同一台电脑。名称只用于显示，随机身份持久保存，改名不会改变身份。

中转仅支持自动注册，需搭配后端 2.0.0 或以上。旧版手动中转配置已停用：维护者先按后端说明迁移服务器，电脑重新填写服务器地址并启用，手机删除旧中转记录后重新扫码。
升级旧版共享令牌方案后需要重新配对。每台手机使用独立凭证，撤销一台设备不影响其他设备。
服务器中转需校验 HTTPS 证书；自签名证书需分别配置 CA。中转服务器可读取转发内容，使用可信服务器。
电脑必须保持在线；请求中断后不会自动重放任务、审批或文件写入。

本版 App 与插件均为 3.2.0。服务器配对二维码使用紧凑编码，需搭配本版 App；已有配对记录保持有效。

安装包与应用更新来源为 [本仓库 Releases](https://github.com/WILSONowo/dsh-remote/releases)。
不同签名的 APK 不能直接覆盖安装；发布者应妥善保存并持续使用自己的签名密钥。

局域网配对可使用「配对码直连」：电脑打开配对页面，设备自动搜索电脑并输入六位码，无需填写 IP；发现受网络限制时仍可使用 IP 地址、域名或扫码连接。

## 构建和测试

插件要求 Node.js 22 或以上：

```sh
npm ci --legacy-peer-deps
npm run build
npm test
npm pack -w dsh-remote-access
```

Android 要求 JDK 17、Android SDK Platform 36；使用仓库提供的 Gradle Wrapper：

```sh
./gradlew testDebugUnitTest assembleDebug
```

Windows 使用 `gradlew.bat`。SDK 路径由本机 `local.properties` 的 `sdk.dir` 配置，不提交该文件。
发布签名可在同一文件配置 `release.storeFile`、`release.storePassword`、`release.keyAlias`、`release.keyPassword`。
不要提交密钥库或凭据。Debug APK 在 `app/build/outputs/apk/debug/`。

测试使用合成数据、临时目录和本机回环端口，不自动访问实际部署、用户会话或付费模型。
SDK 和跨仓库协议测试需要显式配置，见 [测试说明](docs/testing.md)。

## 文档

- [插件配置与鉴权](dsh-remote-access/README.md)
- [协议与迁移](docs/protocol.md)
- [自定义主题](docs/theme-package-format.md)
- [隐私说明](docs/privacy-policy.md) · [安全说明](SECURITY.md) · [贡献指南](CONTRIBUTING.md)

## 署名与许可

当前维护者：[WILSONowo](https://github.com/WILSONowo)。
上游项目：[Hyna-hla/dsh-remote](https://github.com/Hyna-hla/dsh-remote)；上游作者：残星会·虚质空间分部 达妮娅同学（B站：最喜欢达妮娅了）、Yauntyours。
代码按 [MIT](LICENSE) 许可发布，保留 `Copyright (c) 2026 Aemeath`。

鲸鱼娘皮肤素材来自 [dsh-deep-whale](https://github.com/Small-tailqwq/dsh-deep-whale)，按 CC BY-NC-SA 4.0 使用，完整署名链见 [素材声明](docs/maid-atelier-NOTICE.md)。
蓝鲸配色参考 [dsh-blue-whale](https://github.com/starslittle/dsh-blue-whale)，夜之城主题参考 [dsh-theme-cyberpunk2077](https://github.com/nicepkg/dsh-theme-cyberpunk2077)，字体方案参考 [dsh-font](https://github.com/nicepkg/dsh-font)。
应用图标沿用 DeepSeek Harness 图形；这是第三方客户端，不代表官方授权或背书。代码和第三方素材的许可分别适用。
