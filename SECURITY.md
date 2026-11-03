# 安全说明（Security）

## 报告漏洞

本仓库是**个人开发的社区第三方客户端**（远程控制 DSH 的 App）。安全相关漏洞请**私有**报告，勿公开披露细节：

- **首选**：GitHub 私有安全通告（Security → Advisories → New draft security advisory），或对该仓库开一个标记为敏感、不公开细节的 Issue。
- 若需邮件：请在 GitHub 资料中通过私有通告交流，避免把敏感细节写进公共渠道。

请包含：影响版本、复现步骤、预期行为 vs 实际行为、缓解建议。

## 本项目重点关注的安全面

- **App 端**：凭证加密存储（AndroidKeyStore）、持久电脑身份校验、高危操作二次确认。
