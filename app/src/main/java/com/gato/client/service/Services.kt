package com.gato.client.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.view.WindowManager
import android.graphics.PixelFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gato.client.game.AccountManager
import com.gato.client.game.GameSession
import com.gato.client.game.ModuleManager
import com.gato.client.game.module.visual.ESPModule
import com.gato.client.model.CaptureModeModel
import com.gato.client.overlay.OverlayManager
import com.gato.client.render.RenderOverlayView
import com.gato.relay.GatoRelay
import com.gato.relay.GatoRelaySession
import com.gato.relay.address.GatoAddress
import com.gato.relay.definition.Definitions
import com.gato.relay.listener.AutoCodecPacketListener
import com.gato.relay.listener.GamingPacketHandler
import com.gato.relay.listener.OfflineLoginPacketListener
import com.gato.relay.listener.OnlineLoginPacketListener
import com.gato.relay.util.advertisedVersionFor
import com.gato.relay.util.buildAdvertisement
import com.gato.relay.util.captureGamePacket
import com.gato.client.util.RelayLog
import com.gato.relay.util.SUPPORTED_VERSIONS
import java.io.File
import kotlin.concurrent.thread

@Suppress("MemberVisibilityCanBePrivate")
object Services {

    private val handler = Handler(Looper.getMainLooper())

    private var gatoRelay: GatoRelay? = null

    private var thread: Thread? = null

    private var renderView: RenderOverlayView? = null
    private var windowManager: WindowManager? = null

    var isActive by mutableStateOf(false)

    fun toggle(context: Context, captureModeModel: CaptureModeModel) {
        if (!isActive) {
            on(context, captureModeModel)
            return
        }

        off()
    }

    private fun on(context: Context, captureModeModel: CaptureModeModel) {
        if (this.thread != null) {
            return
        }

        val tokenCacheFile = File(context.cacheDir, "token_cache.json")

        isActive = true
        handler.post {
            OverlayManager.show(context)
        }

        setupOverlay(context)

        this.thread = thread(
            name = "GatoRelayThread",
            priority = Thread.MAX_PRIORITY
        ) {
            // Load module configurations
            runCatching {
                ModuleManager.loadConfig()
            }.exceptionOrNull()?.let {
                it.printStackTrace()
                context.toast("Load configuration error: ${it.message}")
            }

            runCatching {
                Definitions.loadBlockPalette()
            }.exceptionOrNull()?.let {
                it.printStackTrace()
                context.toast("Load block palette error: ${it.message}")
            }

            val selectedAccount = AccountManager.selectedAccount

            // advertise the INSTALLED client version: a pong with a newer protocol
            // than the connecting client makes it abort the join (InitialConnection-13)
            val chosenVersion = com.gato.client.util.McVersionResolver.resolved(context)
            println("GatoRelay advertising client version: $chosenVersion")

            // Start GatoRelay to capture game packets
            runCatching {
                gatoRelay = captureGamePacket(
                    advertisement = buildAdvertisement(chosenVersion),
                    remoteAddress = GatoAddress(
                        captureModeModel.serverHostName,
                        captureModeModel.serverPort
                    )
                ) {
                    initModules(this)

                    listeners.add(AutoCodecPacketListener(this))
                    listeners.add(
                        if (selectedAccount == null) OfflineLoginPacketListener(this) else OnlineLoginPacketListener(
                            this,
                            selectedAccount
                        )
                    )
                    listeners.add(GamingPacketHandler(this))
                }
            }.exceptionOrNull()?.let {
                it.printStackTrace()
                context.toast("Start GatoRelay error: ${it.stackTraceToString()}")
            }

        }
    }

    /**
     * Tees System.out into RelayLog so the relay's println diagnostics can be
     * read from the Settings page (and shared as text) without a PC.
     */
    private fun installRelayLogTee() {
        if (System.out.javaClass.name.contains("RelayLogTee")) return
        val original = System.out
        System.setOut(object : java.io.PrintStream(original, true, Charsets.UTF_8) {
            override fun println(x: String?) {
                super.println(x)
                x?.let { RelayLog.log(it) }
            }

            override fun println(x: Any?) {
                super.println(x)
                RelayLog.log(x.toString())
            }
        })
        RelayLog.log("=== relay log tee installed ===")
    }

    private fun off() {
        thread(name = "GatoRelayThread") {
            ModuleManager.saveConfig()
            handler.post {
                OverlayManager.dismiss()
            }
            removeOverlay()
            isActive = false
            gatoRelay?.disconnect()
            thread?.interrupt()
            thread = null
        }
    }

    private fun Context.toast(message: String) {
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun initModules(gatoRelaySession: GatoRelaySession) {
        val session = GameSession(gatoRelaySession)
        gatoRelaySession.listeners.add(session)

        for (module in ModuleManager.modules) {
            module.session = session
        }
        Log.e("Services", "Init session")
    }

    private fun setupOverlay(context: Context) {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha = 0.8f
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                setFitInsetsTypes(0)
                setFitInsetsSides(0)
            }
        }

        renderView = RenderOverlayView(context)
        ESPModule.setRenderView(renderView!!)

        handler.post {
            try {
                windowManager?.addView(renderView, params)
            } catch (e: Exception) {
                e.printStackTrace()
                context.toast("Failed to add overlay view: ${e.message}")
            }
        }
    }

    private fun removeOverlay() {
        renderView?.let { view ->
            windowManager?.removeView(view)
            renderView = null
        }
    }

}