package com.dsh.mobile.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LanDiscoveryState(val searching: Boolean = false, val targets: List<LanPairingTarget> = emptyList(), val error: String? = null)

/** Bounded, foreground-only DNS-SD search. Legacy resolve calls are serialized for API 29–33. */
class LanDiscovery(context: Context) {
    private val manager = context.applicationContext.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(LanDiscoveryState())
    val state = mutableState.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    private var generation = 0
    private val queue = ArrayDeque<NsdServiceInfo>()
    private val found = mutableMapOf<String, LanPairingTarget>()
    private val present = mutableSetOf<String>()
    private var resolving = false
    private var deadline: Runnable? = null

    fun start() {
        stop(); found.clear(); present.clear()
        mutableState.value = LanDiscoveryState(searching = true)
        val run = generation
        fun post(block: () -> Unit) { handler.post { if (generation == run && listener != null) block() } }
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) = post {
                stop(); mutableState.value = mutableState.value.copy(error = "无法搜索局域网，请检查网络和系统权限，或使用 IP 直连。")
            }
            override fun onServiceFound(info: NsdServiceInfo) = post {
                if (info.serviceType.trimEnd('.') != "_dsh-pair._tcp" || present.size >= 64) return@post
                if (present.add(info.serviceName)) { queue.addLast(info); resolveNext(run) }
            }
            override fun onServiceLost(info: NsdServiceInfo) = post {
                present.remove(info.serviceName); found.remove(info.serviceName); updateTargets()
            }
        }
        listener = callback
        try {
            manager.discoverServices("_dsh-pair._tcp.", NsdManager.PROTOCOL_DNS_SD, callback)
            deadline = Runnable { if (generation == run) stop() }.also { handler.postDelayed(it, 15000) }
        } catch (_: Exception) {
            stop(); mutableState.value = mutableState.value.copy(error = "无法搜索局域网，请检查网络和系统权限，或使用 IP 直连。")
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext(run: Int) {
        if (resolving || queue.isEmpty() || generation != run || listener == null) return
        val info = queue.removeFirst()
        if (info.serviceName !in present) { resolveNext(run); return }
        resolving = true
        val callback = object : NsdManager.ResolveListener {
            private fun complete(resolved: NsdServiceInfo?) { handler.post {
                resolving = false
                // An API 29–33 resolve cannot be cancelled. Let it finish before resolving a new search.
                if (run != generation) { resolveNext(generation); return@post }
                if (resolved != null && info.serviceName in present) {
                    val addresses = if (Build.VERSION.SDK_INT >= 34) resolved.hostAddresses else listOfNotNull(resolved.host)
                    val attributes = resolved.attributes.mapValues { (_, value) -> value.toString(Charsets.UTF_8) }
                    lanPairingTarget(attributes, resolved.port, addresses)?.let { found[info.serviceName] = it }
                    updateTargets()
                }
                resolveNext(run)
            } }
            override fun onServiceResolved(resolved: NsdServiceInfo) = complete(resolved)
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) = complete(null)
        }
        try { manager.resolveService(info, callback) } catch (_: Exception) { resolving = false; resolveNext(run) }
    }

    private fun updateTargets() {
        val grouped = linkedMapOf<String, LanPairingTarget>()
        found.values.forEach { grouped[it.session] = mergeLanTargets(grouped[it.session], it) }
        mutableState.value = mutableState.value.copy(targets = grouped.values.sortedBy { it.name })
    }

    fun stop() {
        generation++; deadline?.let(handler::removeCallbacks); deadline = null
        val previous = listener; listener = null; queue.clear()
        if (previous != null) runCatching { manager.stopServiceDiscovery(previous) }
        mutableState.value = mutableState.value.copy(searching = false)
    }

}
