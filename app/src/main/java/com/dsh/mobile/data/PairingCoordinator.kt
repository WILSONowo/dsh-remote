package com.dsh.mobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Coordinates per-phone approval. Scan tickets are kept only for the current attempt. */
class PairingCoordinator(
    private val connection: DshConnection,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events
    private val _awaitingDecision = MutableStateFlow<String?>(null)
    val awaitingDecision: StateFlow<String?> = _awaitingDecision
    private val _waitingForApproval = MutableStateFlow<String?>(null)
    val waitingForApproval: StateFlow<String?> = _waitingForApproval
    private var handshakingFor: String? = null
    private var handshakeJob: Job? = null
    private var scan: Pair<String, PairingLink>? = null

    fun prepareScan(profile: HostProfile, link: PairingLink) {
        handshakeJob?.cancel()
        handshakingFor = null
        _waitingForApproval.value = null
        scan = if (profile.paired) null else profile.id to link
        _awaitingDecision.value = null
    }

    private fun current(profile: HostProfile): Boolean = connection.currentProfile()?.let {
        it.id == profile.id && it.connectionUrl() == profile.connectionUrl() && it.channelToken == profile.channelToken
    } == true

    fun start() {
        scope.launch(Dispatchers.Main.immediate) {
            connection.state.collect { state ->
                val profile = connection.currentProfile()
                if (profile == null) {
                    handshakeJob?.cancel()
                    _awaitingDecision.value = null
                    return@collect
                }
                if (handshakingFor != null && handshakingFor != profile.id) {
                    handshakeJob?.cancel()
                    handshakingFor = null
                }
                if (scan?.first != profile.id) scan = null
                if (state is DshConnection.State.Connected) {
                    if (profile.paired) refreshPaired(profile)
                    else if (handshakingFor != profile.id) {
                        val scanned = scan?.second
                        scan = null
                        if (scanned != null && scanned.expiresAt > System.currentTimeMillis()) {
                            scope.launch(Dispatchers.Main.immediate) { chooseLegacyHandshake(scanned.ticket) }
                        } else {
                            if (scanned != null) _events.tryEmit("二维码已过期，请在电脑重新生成")
                            _awaitingDecision.value = profile.id
                        }
                    }
                } else if (state is DshConnection.State.Error && state.code == ConnectionErrorCode.AUTH_FAILED && profile.paired) {
                    refreshPaired(profile)
                }
            }
        }
    }

    data class PairCodeResult(val ok: Boolean, val message: String?)

    private suspend fun finishPair(profile: HostProfile, rpc: HttpPairingRpc, token: String): Boolean {
        if (!current(profile) || !Regex("[a-f0-9]{64}").matches(token)) return false
        OkHttpClientFactory.ChannelTokenRegistry.set(profile.id, token)
        val info = try { rpc.deviceInfo() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (!current(profile)) return false
        if (profile.computerId.isNotBlank() && info?.computerId != profile.computerId) {
            OkHttpClientFactory.ChannelTokenRegistry.set(profile.id, null)
            _events.tryEmit("电脑身份与连接信息不一致，已断开，请重新扫码")
            connection.disconnect()
            return false
        }
        val updated = profile.copy(
            paired = true, channelToken = token, deviceMac = "",
            computerId = info?.computerId?.takeIf { it.isNotBlank() } ?: profile.computerId,
            deviceModel = info?.name?.takeIf { it.isNotBlank() } ?: info?.model?.takeIf { it.isNotBlank() } ?: profile.deviceModel,
        )
        settingsStore.upsertProfile(updated)
        if (!current(profile)) return false
        _awaitingDecision.value = null
        connection.syncCurrentProfile(updated)
        _events.tryEmit("配对完成，已保存本手机的独立访问权限")
        return true
    }

    suspend fun pairWithCode(code: String): PairCodeResult {
        val profile = connection.currentProfile() ?: return PairCodeResult(false, "没有活跃连接")
        if (profile.connectionMode == "relay") return PairCodeResult(false, "中转连接请扫码或粘贴电脑生成的临时连接信息")
        val rpc = HttpPairingRpc(connection, OkHttpClientFactory.build(profile).first)
        val result = try {
            rpc.codeVerify(code.trim(), settingsStore.ensureDeviceId(), settingsStore.deviceName.first().ifBlank { android.os.Build.MODEL })
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            return PairCodeResult(false, "配对请求失败，请检查电脑和服务器连接后重试")
        }
        if (!current(profile)) return PairCodeResult(false, "连接目标已改变，请重新配对")
        if (result.ok && result.token != null) return if (finishPair(profile, rpc, result.token)) PairCodeResult(true, null)
            else PairCodeResult(false, "未能确认电脑身份，请重新配对")
        return PairCodeResult(false, when (result.error) {
            "no_active_code" -> "请在电脑的 DeepSeek Harness「设置 → 远程控制」生成配对码"
            "code_locked" -> "错误次数过多，请在电脑重新生成配对码"
            "code_mismatch" -> "配对码不正确" + (result.attemptsLeft?.let { "（还剩 $it 次机会）" } ?: "")
            "plugin_old" -> "配对接口不可用，请检查电脑插件版本和服务器连接状态"
            else -> "配对失败，请重新生成配对码"
        })
    }

    /** Both QR and manual approval use an independent secret to claim the credential. */
    suspend fun chooseLegacyHandshake(ticket: String? = null) {
        val profile = connection.currentProfile() ?: return
        if (profile.connectionMode == "relay" && ticket == null) {
            _events.tryEmit("请扫描电脑生成的新二维码，或粘贴临时连接信息")
            _awaitingDecision.value = profile.id
            return
        }
        if (handshakingFor != null) return
        handshakingFor = profile.id
        _waitingForApproval.value = profile.id
        val job = currentCoroutineContext().job
        handshakeJob = job
        _awaitingDecision.value = null
        val rpc = HttpPairingRpc(connection, OkHttpClientFactory.build(profile).first, ticket)
        _events.tryEmit("正在请求配对，请在电脑的 DeepSeek Harness「远程控制」中确认")
        try {
            val result = PairingManager(rpc, scope).ensurePaired(
                settingsStore.ensureDeviceId(), settingsStore.deviceName.first().ifBlank { android.os.Build.MODEL },
            )
            if (!current(profile)) return
            when (result) {
                PairingOutcome.PAIRED, PairingOutcome.APPROVED -> {
                    val token = if (result == PairingOutcome.PAIRED) profile.channelToken else rpc.claim()
                    if (token.isNullOrBlank() || !finishPair(profile, rpc, token)) {
                        _events.tryEmit("未能领取本手机的访问凭证，请重新配对")
                        if (current(profile)) _awaitingDecision.value = profile.id
                    }
                }
                PairingOutcome.SKIPPED -> {
                    _events.tryEmit(if (profile.connectionMode == "relay") "电脑插件不支持扫码配对，请更新插件" else "电脑插件不支持此配对方式，请更新插件或使用配对码")
                    _awaitingDecision.value = profile.id
                }
                PairingOutcome.DENIED, PairingOutcome.TIMEOUT -> {
                    _events.tryEmit(if (result == PairingOutcome.DENIED) "电脑拒绝了本次配对，已断开" else "等待电脑确认超时，请重新配对")
                    connection.disconnect()
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (current(profile)) {
                _events.tryEmit(if (ticket != null) "扫码配对失败：连接信息可能已使用或过期，请在电脑重新生成" else "配对请求失败，请检查连接后重试")
                _awaitingDecision.value = profile.id
            }
        } finally { if (handshakeJob === job) {
            handshakingFor = null; handshakeJob = null; _waitingForApproval.value = null
        } }
    }

    private suspend fun refreshPaired(profile: HostProfile) {
        val rpc = HttpPairingRpc(connection, OkHttpClientFactory.build(profile).first)
        val check = try { rpc.check(settingsStore.ensureDeviceId()) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { return }
        if (!current(profile)) return
        if (!check.paired) {
            val revoked = profile.copy(paired = false, channelToken = "")
            OkHttpClientFactory.ChannelTokenRegistry.set(profile.id, null)
            settingsStore.upsertProfile(revoked)
            connection.syncCurrentProfile(revoked)
            _awaitingDecision.value = profile.id
            return
        }
        val info = try { rpc.deviceInfo() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { return }
        if (info == null || !current(profile)) return
        if (profile.computerId.isNotBlank() && info.computerId != profile.computerId) {
            _events.tryEmit("电脑身份校验失败，已断开，请重新确认连接目标")
            connection.disconnect()
            return
        }
        val updated = profile.copy(
            computerId = info.computerId.ifBlank { profile.computerId }, deviceMac = "",
            deviceModel = info.name.ifBlank { info.model.ifBlank { profile.deviceModel } },
        )
        if (updated != profile) {
            settingsStore.upsertProfile(updated)
            if (current(profile)) connection.syncCurrentProfile(updated)
        }
    }
}
