package com.gato.client.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gato.client.game.AccountManager
import com.gato.relay.util.authorize
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode
import java.util.function.Consumer
import kotlin.concurrent.thread

val auth = "UCmvDWiR0BjlX"
val enSuffix = "cHBJekl6R1YzUQ=="
val deSuffix = String(Base64.decode(enSuffix, Base64.DEFAULT)).trim()
val authId = "$auth-$deSuffix"

@SuppressLint("SetJavaScriptEnabled")
class AuthWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs) {

    var callback: ((Throwable?) -> Unit)? = null

    init {
        CookieManager.getInstance()
            .removeAllCookies(null)

        settings.javaScriptEnabled = true
        webViewClient = AuthWebViewClient()
    }

    fun addAccount() {
        thread {
            runCatching {
                val authManager = authorize(
                    gameVersion = AccountManager.gameVersion,
                    cache = false,
                    msaDeviceCodeCallback = Consumer { deviceCode ->
                        post {
                            loadUrl(deviceCode.directVerificationUri)
                        }
                    }
                )
                val displayName = AccountManager.displayNameOf(authManager)
                val containedAccount =
                    AccountManager.accounts.find { AccountManager.displayNameOf(it) == displayName }
                if (containedAccount != null) {
                    AccountManager.removeAccount(containedAccount)
                }
                AccountManager.addAccount(authManager)

                if (containedAccount == AccountManager.selectedAccount) {
                    AccountManager.selectAccount(authManager)
                }
                callback?.invoke(null)
            }.exceptionOrNull()?.let {
                callback?.invoke(it)
            }
        }
    }

    inner class AuthWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return false
        }

    }

}
