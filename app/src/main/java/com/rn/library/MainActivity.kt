package com.rn.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.rn.library.ui.AppSettings
import com.rn.library.ui.LanguageState
import com.rn.library.ui.ProvideAppLanguage
import com.rn.library.ui.screens.AppTheme
import com.rn.library.ui.screens.LibraryScreen
import com.rn.library.ui.theme.MyLibraryTheme
import com.rn.library.update.GitHubRelease
import com.rn.library.update.GitHubUpdateChecker
import com.rn.library.update.UpdateCheckResult
import com.rn.library.update.UpdateNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private var updateAvailableRelease by mutableStateOf<GitHubRelease?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        setContent {
            val languageState = remember { LanguageState(applicationContext) }
            val currentLanguage = languageState.currentLanguage
            val systemDarkTheme = isSystemInDarkTheme()
            var currentTheme by remember {
                mutableStateOf(if (systemDarkTheme) AppTheme.DARK else AppTheme.LIGHT)
            }
            var dynamicColorsEnabled by remember {
                mutableStateOf(AppSettings.isDynamicColorsEnabled(this))
            }
            var themePalette by remember {
                mutableStateOf(AppSettings.getThemePalette(this))
            }
            var useCustomAccent by remember {
                mutableStateOf(AppSettings.isUseCustomAccent(this))
            }
            var customAccentArgb by remember {
                mutableStateOf(AppSettings.getCustomAccentArgb(this))
            }
            var useCustomStatsColor by remember {
                mutableStateOf(AppSettings.isUseCustomStatsColor(this))
            }
            var customStatsArgb by remember {
                mutableStateOf(AppSettings.getCustomStatsArgb(this))
            }
            var useCustomIconColor by remember {
                mutableStateOf(AppSettings.isUseCustomIconColor(this))
            }
            var customIconColorArgb by remember {
                mutableStateOf(AppSettings.getCustomIconColorArgb(this))
            }
            var useCustomTextColor by remember {
                mutableStateOf(AppSettings.isUseCustomTextColor(this))
            }
            var customTextColorArgb by remember {
                mutableStateOf(AppSettings.getCustomTextColorArgb(this))
            }
            var useCustomButtonColor by remember {
                mutableStateOf(AppSettings.isUseCustomButtonColor(this))
            }
            var customButtonColorArgb by remember {
                mutableStateOf(AppSettings.getCustomButtonColorArgb(this))
            }
            val view = LocalView.current

            ProvideAppLanguage(currentLanguage) {
                MyLibraryTheme(
                    darkTheme = currentTheme == AppTheme.DARK,
                    dynamicColor = dynamicColorsEnabled,
                    palette = themePalette,
                    useCustomAccent = useCustomAccent,
                    customAccentArgb = customAccentArgb,
                    useCustomStatsColor = useCustomStatsColor,
                    customStatsArgb = customStatsArgb,
                    useCustomIconColor = useCustomIconColor,
                    customIconColorArgb = customIconColorArgb,
                    useCustomTextColor = useCustomTextColor,
                    customTextColorArgb = customTextColorArgb,
                    useCustomButtonColor = useCustomButtonColor,
                    customButtonColorArgb = customButtonColorArgb
                ) {
                    DisposableEffect(currentTheme) {
                        if (!view.isInEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val controller = WindowCompat.getInsetsController(window, view)
                            val isDark = currentTheme == AppTheme.DARK
                            controller.isAppearanceLightNavigationBars = !isDark
                            controller.isAppearanceLightStatusBars = !isDark
                        }
                        onDispose { }
                    }

                    LibraryScreen(
                        modifier = Modifier.fillMaxSize(),
                        providedLanguageState = languageState,
                        currentTheme = currentTheme,
                        onThemeChange = { newTheme -> currentTheme = newTheme },
                        dynamicColorsEnabled = dynamicColorsEnabled,
                        onDynamicColorsEnabledChange = { enabled ->
                            dynamicColorsEnabled = enabled
                            AppSettings.setDynamicColorsEnabled(this, enabled)
                        },
                        themePalette = themePalette,
                        onThemePaletteChange = { palette ->
                            themePalette = palette
                            AppSettings.setThemePalette(this, palette)
                        },
                        useCustomAccent = useCustomAccent,
                        onUseCustomAccentChange = { enabled ->
                            useCustomAccent = enabled
                            AppSettings.setUseCustomAccent(this, enabled)
                        },
                        customAccentArgb = customAccentArgb,
                        onCustomAccentArgbChange = { argb ->
                            customAccentArgb = argb
                            AppSettings.setCustomAccentArgb(this, argb)
                        },
                        useCustomStatsColor = useCustomStatsColor,
                        onUseCustomStatsColorChange = { enabled ->
                            useCustomStatsColor = enabled
                            AppSettings.setUseCustomStatsColor(this, enabled)
                        },
                        customStatsArgb = customStatsArgb,
                        onCustomStatsArgbChange = { argb ->
                            customStatsArgb = argb
                            AppSettings.setCustomStatsArgb(this, argb)
                        },
                        useCustomIconColor = useCustomIconColor,
                        onUseCustomIconColorChange = { enabled ->
                            useCustomIconColor = enabled
                            AppSettings.setUseCustomIconColor(this, enabled)
                        },
                        customIconColorArgb = customIconColorArgb,
                        onCustomIconColorChange = { argb ->
                            customIconColorArgb = argb
                            AppSettings.setCustomIconColorArgb(this, argb)
                        },
                        useCustomTextColor = useCustomTextColor,
                        onUseCustomTextColorChange = { enabled ->
                            useCustomTextColor = enabled
                            AppSettings.setUseCustomTextColor(this, enabled)
                        },
                        customTextColorArgb = customTextColorArgb,
                        onCustomTextColorArgbChange = { argb ->
                            customTextColorArgb = argb
                            AppSettings.setCustomTextColorArgb(this, argb)
                        },
                        useCustomButtonColor = useCustomButtonColor,
                        onUseCustomButtonColorChange = { enabled ->
                            useCustomButtonColor = enabled
                            AppSettings.setUseCustomButtonColor(this, enabled)
                        },
                        customButtonColorArgb = customButtonColorArgb,
                        onCustomButtonColorArgbChange = { argb ->
                            customButtonColorArgb = argb
                            AppSettings.setCustomButtonColorArgb(this, argb)
                        }
                    )

                    updateAvailableRelease?.let { release ->
                        AlertDialog(
                            onDismissRequest = { updateAvailableRelease = null },
                            title = { Text(stringResource(R.string.update_available_title)) },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.update_available_message,
                                        GitHubUpdateChecker.currentVersionName(this@MainActivity),
                                        release.tag
                                    )
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val targetUrl = release.htmlUrl.ifBlank {
                                            "https://github.com/${GitHubUpdateChecker.OWNER}/${GitHubUpdateChecker.REPO}/releases/latest"
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            startActivity(intent)
                                        } catch (_: Exception) { }
                                        updateAvailableRelease = null
                                    }
                                ) {
                                    Text(stringResource(R.string.update_download))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateAvailableRelease = null }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
        checkUpdatesInBackground()
    }

    private fun checkUpdatesInBackground() {
        if (!GitHubUpdateChecker.shouldRunBackgroundCheck(this)) return
        lifecycleScope.launch {
            GitHubUpdateChecker.markBackgroundCheck(this@MainActivity)
            val result = withContext(Dispatchers.IO) {
                GitHubUpdateChecker.check(this@MainActivity)
            }
            if (result is UpdateCheckResult.Available) {
                UpdateNotifier.notifyIfNew(this@MainActivity, result)
                updateAvailableRelease = result.release
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreview() {
    MyLibraryTheme {
        LibraryScreen(
            currentTheme = AppTheme.DARK,
            onThemeChange = {}
        )
    }
}
