package com.dsh.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 连接成功后的配对协调器（v1.8.0 起配对码优先）：
 * Connected 且活跃 profile 未配对时，不再自动发起握手，而是把决策权交给 UI
 * （awaitingDecision 状态流）：UI 可走「配对码」（pairWithCode——PC 生成 6 位随机码、
 * 手机输入即完成配对并直接拿 token），或回退「等待 PC 确认框」（chooseLegacyHandshake）。
 * 结果事件供 UI 层 Toast/snackbar 展示。
 *
 * 设备记录（v1.6.0）：配对通过后经 device/info 抓取主机机型与 MAC 存入 HostProfile；
 * 重连（已配对）时校验 MAC 一致才放行——动态公网 IP 被回收再分配时不会误连他人主机。
 */
class PairingCoordinator(
    private val connection: DshConnection,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    /** 等待配对决策的活跃 profile id；null = 无待决策（UI 据此弹配对对话框） */
    private val _awaitingDecision = MutableStateFlow<String?>(null)
    val awaitingDecision: StateFlow<String?> = _awaitingDecision

    private var handshakingFor: String? = null

    fun start() {
        scope.launch {
            connection.state.collect { st ->
                val profile = connection.currentProfile() ?: run {
                    // 无活跃连接目标：清除待配对状态与握手标记
                    handshakingFor = null
                    _awaitingDecision.value = null
                    return@collect
                }
                if (st is DshConnection.State.Error && st.code == ConnectionErrorCode.AUTH_FAILED && profile.paired) {
                    refreshPaired(profile)
                } else if (st is DshConnection.State.Connected) {
                    if (profile.paired) {
                        refreshPaired(profile)
                    } else if (handshakingFor != profile.id) {
                        // 未配对：等待 UI 决策（配对码优先）。不再因为连接抖动清掉，
                        // 保证配对码输入框能稳定停留、真正可输入（修复「闪一下就消失/无法输码」）。
                        handshakingFor = null
                        _awaitingDecision.value = profile.id
                    }
                } else {
                    // 连接抖动（Connecting/Error/Disconnected）：只复位握手协程标记，
                    // 保留 _awaitingDecision——配对框继续可用，配对成功 / 显式断开时再清除。
                    handshakingFor = null
                }
            }
        }
    }

    /** 配对码结果：ok=true 已配对；否则 message 为可展示的错误说明 */
    data class PairCodeResult(val ok: Boolean, val message: String?)

    /** UI 选择「使用配对码配对」：verify 通过即配对 + 直接拿 token + 记录设备 */
    suspend fun pairWithCode(code: String): PairCodeResult {
        val profile = connection.currentProfile() ?: return PairCodeResult(false, "没有活跃连接")
        val rpc = HttpPairingRpc(connection, OkHttpClientFactory.build(profile).first)
        val res = runCatching {
            rpc.codeVerify(
                code.trim(),
                settingsStore.ensureDeviceId(),
                settingsStore.deviceName.first().ifBlank { android.os.Build.MODEL },
            )
        }.getOrNull() ?: return PairCodeResult(false, "配对请求失败（网络或服务器错误），请重试")
        if (connection.currentProfile()?.id != profile.id) return PairCodeResult(false, "连接目标已改变，请重新配对")
        if (res.ok && res.token?.matches(Regex("[a-f0-9]{64}")) == true) {
            res.token?.takeIf { it.isNotBlank() }?.let { tok ->
                OkHttpClientFactory.ChannelTokenRegistry.set(profile.id, tok)
            }
            // token 已热生效，device/info 可通过鉴权
            val info = runCatching { rpc.deviceInfo() }.getOrNull()
            val updated = profile.copy(
                paired = true,
                channelToken = res.token ?: profile.channelToken,
                deviceModel = info?.model?.takeIf { it.isNotBlank() } ?: profile.deviceModel,
                deviceMac = info?.mac?.takeIf { it.isNotBlank() } ?: profile.deviceMac,
            )
            settingsStore.upsertProfile(updated)
            connection.syncCurrentProfile(updated)
            _awaitingDecision.value = null
            _events.tryEmit(
                if (info?.model?.isNotBlank() == true) "配对完成，已记录设备：${info.model}"
                else "配对完成，已保存本手机的设备令牌"
            )
            return PairCodeResult(true, null)
        }
        return PairCodeResult(false, when (res.error) {
            "no_active_code" -> "请打开电脑的 DeepSeek Harness「设置 → 远程控制」，查看或重新生成配对码"
            "code_locked" -> "配对码已锁定（错误次数过多），请在电脑的 DeepSeek Harness「设置 → 远程控制」中重新生成"
            "code_mismatch" -> "配对码不正确" + (res.attemptsLeft?.let { "（还剩 $it 次机会）" } ?: "")
            "plugin_old" -> "配对接口不可用：直连请检查电脑插件是否安装；服务器中转请检查服务器地址、主机 ID 和电脑插件状态"
            else -> "配对失败" + (res.error?.let { "：$it" } ?: "")
        })
    }

    /** UI 选择「等待 PC 确认」（旧式握手：pair/request + PC 弹窗批准） */
    suspend fun chooseLegacyHandshake() {
        val profile = connection.currentProfile() ?: return
        if (handshakingFor == profile.id) return
        handshakingFor = profile.id
        val rpc = HttpPairingRpc(connection, OkHttpClientFactory.build(profile).first)
        val result = runCatching {
            PairingManager(rpc, scope)
                .ensurePaired(
                    settingsStore.ensureDeviceId(),
                    settingsStore.deviceName.first().ifBlank { android.os.Build.MODEL },
                )
        }.getOrElse { null }
        when (result) {
            null -> {
                _awaitingDecision.value = profile.id
                _events.tryEmit("配对请求失败（网络或服务器错误），下次连接时重试")
            }
            PairingOutcome.PAIRED, PairingOutcome.APPROVED -> {
                // 配对通过后立即回查拿通道 token（Bearer 鉴权凭证），注册表热生效
                val tok = if (result == PairingOutcome.PAIRED) profile.channelToken
                    else runCatching { rpc.claim() }.getOrNull()
                if (tok.isNullOrBlank() || connection.currentProfile()?.id != profile.id) {
                    _events.tryEmit("未能领取设备凭证，请重新配对")
                    _awaitingDecision.value = profile.id
                    handshakingFor = null
                    return
                }
                if (!tok.isNullOrBlank()) {
                    OkHttpClientFactory.ChannelTokenRegistry.set(profile.id, tok)
                }
                // 首次连接：抓取主机机型与 MAC 写入设备记录（token 已热生效，device/info 可通过鉴权）
                val info = runCatching { rpc.deviceInfo() }.getOrNull()
                val updated = profile.copy(
                    paired = true,
                    channelToken = tok ?: profile.channelToken,
                    deviceModel = info?.model?.takeIf { it.isNotBlank() } ?: profile.deviceModel,
                    deviceMac = info?.mac?.takeIf { it.isNotBlank() } ?: profile.deviceMac,
                )
                settingsStore.upsertProfile(updated)
                connection.syncCurrentProfile(updated)
                _awaitingDecision.value = null
                when {
                    !tok.isNullOrBlank() -> _events.tryEmit(
                        if (info?.model?.isNotBlank() == true) "配对完成，已记录设备：${info.model}"
                        else "配对完成，已保存本手机的设备令牌"
                    )
                    else -> _events.tryEmit("配对完成")
                }
            }
            PairingOutcome.SKIPPED -> {
                // 安全：空配对码/插件未确认时绝不跳过验证、绝不签发令牌。
                // 保持待配对（awaitingDecision），引导用户用 PC 端自动生成的配对码完成配对。
                _events.tryEmit("请使用电脑上显示的 6 位配对码完成配对")
                _awaitingDecision.value = profile.id
            }
            PairingOutcome.DENIED -> {
                _awaitingDecision.value = null
                _events.tryEmit("电脑拒绝了本次配对，连接已断开")
                connection.disconnect()
            }
            PairingOutcome.TIMEOUT -> {
                _awaitingDecision.value = null
                _events.tryEmit("配对确认超时，请在电脑的 DeepSeek Harness「设置 → 远程控制」中确认请求；连接已断开")
                connection.disconnect()
            }
        }
        handshakingFor = null
    }

    /** 已配对 profile 的重连处理：回查配对状态、顺带刷新通道 token、设备（MAC）校验 */
    private suspend fun refreshPaired(profile: HostProfile) {
        val still = runCatching {
            HttpPairingRpc(connection, OkHttpClientFactory.build(profile).first)
                .check(settingsStore.ensureDeviceId())
        }.getOrNull()
        when {
            // 回查失败保守视为仍配对（网络错不打扰、不触发重新握手）
            still == null -> return
            !still.paired -> {
                // 已被 PC 撤销：重新等待配对决策（配对码优先）
                val revoked = profile.copy(paired = false, channelToken = "")
                OkHttpClientFactory.ChannelTokenRegistry.set(profile.id, null)
                settingsStore.upsertProfile(revoked)
                connection.syncCurrentProfile(revoked)
                _awaitingDecision.value = profile.id
            }
            else -> {
                // 重连设备校验：MAC 不匹配（IP 被回收到他人主机）→ 断开
                verifyDevice(profile)
            }
        }
    }

    /**
     * 重连设备校验（已配对 profile）：
     * - 旧插件（404）/网络错误 → null，降级不校验；
     * - 首见（旧版本升级前配对的记录无 MAC）→ 记录机型与 MAC；
     * - MAC 不一致 → 断开（该地址已被回收给别的设备）。
     */
    private suspend fun verifyDevice(profile: HostProfile) {
        val info = runCatching {
            HttpPairingRpc(connection, OkHttpClientFactory.build(profile).first).deviceInfo()
        }.getOrNull() ?: return
        if (info.mac.isBlank() && info.model.isBlank()) return
        if (profile.deviceMac.isBlank()) {
            settingsStore.upsertProfile(profile.copy(deviceModel = info.model, deviceMac = info.mac))
            return
        }
        if (info.mac.isNotBlank() && !info.mac.equals(profile.deviceMac, ignoreCase = true)) {
            _events.tryEmit(
                "设备校验失败：该地址当前不是「${profile.deviceModel.ifBlank { "已配对" }}」，已断开（公网 IP 可能被回收）",
            )
            connection.disconnect()
            return
        }
        if (info.model.isNotBlank() && info.model != profile.deviceModel) {
            settingsStore.upsertProfile(profile.copy(deviceModel = info.model))
        }
    }
}
