@file:Suppress("AssignedValueIsNeverRead")

package org.mlm.mages

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mages.shared.generated.resources.Res
import org.koin.compose.koinInject
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost
import org.mlm.mages.di.KoinApp
import org.mlm.mages.nav.DeepLinkAction
import org.mlm.mages.platform.MagesPaths
import org.mlm.mages.platform.Notifier
import org.mlm.mages.platform.SettingsProvider
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    val initialDeepLink =
        args.firstOrNull { it.startsWith("matrix:") || it.startsWith("http") }

    application {
        MagesPaths.init()

    val settingsRepo = remember { SettingsProvider.get() }
    val initialStartInTray = remember {
        runBlocking { settingsRepo.flow.first().startInTray }
    }

    var startInTray by remember { mutableStateOf(initialStartInTray) }
var showWindow by remember { mutableStateOf(!startInTray || initialDeepLink != null) }

    val deepLinkEmitter = remember { MutableSharedFlow<DeepLinkAction>(extraBufferCapacity = 8) }
    val deepLinks = remember { deepLinkEmitter.asSharedFlow() }

    val scope = rememberCoroutineScope()

    val windowState = rememberWindowState()

    var tray by remember { mutableStateOf<SystemTray?>(null) }
    var showItem by remember { mutableStateOf<MenuItem?>(null) }

    LaunchedEffect(Unit) {
        val computedTray = withContext(Dispatchers.IO) {
            SystemTray.DEBUG = false

            val osName = System.getProperty("os.name").lowercase()
            if (osName.contains("mac")) {
                SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing
            }

            val t = SystemTray.get()
            if (t == null) {
                println("SystemTray.get() returned null - no tray available on this platform/configuration.")
            }
            t
        }
        tray = computedTray
    }

    DisposableEffect(tray) {
        val t = tray ?: return@DisposableEffect onDispose { }

        val iconBytes = runBlocking { Res.readBytes("files/tray.png") }
        t.setImage(iconBytes.inputStream())

        val showMenuItem = MenuItem("Show").apply {
            setCallback {
                SwingUtilities.invokeLater { showWindow = true }
            }
        }
        showItem = showMenuItem
        t.menu.add(showMenuItem)

        t.menu.add(dorkbox.systemTray.Separator())

        val minimizeItem = MenuItem(
            if (startInTray) "✓ Minimize to tray on launch"
            else "Minimize to tray on launch"
        )

        minimizeItem.setCallback {
            SwingUtilities.invokeLater {
                startInTray = !startInTray
                minimizeItem.text =
                    if (startInTray) "✓ Minimize to tray on launch"
                    else "Minimize to tray on launch"
            }

            scope.launch {
                settingsRepo.update { it.copy(startInTray = startInTray) }
            }
        }

        t.menu.add(minimizeItem)
        t.menu.add(dorkbox.systemTray.Separator())

        t.menu.add(MenuItem("Quit").apply {
            setCallback {
                SwingUtilities.invokeLater {
                    t.shutdown()
                    exitApplication()
                }
            }
        })

        onDispose { t.shutdown() }
    }

    LaunchedEffect(tray) {
        if (tray == null) return@LaunchedEffect
        NotifierImpl.unreadRooms.collect { unread ->
            val status = if (unread > 0) "Mages ($unread unread)" else "Mages"
            tray?.setStatus(status)
            tray?.setTooltip(status)
            showItem?.text = if (unread > 0) "Show ($unread unread)" else "Show"
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val osName = System.getProperty("os.name").lowercase()
            if (osName.contains("linux")) {
                NotifierImpl.warmUp()
            } else {
                println("Skipping NotifierImpl warmup: D-Bus is not supported on $osName")
            }
        }
    }

    // Keep notification state when the window is hidden to the tray.
    LaunchedEffect(showWindow) {
        if (!showWindow) {
            Notifier.setWindowFocused(false)
        }
    }

    KoinApp(settingsRepo) {
        Window(
            onCloseRequest = {
                Notifier.setWindowFocused(false)
                showWindow = false
            },
            state = windowState,
            visible = showWindow,
            title = "Mages"
        ) {
            val window = this.window
            val matrixService: MatrixService = koinInject()

            DisposableEffect(window, matrixService) {
                val listener = object : WindowFocusListener {
                    var lastNudgeMs = 0L
                    override fun windowGainedFocus(e: WindowEvent?) {
                        Notifier.setWindowFocused(true)
                        val now = System.currentTimeMillis()
                        if (now - lastNudgeMs > 5000L) {
                            lastNudgeMs = now
                            scope.launch(Dispatchers.IO) {
                                runCatching<Unit> {
                                    matrixService.portOrNull?.enterForeground()
                                }
                            }
                        }
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        Notifier.setWindowFocused(false)
                    }
                }

                window.addWindowFocusListener(listener)
                Notifier.setWindowFocused(window.isFocused)

                onDispose {
                    window.removeWindowFocusListener(listener)
                    Notifier.setWindowFocused(false)
                }
            }

            ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
                DesktopAppContent(
                    deepLinks = deepLinks,
                    initialDeepLink = initialDeepLink
                )
            }
        }

        DesktopBackground(
            deepLinkEmitter = deepLinkEmitter,
            scope = scope,
            onShowWindow = { showWindow = true }
        )
    }
}
}
