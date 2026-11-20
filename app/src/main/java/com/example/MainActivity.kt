package com.example

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var topProgressBar: LinearProgressIndicator
    private lateinit var offlineLayout: ConstraintLayout
    private lateinit var btnRetry: MaterialButton
    private lateinit var splashLayout: ConstraintLayout
    private lateinit var ivSplashLogo: ImageView
    private lateinit var creditContainer: View
    private lateinit var tvCreditTelegram: TextView
    private lateinit var fabPreviewSimulator: ExtendedFloatingActionButton

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isSplashDismissed = false
    private var isSimulatingOffline = false
    private var hasMainFrameError = false
    private var splashStartTimestamp = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val splashFailsafeRunnable = Runnable { fadeOutSplashIfNeeded() }

    private val pickLogoLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                ivSplashLogo.setImageURI(uri)
                Toast.makeText(this, R.string.preview_logo_updated, Toast.LENGTH_SHORT).show()
            }
        }

    private val webFileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileUploadCallback ?: return@registerForActivityResult
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            callback.onReceiveValue(results)
            fileUploadCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        bindViews()
        applyEdgeToEdgeInsets()
        configureSplashCredits()
        configureWebView()
        configurePullToRefresh()
        configureOfflineRetry()
        configureBackNavigation()
        configurePreviewSimulator()

        splashStartTimestamp = System.currentTimeMillis()
        mainHandler.postDelayed(splashFailsafeRunnable, SPLASH_FAILSAFE_TIMEOUT_MS)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            fadeOutSplashIfNeeded()
        } else {
            loadWebsiteOrShowOffline(TARGET_URL)
        }
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.rootContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webView = findViewById(R.id.webView)
        topProgressBar = findViewById(R.id.topProgressBar)
        offlineLayout = findViewById(R.id.offlineLayout)
        btnRetry = findViewById(R.id.btnRetry)
        splashLayout = findViewById(R.id.splashLayout)
        ivSplashLogo = findViewById(R.id.ivSplashLogo)
        creditContainer = findViewById(R.id.creditContainer)
        tvCreditTelegram = findViewById(R.id.tvCreditTelegram)
        fabPreviewSimulator = findViewById(R.id.fabPreviewSimulator)
    }

    private fun applyEdgeToEdgeInsets() {
        val baseCreditBottomMargin = (28 * resources.displayMetrics.density).toInt()
        val baseFabBottomMargin = (16 * resources.displayMetrics.density).toInt()
        val baseFabEndMargin = (16 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            swipeRefreshLayout.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )

            topProgressBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }

            offlineLayout.updatePadding(
                top = insets.top,
                bottom = insets.bottom
            )

            splashLayout.updatePadding(
                top = insets.top
            )

            creditContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseCreditBottomMargin + insets.bottom
            }

            fabPreviewSimulator.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseFabBottomMargin + insets.bottom
                rightMargin = baseFabEndMargin + insets.right
            }

            windowInsets
        }
    }

    private fun configureSplashCredits() {
        tvCreditTelegram.setOnClickListener {
            openExternalUrl(TELEGRAM_PROFILE_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (shouldOpenExternally(url)) {
                    openExternalUrl(url)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasMainFrameError = false
                if (isSplashDismissed) {
                    topProgressBar.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                topProgressBar.visibility = View.GONE

                if (!hasMainFrameError && !isSimulatingOffline) {
                    offlineLayout.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
                fadeOutSplashWithMinDuration()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val errorCode = error?.errorCode ?: 0
                    if (errorCode == ERROR_UNKNOWN) return
                    hasMainFrameError = true
                    swipeRefreshLayout.isRefreshing = false
                    topProgressBar.visibility = View.GONE
                    showOfflineScreen()
                    fadeOutSplashWithMinDuration()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (isSplashDismissed) {
                    topProgressBar.setProgressCompat(newProgress, true)
                    if (newProgress >= 100) {
                        topProgressBar.visibility = View.GONE
                    }
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: return false
                    webFileChooserLauncher.launch(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileUploadCallback = null
                    false
                }
            }
        }
    }

    private fun configurePullToRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            R.color.tasty_primary,
            R.color.telegram_blue,
            R.color.tasty_accent_green
        )
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0
        }
        swipeRefreshLayout.setOnRefreshListener {
            loadWebsiteOrShowOffline(webView.url ?: TARGET_URL)
        }
    }

    private fun configureOfflineRetry() {
        btnRetry.setOnClickListener {
            if (isSimulatingOffline) {
                isSimulatingOffline = false
            }
            loadWebsiteOrShowOffline(webView.url ?: TARGET_URL)
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack() && !isSimulatingOffline) {
                        offlineLayout.visibility = View.GONE
                        webView.visibility = View.VISIBLE
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun configurePreviewSimulator() {
        fabPreviewSimulator.visibility = if (ENABLE_PREVIEW_SIMULATOR) View.VISIBLE else View.GONE
        fabPreviewSimulator.setOnClickListener {
            showPreviewSimulatorSheet()
        }
    }

    private fun showPreviewSimulatorSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_preview_simulator, null)
        dialog.setContentView(sheetView)

        val btnUploadLogo = sheetView.findViewById<MaterialButton>(R.id.btnUploadLogo)
        val btnResetLogo = sheetView.findViewById<MaterialButton>(R.id.btnResetLogo)
        val btnReplaySplash = sheetView.findViewById<MaterialButton>(R.id.btnReplaySplash)
        val switchOffline = sheetView.findViewById<MaterialSwitch>(R.id.switchSimulateOffline)
        val btnTestTelegram = sheetView.findViewById<MaterialButton>(R.id.btnTestTelegram)
        val btnTestWhatsApp = sheetView.findViewById<MaterialButton>(R.id.btnTestWhatsApp)
        val btnTestEmail = sheetView.findViewById<MaterialButton>(R.id.btnTestEmail)
        val btnTestCall = sheetView.findViewById<MaterialButton>(R.id.btnTestCall)
        val btnHidePreviewPill = sheetView.findViewById<MaterialButton>(R.id.btnHidePreviewPill)

        switchOffline.isChecked = isSimulatingOffline

        btnUploadLogo.setOnClickListener {
            pickLogoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        btnResetLogo.setOnClickListener {
            ivSplashLogo.setImageResource(R.drawable.app_logo)
            Toast.makeText(this, R.string.preview_logo_reset, Toast.LENGTH_SHORT).show()
        }

        btnReplaySplash.setOnClickListener {
            dialog.dismiss()
            replaySplashPreview()
        }

        switchOffline.setOnCheckedChangeListener { _, isChecked ->
            isSimulatingOffline = isChecked
            if (isChecked) {
                showOfflineScreen()
            } else {
                loadWebsiteOrShowOffline(webView.url ?: TARGET_URL)
            }
        }

        btnTestTelegram.setOnClickListener { openExternalUrl(TELEGRAM_PROFILE_URL) }
        btnTestWhatsApp.setOnClickListener { openExternalUrl("https://wa.me/8801700000000") }
        btnTestEmail.setOnClickListener { openExternalUrl("mailto:support@tastyfx.pro") }
        btnTestCall.setOnClickListener { openExternalUrl("tel:+8801700000000") }

        btnHidePreviewPill.setOnClickListener {
            fabPreviewSimulator.visibility = View.GONE
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun replaySplashPreview() {
        mainHandler.removeCallbacks(splashFailsafeRunnable)
        isSplashDismissed = false
        splashStartTimestamp = System.currentTimeMillis()
        splashLayout.animate().cancel()
        splashLayout.alpha = 1f
        splashLayout.visibility = View.VISIBLE
        mainHandler.postDelayed({ fadeOutSplashIfNeeded() }, PREVIEW_SPLASH_DURATION_MS)
    }

    private fun loadWebsiteOrShowOffline(url: String) {
        if (isSimulatingOffline || !isNetworkAvailable(this)) {
            swipeRefreshLayout.isRefreshing = false
            showOfflineScreen()
            fadeOutSplashWithMinDuration()
            return
        }

        offlineLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        if (webView.url == url) {
            webView.reload()
        } else {
            webView.loadUrl(url)
        }
    }

    private fun showOfflineScreen() {
        webView.visibility = View.INVISIBLE
        offlineLayout.visibility = View.VISIBLE
    }

    private fun fadeOutSplashWithMinDuration() {
        if (isSplashDismissed) return
        val elapsed = System.currentTimeMillis() - splashStartTimestamp
        val remaining = (MIN_SPLASH_DISPLAY_MS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) {
            mainHandler.postDelayed({ fadeOutSplashIfNeeded() }, remaining)
        } else {
            fadeOutSplashIfNeeded()
        }
    }

    private fun fadeOutSplashIfNeeded() {
        if (isSplashDismissed) return
        isSplashDismissed = true
        mainHandler.removeCallbacks(splashFailsafeRunnable)
        splashLayout.animate()
            .alpha(0f)
            .setDuration(SPLASH_FADE_DURATION_MS)
            .withEndAction {
                splashLayout.visibility = View.GONE
            }
            .start()
    }

    private fun openExternalUrl(url: String) {
        try {
            val intent = when {
                url.startsWith("intent://", ignoreCase = true) -> {
                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME).also { parsed ->
                        val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
                        if (parsed.resolveActivity(packageManager) == null && !fallbackUrl.isNullOrBlank()) {
                            openExternalUrl(fallbackUrl)
                            return
                        }
                    }
                }
                url.startsWith("mailto:", ignoreCase = true) -> {
                    Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                }
                url.startsWith("tel:", ignoreCase = true) -> {
                    Intent(Intent.ACTION_DIAL, Uri.parse(url))
                }
                else -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                }
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.external_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val TARGET_URL = "https://tastyfx.pro/"
        const val TELEGRAM_PROFILE_URL = "https://t.me/shamimasraf99"

        // Set to false when exporting a clean production APK without the preview tool button
        private const val ENABLE_PREVIEW_SIMULATOR = true

        private const val MIN_SPLASH_DISPLAY_MS = 900L
        private const val SPLASH_FADE_DURATION_MS = 450L
        private const val SPLASH_FAILSAFE_TIMEOUT_MS = 12_000L
        private const val PREVIEW_SPLASH_DURATION_MS = 2_500L

        private val EXTERNAL_SCHEMES = setOf(
            "tg", "whatsapp", "mailto", "tel", "sms", "intent", "market"
        )

        private val EXTERNAL_HOSTS = setOf(
            "t.me", "telegram.me", "telegram.dog",
            "wa.me", "api.whatsapp.com", "chat.whatsapp.com"
        )

        fun shouldOpenExternally(url: String): Boolean {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme in EXTERNAL_SCHEMES) return true
            if (scheme != "http" && scheme != "https") return true
            val host = uri.host?.lowercase() ?: return false
            return EXTERNAL_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
