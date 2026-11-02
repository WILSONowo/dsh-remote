# 测试

默认测试使用合成设备、随机临时凭证和隔离临时目录；网络测试只启动本机回环服务并使用随机端口。
不会探测局域网设备、读取实际安装的用户数据或调用模型。测试里的域名、路径和地址是合成输入，不是部署配置。

```sh
npm ci --legacy-peer-deps
npm run build
npm test
./gradlew testDebugUnitTest assembleDebug
```

## 可选 SDK 与后端联调

这些测试默认跳过，不自动查找本机安装。显式设置以下环境变量后运行 `npm test`：

- `DSH_TEST_RUNTIME`：独立准备的 DeepSeek Harness `0.2.1-rc.5` SDK 的 `node_modules/@deepseek-ai` 目录。只包含包，不包含用户配置、会话或凭据。
- `DSH_RELAY_BACKEND`：已运行 `npm ci` 和 `npm run build` 的 `dsh-remote-backend` 仓库绝对路径。
- `OPENSSL_BIN`：OpenSSL 可执行文件路径；不设置时使用 PATH 中的 `openssl`。TLS 测试现场生成一天有效的临时证书并在结束后清理。

SDK 测试使用实际协议类和合成业务服务；后端联调仅使用本机临时服务器。环境变量指向无效目录时测试失败。
发布前可同时启用两项，验证 HTTP、WebSocket、SSE、设备隔离、证书验证和断线重连。

实际部署或外部网络的测试必须单独显式配置目标和凭证，不能加入默认测试或提交真实数据。

## 插件设置页

中英文词条及语言切换测试随 `npm test` 运行；设置 `DSH_TEST_RUNTIME` 后会使用真实宿主的 LocaleRuntime。

使用同一个 SDK 路径可启动隔离的界面预览：

```sh
node dsh-remote-access/scripts/preview-settings.mjs
```

该预览要求 SDK 中包含 `dsh-web-frontend`、`dsh-client-locale` 和 `dsh-client-ui-theme`，目前适配 `0.2.1-rc.5`。它复用宿主控件和主题，仅在回环地址监听，接口使用合成数据，不加载真实电脑配置或连接服务器。
检查中英文切换、深浅色、窄窗口、保存、二维码、配对确认、撤销和域名增删；预览工具栏只属于测试环境，不进入插件包。
