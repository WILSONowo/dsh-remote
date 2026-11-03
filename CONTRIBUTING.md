# 贡献指南

欢迎向 [本仓库](https://github.com/WILSONowo/dsh-remote) 提交 Issue 或 PR。安全问题见 [SECURITY.md](SECURITY.md)。

- `app/`：Android 客户端；`docs/`：通用使用和协议文档。
- Android 运行 `./gradlew testDebugUnitTest assembleDebug`。
- 后端在 [dsh-remote-backend](https://github.com/WILSONowo/dsh-remote-backend) 独立维护。电脑插件在 [dsh-remote-access](https://github.com/WILSONowo/dsh-remote-access) 独立维护。
- 提交仅包含功能源码、必要配置、通用文档与可复现测试。不要提交个人路径、设备信息、真实部署配置、凭据、原始日志、截图或开发交接流水。
- 测试使用合成数据；访问真实部署或外部网络必须显式配置。保留必要产品入口、公钥、安全白名单及第三方署名。
- 提交前检查完整 diff、新增文件、提交信息、PR 内容及构建产物；提交身份推荐使用 GitHub noreply 邮箱。
