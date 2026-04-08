/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import androidx.datastore.preferences.core.edit
import com.metrolist.music.utils.dataStore
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var hasCompletedLogin by remember { mutableStateOf(false) }
    val pendingVisitorData = remember { AtomicReference<CompletableDeferred<String>?>(null) }
    val pendingDataSyncId = remember { AtomicReference<CompletableDeferred<String>?>(null) }

    var webView: WebView? = null

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (url?.startsWith("https://music.youtube.com") == true && !hasCompletedLogin) {
                            val visitorDataDeferred = CompletableDeferred<String>()
                            val dataSyncIdDeferred = CompletableDeferred<String>()
                            pendingVisitorData.set(visitorDataDeferred)
                            pendingDataSyncId.set(dataSyncIdDeferred)

                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                            innerTubeCookie = CookieManager.getInstance().getCookie(url)
                            hasCompletedLogin = true

                            coroutineScope.launch {
                                val resolvedVisitorData = withTimeoutOrNull(5000) { visitorDataDeferred.await() }
                                val resolvedDataSyncId = withTimeoutOrNull(5000) { dataSyncIdDeferred.await() }

                                if (resolvedVisitorData.isNullOrBlank() || resolvedDataSyncId.isNullOrBlank()) {
                                    hasCompletedLogin = false
                                    reportException(
                                        IllegalStateException(
                                            "Login: timed out waiting for visitorData/dataSyncId from WebView",
                                        ),
                                    )
                                    pendingVisitorData.set(null)
                                    pendingDataSyncId.set(null)
                                    return@launch
                                }

                                visitorData = resolvedVisitorData
                                dataSyncId = resolvedDataSyncId

                                // Persist critical auth preferences before restart so the next process sees a logged-in state
                                try {
                                    context.dataStore.edit { settings ->
                                        settings[VisitorDataKey] = resolvedVisitorData
                                        settings[DataSyncIdKey] = resolvedDataSyncId
                                        settings[InnerTubeCookieKey] = innerTubeCookie
                                    }
                                } catch (e: IOException) {
                                    hasCompletedLogin = false
                                    Timber.e(e, "Login: Failed to persist auth data to DataStore")
                                    reportException(e)
                                    pendingVisitorData.set(null)
                                    pendingDataSyncId.set(null)
                                    return@launch
                                } catch (e: Exception) {
                                    hasCompletedLogin = false
                                    Timber.e(e, "Login: Unexpected failure while persisting auth data")
                                    reportException(e)
                                    pendingVisitorData.set(null)
                                    pendingDataSyncId.set(null)
                                    return@launch
                                }

                                // Initialize YouTube object with new authentication data
                                YouTube.cookie = innerTubeCookie
                                YouTube.dataSyncId = resolvedDataSyncId
                                YouTube.visitorData = resolvedVisitorData

                                Timber.d("Login: YouTube object initialized, validating...")

                                YouTube.accountInfo().onSuccess {
                                    accountName = it.name
                                    accountEmail = it.email.orEmpty()
                                    accountChannelHandle = it.channelHandle.orEmpty()

                                    Timber.d("Login: Successfully logged in as ${it.name}, restarting app...")

                                    // Clean up WebView
                                    webView?.apply {
                                        stopLoading()
                                        clearHistory()
                                        clearCache(true)
                                        clearFormData()
                                    }

                                    // Restart app to apply login state throughout
                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    context.startActivity(intent)
                                    Runtime.getRuntime().exit(0)
                                }.onFailure {
                                    Timber.e(it, "Login: Authentication validation failed")
                                    hasCompletedLogin = false // Allow retry
                                    reportException(it)
                                }

                                pendingVisitorData.set(null)
                                pendingDataSyncId.set(null)
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRetrieveVisitorData(newVisitorData: String?) {
                        if (newVisitorData != null) {
                            pendingVisitorData.get()?.complete(newVisitorData)
                        }
                    }
                    @JavascriptInterface
                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                        if (newDataSyncId != null) {
                            val normalizedDataSyncId =
                                newDataSyncId
                                    .takeIf { !it.contains("||") }
                                    ?: newDataSyncId.takeIf { it.endsWith("||") }?.substringBefore("||")
                                    ?: newDataSyncId.substringAfter("||")
                            pendingDataSyncId.get()?.complete(normalizedDataSyncId)
                        }
                    }
                }, "Android")
                webView = this
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
            }
        }
    )

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
