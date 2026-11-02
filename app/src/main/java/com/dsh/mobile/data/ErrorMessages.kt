package com.dsh.mobile.data

object ErrorMessages {
    fun reason(code: ConnectionErrorCode): String = when (code) {
        ConnectionErrorCode.DNS_UNREACHABLE -> "域名无法解析"
        ConnectionErrorCode.PORT_UNREACHABLE -> "端口不可达（连接被拒绝/超时）"
        ConnectionErrorCode.TLS_CERT_FAILED -> "HTTPS 证书校验失败"
        ConnectionErrorCode.AUTH_FAILED -> "设备令牌或访问凭证无效（401/403）"
        ConnectionErrorCode.VERSION_MISMATCH -> "手机与电脑上的 DeepSeek Harness 版本不兼容"
        ConnectionErrorCode.PROXY_FAILED -> "代理不可达"
        ConnectionErrorCode.PROTOCOL_ERROR -> "服务响应异常"
        ConnectionErrorCode.UNKNOWN -> "未知错误"
    }

    fun advice(code: ConnectionErrorCode): String = when (code) {
        ConnectionErrorCode.DNS_UNREACHABLE -> "检查服务器地址中的域名；直连局域网电脑时也可填写其 IP 地址"
        ConnectionErrorCode.PORT_UNREACHABLE -> "直连：确认电脑上的 DeepSeek Harness 已启动，地址和端口正确；服务器中转：确认中转服务运行，手机可访问其地址和端口"
        ConnectionErrorCode.TLS_CERT_FAILED -> "检查证书有效期及域名；使用自签名证书时，请在主机配置中导入 CA 证书。服务器中转必须校验证书，不能跳过"
        ConnectionErrorCode.AUTH_FAILED -> "确认连接的是目标电脑，使用其配对码重新配对；若配置了额外的网关鉴权，请检查访问凭证。中转注册令牌仅填写在电脑插件中"
        ConnectionErrorCode.VERSION_MISMATCH -> "检查手机应用、电脑上的 DeepSeek Harness 与远程控制插件是否为兼容版本"
        ConnectionErrorCode.PROXY_FAILED -> "检查代理地址/端口/账号，或关闭该主机的代理"
        ConnectionErrorCode.PROTOCOL_ERROR -> "直连地址应指向电脑的 DeepSeek Harness 服务；服务器中转请检查电脑插件的连接状态，或重新扫描电脑生成的二维码（电脑离线时会返回 503）。仍失败可导出日志排查"
        ConnectionErrorCode.UNKNOWN -> "导出日志排查"
    }
}
