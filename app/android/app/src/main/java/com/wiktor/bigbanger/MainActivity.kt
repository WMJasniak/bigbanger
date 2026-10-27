package com.wiktor.bigbanger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wiktor.bigbanger.ui.BigBangerApp
import com.wiktor.bigbanger.ui.UpdateDialog
import com.wiktor.bigbanger.ui.UpdatePhase
import com.wiktor.bigbanger.update.ReleaseInfo
import com.wiktor.bigbanger.update.UpdateSettings
import com.wiktor.bigbanger.update.Updater
import kotlinx.coroutines.launch

private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // twice a day at most

// A deliberate dark palette -- overriding only `primary` on the Material3 baseline
// left every other token at its default blue-violet tuning, which clashed with the
// orange accent and read as washed out/low-contrast throughout the app.
private val BigBangerColorScheme = darkColorScheme(
    primary = Color(0xFFE1551F),
    onPrimary = Color(0xFFFFFAF6),
    primaryContainer = Color(0xFF5A2512),
    onPrimaryContainer = Color(0xFFFFD9C7),
    secondary = Color(0xFFC9A227),
    onSecondary = Color(0xFF2B2100),
    background = Color(0xFF141312),
    onBackground = Color(0xFFF4EFE8),
    surface = Color(0xFF141312),
    onSurface = Color(0xFFF4EFE8),
    surfaceVariant = Color(0xFF231F1C),
    onSurfaceVariant = Color(0xFFB8AFA6),
    outline = Color(0xFF8A8178),
    outlineVariant = Color(0xFF3A3531),
)

class MainActivity : ComponentActivity() {
    private val updater by lazy { Updater(applicationContext) }
    private val updateSettings by lazy { UpdateSettings(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = BigBangerColorScheme) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    val viewModel: BigBangerViewModel = viewModel()
                    var availableRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
                    var updatePhase by remember { mutableStateOf<UpdatePhase>(UpdatePhase.Idle) }
                    var manualCheckStatus by remember { mutableStateOf<String?>(null) }

                    fun checkForUpdates(force: Boolean) {
                        if (force) manualCheckStatus = "Checking..."
                        lifecycleScope.launch {
                            val release = updater.checkLatest()
                            updateSettings.lastCheckMs = System.currentTimeMillis()
                            val isNewer = release != null && release.versionCode > BuildConfig.VERSION_CODE
                            if (isNewer && (force || release!!.versionCode != updateSettings.skippedVersionCode)) {
                                availableRelease = release
                                updatePhase = UpdatePhase.Idle
                                manualCheckStatus = null
                            } else if (force) {
                                manualCheckStatus = if (release == null) {
                                    "Couldn't check right now -- try again later."
                                } else {
                                    "You're on the latest version."
                                }
                            }
                        }
                    }

                    // Silent, rate-limited check on launch.
                    LaunchedEffect(Unit) {
                        if (System.currentTimeMillis() - updateSettings.lastCheckMs > CHECK_INTERVAL_MS) {
                            checkForUpdates(force = false)
                        }
                    }

                    BigBangerApp(
                        viewModel = viewModel,
                        updateStatus = manualCheckStatus,
                        onCheckUpdates = { checkForUpdates(force = true) },
                    )

                    availableRelease?.let { release ->
                        UpdateDialog(
                            release = release,
                            phase = updatePhase,
                            onUpdate = {
                                lifecycleScope.launch {
                                    try {
                                        val file = updater.download(release) { p ->
                                            updatePhase = UpdatePhase.Downloading(p)
                                        }
                                        availableRelease = null
                                        updatePhase = UpdatePhase.Idle
                                        updater.install(file)
                                    } catch (e: Exception) {
                                        updatePhase = UpdatePhase.Error(e.message ?: "unknown error")
                                    }
                                }
                            },
                            onSkip = {
                                updateSettings.skippedVersionCode = release.versionCode
                                availableRelease = null
                            },
                            onDismiss = { availableRelease = null },
                        )
                    }
                }
            }
        }
    }
}
