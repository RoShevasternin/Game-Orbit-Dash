package com.lewydo.orbitdash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import com.lewydo.orbitdash.databinding.ActivityMainBinding
import com.lewydo.orbitdash.services.ads.AdManager
import com.lewydo.orbitdash.services.leaderboard.LeaderboardManager
import com.lewydo.orbitdash.services.tiktok.RemoteConfigModel
import com.lewydo.orbitdash.services.tiktok.TikTokManager
import com.lewydo.orbitdash.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private var onCreateCounter = 0

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {

    companion object {
        var statusBarHeight = 0
        var navBarHeight    = 0
    }

    private val coroutine = CoroutineScope(Dispatchers.Default)

    private val onceExit            = AtomicBoolean(true)
    private val onceSystemBarHeight = AtomicBoolean(true)

    private lateinit var binding : ActivityMainBinding

    val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }

    lateinit var adManager: AdManager
        private set

    lateinit var leaderboardManager: LeaderboardManager
        private set

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        onCreateCounter++
        log("MainActivity: $onCreateCounter")

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        initialize()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            if (onceSystemBarHeight.getAndSet(false)) {
                statusBarHeight = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
                navBarHeight    = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom

                log("statusBarHeight = $statusBarHeight | navBarHeight = $navBarHeight")

                // hide Status or Nav bar (після встановлення їх розмірів)
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    override fun exit() {
        if (onceExit.getAndSet(false)) {
            log("exit")
            finish()
        }
    }

    override fun onResume()  { super.onResume();  adManager.onResume() }
    override fun onPause()   { super.onPause();   adManager.onPause() }
    override fun onDestroy() { super.onDestroy(); adManager.onDestroy() }

    private fun initialize() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeAds()
        initializeLeaderboard()

        fetchRemoteConfig { log("COMPLETE CONFIG...") }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    fun openEmail(to: String, subject: String = "") {
        runOnUiThread {
            val uri = "mailto:$to?subject=${Uri.encode(subject)}".toUri()

            val intent = Intent(Intent.ACTION_SENDTO, uri)
            startActivity(Intent.createChooser(intent, "Send email"))
        }
    }

    fun openInstagram(username: String) {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "instagram://user?username=$username".toUri()).setPackage("com.instagram.android"))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, "https://www.instagram.com/$username".toUri()))
            }
        }
    }

    fun openTelegram(username: String) {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "tg://resolve?domain=$username".toUri()).setPackage("org.telegram.messenger"))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/$username".toUri()))
            }
        }
    }

    fun openPlayMarket() {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "market://dev?id=5953840215091948966".toUri()).setPackage("com.android.vending"))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/dev?id=5953840215091948966".toUri()))
            }
        }
    }

    // ------------------------------------------------------------------------
    // Ads
    // ------------------------------------------------------------------------

    private fun initializeAds() {
        adManager = AdManager(this, binding)
        adManager.initialize()
    }

    // ------------------------------------------------------------------------
    // Leaderboard
    // ------------------------------------------------------------------------

    private fun initializeLeaderboard() {
        leaderboardManager = LeaderboardManager(this, getString(R.string.leaderboard_id))
        leaderboardManager.initialize()
    }

    fun submitXp(xp: Long) {
        leaderboardManager.submitScore(xp)
    }

    fun showLeaderboard() {
        leaderboardManager.showLeaderboard()
    }

    // ------------------------------------------------------------------------
    // Services
    // ------------------------------------------------------------------------
    private fun fetchRemoteConfig(onComplete: (success: Boolean) -> Unit) {
        val remoteConfig = Firebase.remoteConfig

        // налаштування (інтервал оновлення)
        val settings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600   // 1 год; для тесту постав 0
        }
        remoteConfig.setConfigSettingsAsync(settings)

        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                runCatching {
                    val json = remoteConfig.getString("config")
                    val model = Gson().fromJson(json, RemoteConfigModel::class.java)

                    log("MODEL = $model")

                    initTikTok(model)
                    onComplete(true)
                }.onFailure {
                    log("Parse failed: $it")
                    onComplete(false)
                }
            } else {
                log("Fetch failed: ${task.exception}")
                onComplete(false)
            }
        }
    }

    private fun initTikTok(model: RemoteConfigModel) {
        val tiktok = model.tiktok
        if (tiktok == null || !tiktok.isValid) {
            log("TikTok config missing/invalid — skip init")
            return
        }
        TikTokManager.initialize(application, tiktok.appIds, tiktok.secret!!)
    }

    // ------------------------------------------------------------------------
    // PERMISSIONS
    // ------------------------------------------------------------------------
    /**
     * Push permission (Android 13+)
     * */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> log("POST_NOTIFICATIONS granted = $granted") }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

}