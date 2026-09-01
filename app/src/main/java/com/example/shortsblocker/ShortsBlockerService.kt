package com.example.shortsblocker

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessibility service that:
 * 1. Tracks exact per-second usage of monitored apps (YouTube, Facebook, Instagram, Custom apps)
 *    using a reliable 1-second ticker even when full-screen videos are playing passively.
 * 2. Immediately closes (GLOBAL_ACTION_HOME) the app when its App Limit is reached and locks it for 24 hours until reset.
 * 3. Detects and blocks Shorts/Reels when the dedicated Short Limit is reached, or instantly if Block (0m/Off).
 *    If Short Limit is -1 (Unlimited), shorts are allowed without blocking.
 * 4. Detects and blocks adult/porn websites and custom added domains inside web browsers.
 * 5. Persistently maintains the 24-hour lock across app closures and restarts until 24 hours expire or user clicks Reset in Blocker app.
 */
class ShortsBlockerService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastBackActionTimestamp: Long = 0L
    private var lastHomeActionTimestamp: Long = 0L
    private var lastToastTimestamp: Long = 0L
    private var consecutiveShortsBackAttempts: Int = 0

    @Volatile
    private var activeForegroundPackage: String = ""
    @Volatile
    private var lastPackageEventTime: Long = 0L

    // Adult keywords and popular porn domain substrings
    private val adultKeywords = listOf(
        "porn", "xxx", "xvideos", "pornhub", "xnxx", "xhamster", "redtube",
        "youporn", "brazzers", "sex", "nude", "erotic", "nsfw", "cam4",
        "chaturbate", "onlyfans", "bangbros", "adultdvd", "eporner", "beeg",
        "hqporner", "tnaflix", "tube8", "spankwire", "daftsex", "vporn", "leakgirls"
    )

    // Intrusive Ad Networks, Popups and Malicious Trackers
    private val defaultAdNetworks = listOf(
        "doubleclick.net", "googleadservices", "pagead2.googlesyndication",
        "popads.net", "propellerads", "adsterra", "exoclick", "trafficjunky",
        "outbrain.com", "taboola.com", "mgid.com", "adnxs.com", "criteo.com",
        "adroll.com", "clickadu", "richpush", "onclickads", "bet365", "1xbet",
        "melbet", "mostbet", "adcolony", "applovin", "unityads", "ironsrc"
    )

    // Skip Ad button identifiers and texts across apps and web
    private val skipAdButtonKeywords = listOf(
        // Resource / View IDs
        "skip_ad", "ad_skip", "skip_button", "skipbutton", "btn_skip",
        "ytp-ad-skip-button", "ytp-ad-skip-button-modern", "ytp-ad-skip-button-slot",
        "ytp-ad-skip-button-text", "ytp-ad-preview-container", "action_skip",
        "skip_ad_container", "ad_skip_button", "video_ad_skip_button",
        "skip_overlay", "skip_countdown", "ad_dismiss", "dismiss_ad",
        "close_ad", "ad_close", "close_ad_button", "tt_video_ad_close",
        "tt_reward_full_count_down", "anythink_myoffer_btn_close",
        "ksad_end_close_btn", "al_exo_close_button", "mbridge_interstitial_close_id",
        "native_ad_view", "interstitial_control_button", "iv_close",
        "btn_close_ad", "ad_cancel", "cancel_ad", "btn_close", "banner_close",
        "popup_close", "ad_close_btn", "btn_ad_close", "close_button", "ad_skip_text",

        // Texts / Content Descriptions
        "skip ad", "skip ads", "skip video", "skip in", "skip",
        "skipping in", "ad will end in", "close ad", "close ads",
        "dismiss ad", "dismiss advertisement", "close advertisement",
        "বিজ্ঞাপন এড়িয়ে যান", "বিজ্ঞাপন এড়িয়ে যান", "স্কিপ",
        "বিজ্ঞাপন বন্ধ করুন", "বিজ্ঞাপন বাদ দিন", "বিজ্ঞাপন"
    )

    private val popupAdCloseKeywords = listOf(
        "close", "dismiss", "✕", "✖", "❌", "×", "x",
        "btn_close", "close_btn", "iv_close", "close_button",
        "ad_close", "popup_close", "banner_close", "interstitial_close"
    )

    // Browsers package names to inspect for URL / Web address bars
    private val browserPackages = listOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.UCMobile.intl",
        "com.uc.browser.en",
        "com.mi.globalbrowser"
    )

    // Full-screen shorts/reels player identifiers (strictly specific to the active shorts player, NOT the feed or home tab)
    private val youtubePlayerIndicators = listOf(
        "reel_watch_fragment",
        "reel_player_fragment",
        "shorts_player_fragment",
        "shorts_player_view",
        "reel_player_page",
        "shorts_video_surface_view",
        "reel_video_view",
        "reel_watch_container",
        "shorts_container",
        "shorts_root",
        "reel_root",
        "shorts_main_container",
        "shorts_player_surface",
        "reel_player_overlay",
        "reel_player_video_link",
        "reel_player_creator_avatar",
        "reel_player_like_button",
        "reel_player_dislike_button",
        "reel_player_comment_button",
        "reel_player_share_button",
        "reel_player_remix_button",
        "reel_player_sound_button",
        "shorts_sound_title",
        "shorts_pivot_button",
        "shorts_camera_button",
        "shorts_remix_button",
        "reel_item_player",
        "reel_watch_view",
        "reelwatchactivity",
        "shortsactivity",
        "shorts_video_view",
        "shorts_pager",
        "reel_pager",
        "shorts_video_container",
        "shorts_view_pager",
        "youtube_reel"
    )

    // Specific text/contentDescription strictly unique to active full-screen Shorts player
    private val youtubeShortsTextKeywords = listOf(
        "dislike this short",
        "like this short",
        "remix this short",
        "sound used in this short",
        "remix with this sound",
        "use this sound",
        "create a short",
        "create short",
        "pause short",
        "play short",
        "shorts sound",
        "open remix menu",
        "এই শর্টটি অপছন্দ করুন",
        "এই শর্টটি পছন্দ করুন",
        "শর্টটি অপছন্দ করুন",
        "শর্টটি পছন্দ করুন",
        "এই সাউন্ড দিয়ে শর্ট তৈরি করুন"
    )

    private val facebookReelsPlayerIndicators = listOf(
        "fb_shorts_viewer_fragment",
        "reels_viewer_fragment",
        "fb_shorts_full_screen",
        "reels_video_view_container",
        "reel_viewer_activity",
        "reel_viewer_page",
        "full_screen_video_player_reels",
        "reelsvieweractivity",
        "fbshortsactivity",
        "reel_action_bar",
        "fb_shorts_container",
        "reels_video_surface",
        "reels_tray_fullscreen",
        "fb_reels_video_view"
    )

    private val facebookReelsTextKeywords = listOf(
        "remix this reel",
        "remix with this reel",
        "use this audio",
        "original audio",
        "like this reel",
        "comment on reel",
        "share this reel",
        "swipe up for next reel",
        "swipe up to view next reel",
        "audio used in reel",
        "এই রিলটি পছন্দ করুন",
        "এই রিলটি শেয়ার করুন",
        "এই অডিও দিয়ে রিল তৈরি করুন",
        "রিল তৈরি করুন"
    )

    private val instagramReelsPlayerIndicators = listOf(
        "clips_viewer_fragment",
        "clips_video_player",
        "reel_viewer_fragment",
        "clips_viewer_container",
        "instagram_reel_viewer",
        "clips_video_container",
        "clips_action_bar",
        "clips_author_container",
        "clips_camera",
        "clipsvieweractivity",
        "reel_viewer_root",
        "clips_swipe_refresh_layout",
        "clips_audio_mix_editor"
    )

    private val instagramReelsTextKeywords = listOf(
        "reels video player",
        "audio used in this reel",
        "remix this reel",
        "use audio",
        "like reel",
        "comment on reel",
        "share reel",
        "original audio - ",
        "watch reels",
        "clips_audio",
        "এই রিলটি লাইক করুন",
        "এই রিলটি শেয়ার করুন"
    )

    // 1-second continuous foreground ticker to ensure time tracking always happens
    // even during long video playback where no accessibility events are triggered
    private val tickerRunnable = object : Runnable {
        override fun run() {
            try {
                onTickerTick()
            } catch (_: Exception) {
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val NOTIFICATION_CHANNEL_ID = "shorts_blocker_service_channel"
    private val NOTIFICATION_ID = 1001

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "ShortsBlocker ব্যাকগ্রাউন্ড সুরক্ষা",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "অ্যাপ লিমিট এবং শর্টস ব্লকিং ব্যাকগ্রাউন্ডে চালু রাখে"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startOrUpdateForegroundNotification() {
        try {
            createNotificationChannel()
            if (!::prefs.isInitialized) {
                prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            val isMasterEnabled = prefs.getBoolean(PREF_ENABLED, true)
            val isNotifEnabled = prefs.getBoolean(PREF_PERSISTENT_NOTIFICATION, true)

            if (!isMasterEnabled || !isNotifEnabled) {
                return
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_blocker_shield_1788275701896)
                .setContentTitle("ShortsBlocker সুরক্ষা সক্রিয় রয়েছে")
                .setContentText("অ্যাপ লিমিট, শর্টস ও অ্যাড ব্লকার ব্যাকগ্রাউন্ডে চলছে")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkAndResetDailyUsage()
        startOrUpdateForegroundNotification()
        mainHandler.removeCallbacks(tickerRunnable)
        mainHandler.post(tickerRunnable)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkAndResetDailyUsage()
        startOrUpdateForegroundNotification()
        mainHandler.removeCallbacks(tickerRunnable)
        mainHandler.post(tickerRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startOrUpdateForegroundNotification()
        mainHandler.removeCallbacks(tickerRunnable)
        mainHandler.post(tickerRunnable)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure ticker and background execution never die even when user clears recent apps
        mainHandler.removeCallbacks(tickerRunnable)
        mainHandler.post(tickerRunnable)
        startOrUpdateForegroundNotification()
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(tickerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(tickerRunnable)
    }

    private fun getTrackedAppInfo(pkg: String): TrackedAppInfo? {
        if (pkg.isBlank()) return null
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        val isMasterEnabled = prefs.getBoolean(PREF_ENABLED, true)
        if (!isMasterEnabled) return null

        val lowerPkg = pkg.lowercase()
        return when {
            lowerPkg.contains("youtube") -> {
                TrackedAppInfo(appKey = "youtube", appLabel = "YouTube", packageName = pkg, isCustomApp = false)
            }
            lowerPkg.contains("facebook.katana") || lowerPkg.contains("facebook.lite") || lowerPkg.contains("facebook.orca") -> {
                TrackedAppInfo(appKey = "facebook", appLabel = "Facebook", packageName = pkg, isCustomApp = false)
            }
            lowerPkg.contains("instagram") -> {
                TrackedAppInfo(appKey = "instagram", appLabel = "Instagram", packageName = pkg, isCustomApp = false)
            }
            else -> {
                val customAppsStr = prefs.getString(PREF_CUSTOM_APPS, "") ?: ""
                val matchingCustom = customAppsStr.split(";")
                    .filter { it.isNotBlank() }
                    .mapNotNull {
                        val parts = it.split("#")
                        if (parts.size >= 3) Triple(parts[0], parts[1], parts[2].toBoolean()) else null
                    }
                    .firstOrNull { (_, customPkg, isEnabled) ->
                        isEnabled && (pkg.equals(customPkg, ignoreCase = true) || pkg.contains(customPkg, ignoreCase = true))
                    }

                if (matchingCustom != null) {
                    TrackedAppInfo(
                        appKey = matchingCustom.second,
                        appLabel = matchingCustom.first,
                        packageName = matchingCustom.second,
                        isCustomApp = true
                    )
                } else null
            }
        }
    }

    /**
     * Checks if the app is under 24-hour lockout.
     * Returns true if locked, false if not locked or 24h expired.
     */
    private fun isAppLocked(appKey: String): Boolean {
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val isLocked = prefs.getBoolean("app_locked_$appKey", false)
        if (!isLocked) return false

        val lockUntil = prefs.getLong("app_locked_until_$appKey", 0L)
        val now = System.currentTimeMillis()
        if (lockUntil > 0L && now >= lockUntil) {
            // 24 hours elapsed -> automatically unlock and reset usage
            prefs.edit()
                .putBoolean("app_locked_$appKey", false)
                .putLong("app_locked_until_$appKey", 0L)
                .putLong("app_used_sec_$appKey", 0L)
                .commit()
            return false
        }
        return true
    }

    private fun isShortsLocked(appKey: String): Boolean {
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val isLocked = prefs.getBoolean("shorts_locked_$appKey", false)
        if (!isLocked) return false

        val lockUntil = prefs.getLong("shorts_locked_until_$appKey", 0L)
        val now = System.currentTimeMillis()
        if (lockUntil > 0L && now >= lockUntil) {
            prefs.edit()
                .putBoolean("shorts_locked_$appKey", false)
                .putLong("shorts_locked_until_$appKey", 0L)
                .putLong("shorts_used_sec_$appKey", 0L)
                .commit()
            return false
        }
        return true
    }

    /**
     * Executes every 1 second to reliably record seconds used and enforce limits
     */
    private fun onTickerTick() {
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        checkAndResetDailyUsage()

        val isMasterEnabled = prefs.getBoolean(PREF_ENABLED, true)
        if (!isMasterEnabled) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isInteractive == false) {
            return
        }

        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        val rootPkg = root?.packageName?.toString() ?: ""
        val now = SystemClock.elapsedRealtime()

        val currentPkg = when {
            rootPkg.isNotBlank() && !isIgnoredSystemPackage(rootPkg) -> {
                activeForegroundPackage = rootPkg
                rootPkg
            }
            activeForegroundPackage.isNotBlank() -> activeForegroundPackage
            else -> ""
        }

        if (currentPkg.isBlank()) return

        // Auto-skip video ads and close popup ads continuously in any app or webview
        if (root != null) {
            tryAutoSkipAds(root, currentPkg)
        }

        val trackedApp = getTrackedAppInfo(currentPkg) ?: return
        val appKey = trackedApp.appKey
        val appLabel = trackedApp.appLabel

        // 1. Check if app is locked under 24-hour lock rule
        if (isAppLocked(appKey)) {
            val lockUntil = prefs.getLong("app_locked_until_$appKey", 0L)
            if (now - lastHomeActionTimestamp >= 500L) {
                lastHomeActionTimestamp = now
                showApp24HourLockedToast(appLabel, lockUntil)
                recordBlockEvent("$appLabel (২৪ ঘণ্টার লকড / 24h Lock)", currentPkg)
                activeForegroundPackage = ""
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return
        }

        // 2. Increment this app's daily used seconds
        val appPrefLimitKey = "app_limit_$appKey"
        val appPrefUsedKey = "app_used_sec_$appKey"
        val appLimitMinutes = prefs.getInt(appPrefLimitKey, 0)
        val currentAppUsed = prefs.getLong(appPrefUsedKey, 0L) + 1L
        prefs.edit().putLong(appPrefUsedKey, currentAppUsed).commit()

        // 3. Check if App Limit is exceeded -> Trigger 24-hour Lockout
        if (appLimitMinutes > 0 && currentAppUsed >= (appLimitMinutes * 60L)) {
            val lockUntilTimestamp = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
            prefs.edit()
                .putBoolean("app_locked_$appKey", true)
                .putLong("app_locked_until_$appKey", lockUntilTimestamp)
                .commit()

            if (now - lastHomeActionTimestamp >= 500L) {
                lastHomeActionTimestamp = now
                showApp24HourLockedToast(appLabel, lockUntilTimestamp)
                recordBlockEvent("$appLabel (২৪ ঘণ্টার লকড / 24h Lock)", currentPkg)
                activeForegroundPackage = ""
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return
        }

        // 4. If it's YouTube / Facebook / Instagram, track Shorts / Reels time
        if (!trackedApp.isCustomApp && root != null) {
            val isShortsOpen = when (appKey) {
                "youtube" -> isYouTubeShortsPlayerActive(root)
                "facebook" -> isFacebookReelsPlayerActive(root)
                "instagram" -> isInstagramReelsPlayerActive(root)
                else -> false
            }

            if (isShortsOpen) {
                val shortsPrefLimitKey = "shorts_limit_$appKey"
                val shortsPrefUsedKey = "shorts_used_sec_$appKey"
                val shortsLimitMinutes = prefs.getInt(shortsPrefLimitKey, 0) // -1: Unlimited, 0: Block (Off), >0: Mins

                val currentShortsUsed = prefs.getLong(shortsPrefUsedKey, 0L) + 1L
                prefs.edit().putLong(shortsPrefUsedKey, currentShortsUsed).commit()

                val shortsLocked = isShortsLocked(appKey)

                // If shortsLimitMinutes == -1 (Unlimited), do NOT block.
                // If shortsLimitMinutes == 0 (Block), block immediately.
                // If shortsLimitMinutes > 0, block when used >= limit (24h locked).
                if (shortsLimitMinutes != -1 && (shortsLimitMinutes == 0 || shortsLocked || currentShortsUsed >= (shortsLimitMinutes * 60L))) {
                    if (shortsLimitMinutes > 0 && !shortsLocked) {
                        val shortsLockUntil = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
                        prefs.edit()
                            .putBoolean("shorts_locked_$appKey", true)
                            .putLong("shorts_locked_until_$appKey", shortsLockUntil)
                            .commit()
                    }

                    if (now - lastBackActionTimestamp >= THROTTLE_INTERVAL_MS) {
                        lastBackActionTimestamp = now
                        consecutiveShortsBackAttempts++
                        val shortLabel = when (appKey) {
                            "youtube" -> "YouTube Shorts"
                            "facebook" -> "Facebook Reels"
                            "instagram" -> "Instagram Reels"
                            else -> appLabel
                        }
                        if (shortsLimitMinutes > 0) {
                            val shortsLockUntil = prefs.getLong("shorts_locked_until_$appKey", 0L)
                            showShorts24HourLockedToast(shortLabel, shortsLockUntil)
                        } else {
                            showShortsInstantBlockToast(shortLabel)
                        }
                        recordBlockEvent(shortLabel, currentPkg)
                        if (consecutiveShortsBackAttempts > 2) {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        } else {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                    }
                } else {
                    consecutiveShortsBackAttempts = 0
                }
            } else {
                consecutiveShortsBackAttempts = 0
            }
        }
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("latin") ||
                lower.contains("toast") ||
                lower.contains("volume")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::prefs.isInitialized) {
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        val isMasterEnabled = prefs.getBoolean(PREF_ENABLED, true)
        if (!isMasterEnabled) return

        checkAndResetDailyUsage()

        val packageName = event.packageName?.toString() ?: return

        if (!isIgnoredSystemPackage(packageName)) {
            activeForegroundPackage = packageName
            lastPackageEventTime = SystemClock.elapsedRealtime()
        }

        // 1. Check if event is in a Web Browser for Adult/Porn Sites or Custom Blocked Domains or Ads
        val isBrowser = browserPackages.any { packageName.contains(it, ignoreCase = true) }
        if (isBrowser) {
            handleBrowserEvent(packageName)
            return
        }

        // 2. Try auto-skipping video ads if enabled
        val eventClassName = event.className?.toString() ?: ""
        val root: AccessibilityNodeInfo? = rootInActiveWindow ?: event.source
        if (root != null) {
            tryAutoSkipAds(root, packageName)
        }

        // 3. Check for Tracked Apps
        val trackedApp = getTrackedAppInfo(packageName) ?: return
        val appKey = trackedApp.appKey
        val appLabel = trackedApp.appLabel
        val isCustomApp = trackedApp.isCustomApp

        val now = SystemClock.elapsedRealtime()

        // Immediate check: If this app is under 24-hour lockout or limit exceeded, kick user out to Home
        val appLimitMinutes = prefs.getInt("app_limit_$appKey", 0)
        val todayAppUsedSeconds = prefs.getLong("app_used_sec_$appKey", 0L)
        val appLocked = isAppLocked(appKey)

        if (appLocked || (appLimitMinutes > 0 && todayAppUsedSeconds >= (appLimitMinutes * 60L))) {
            if (!appLocked && appLimitMinutes > 0) {
                val lockUntilTimestamp = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
                prefs.edit()
                    .putBoolean("app_locked_$appKey", true)
                    .putLong("app_locked_until_$appKey", lockUntilTimestamp)
                    .commit()
            }

            if (now - lastHomeActionTimestamp >= 500L) {
                lastHomeActionTimestamp = now
                val lockUntil = prefs.getLong("app_locked_until_$appKey", System.currentTimeMillis() + 24 * 3600 * 1000L)
                showApp24HourLockedToast(appLabel, lockUntil)
                recordBlockEvent("$appLabel (২৪ ঘণ্টার লকড / 24h Lock)", packageName)
                activeForegroundPackage = ""
                performGlobalAction(GLOBAL_ACTION_HOME)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return
        }

        val isShortsOpen = when {
            isCustomApp -> true
            root != null && packageName.contains("youtube", ignoreCase = true) -> isYouTubeShortsPlayerActive(root, eventClassName)
            root != null && (packageName.contains("facebook.katana", ignoreCase = true) || packageName.contains("facebook.lite", ignoreCase = true) || packageName.contains("facebook.orca", ignoreCase = true)) -> isFacebookReelsPlayerActive(root, eventClassName)
            root != null && packageName.contains("instagram", ignoreCase = true) -> isInstagramReelsPlayerActive(root, eventClassName)
            packageName.contains("youtube", ignoreCase = true) && (eventClassName.contains("ReelWatchActivity", ignoreCase = true) || eventClassName.contains("ShortsActivity", ignoreCase = true) || eventClassName.contains("ReelItemFragment", ignoreCase = true)) -> true
            packageName.contains("instagram", ignoreCase = true) && eventClassName.contains("ClipsViewerActivity", ignoreCase = true) -> true
            (packageName.contains("facebook.katana", ignoreCase = true) || packageName.contains("facebook.lite", ignoreCase = true)) && eventClassName.contains("ReelsViewerActivity", ignoreCase = true) -> true
            else -> false
        }

        if (isShortsOpen) {
            val shortsPrefLimitKey = "shorts_limit_$appKey"
            val shortsPrefUsedKey = "shorts_used_sec_$appKey"
            val shortsLimitMinutes = prefs.getInt(shortsPrefLimitKey, 0)
            val todayShortsUsedSeconds = prefs.getLong(shortsPrefUsedKey, 0L)
            val shortsLocked = isShortsLocked(appKey)

            // -1 = Unlimited (no block), 0 = Block, > 0 = time limit
            if (shortsLimitMinutes != -1 && (shortsLimitMinutes == 0 || shortsLocked || todayShortsUsedSeconds >= (shortsLimitMinutes * 60L))) {
                if (shortsLimitMinutes > 0 && !shortsLocked) {
                    val shortsLockUntil = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
                    prefs.edit()
                        .putBoolean("shorts_locked_$appKey", true)
                        .putLong("shorts_locked_until_$appKey", shortsLockUntil)
                        .commit()
                }

                if (now - lastBackActionTimestamp >= THROTTLE_INTERVAL_MS) {
                    lastBackActionTimestamp = now
                    consecutiveShortsBackAttempts++
                    val shortLabel = when (appKey) {
                        "youtube" -> "YouTube Shorts"
                        "facebook" -> "Facebook Reels"
                        "instagram" -> "Instagram Reels"
                        else -> appLabel
                    }
                    if (shortsLimitMinutes > 0) {
                        val shortsLockUntil = prefs.getLong("shorts_locked_until_$appKey", 0L)
                        showShorts24HourLockedToast(shortLabel, shortsLockUntil)
                    } else {
                        showShortsInstantBlockToast(shortLabel)
                    }
                    recordBlockEvent(shortLabel, packageName)
                    if (consecutiveShortsBackAttempts > 2) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    } else {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }
            } else {
                consecutiveShortsBackAttempts = 0
            }
        } else {
            consecutiveShortsBackAttempts = 0
        }
    }

    private fun handleBrowserEvent(packageName: String) {
        val blockAdult = prefs.getBoolean(PREF_BLOCK_ADULT_WEBSITES, true)
        val blockAds = prefs.getBoolean(PREF_BLOCK_ADS, true)
        
        val customSitesStr = prefs.getString(PREF_CUSTOM_WEBSITES, "") ?: ""
        val customSites = customSitesStr.split(";")
            .filter { it.isNotBlank() }
            .mapNotNull {
                val parts = it.split("#")
                if (parts.size >= 2 && parts[1].toBoolean()) parts[0].lowercase().trim() else null
            }

        val customAdFiltersStr = prefs.getString(PREF_CUSTOM_AD_FILTERS, "") ?: ""
        val customAdFilters = customAdFiltersStr.split(";")
            .filter { it.isNotBlank() }
            .mapNotNull {
                val parts = it.split("#")
                if (parts.size >= 2 && parts[1].toBoolean()) parts[0].lowercase().trim() else null
            }

        val root = rootInActiveWindow ?: return
        val urlOrContent = findBrowserUrlOrKeywords(root) ?: return

        val containsAdult = blockAdult && adultKeywords.any { urlOrContent.contains(it) }
        val containsCustomSite = customSites.any { it.isNotEmpty() && urlOrContent.contains(it) }
        val containsAdNetwork = blockAds && (defaultAdNetworks.any { urlOrContent.contains(it) } || customAdFilters.any { it.isNotEmpty() && urlOrContent.contains(it) })

        if (containsAdult || containsCustomSite || containsAdNetwork) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackActionTimestamp >= THROTTLE_INTERVAL_MS) {
                lastBackActionTimestamp = now
                val label = when {
                    containsAdult -> "Adult Website Blocked"
                    containsAdNetwork -> "Ad Popup / Network Blocked"
                    else -> "Custom Website Blocked"
                }
                recordBlockEvent(label, packageName)
                if (containsAdult || containsCustomSite) {
                    showReminderToast()
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun tryAutoSkipAds(root: AccessibilityNodeInfo, packageName: String) {
        val autoSkip = prefs.getBoolean(PREF_AUTO_SKIP_VIDEO_ADS, true)
        val blockPopup = prefs.getBoolean(PREF_BLOCK_POPUP_ADS, true)
        val blockAds = prefs.getBoolean(PREF_BLOCK_ADS, true)
        if (!autoSkip && !blockPopup && !blockAds) return

        // 1. Try finding and clicking Skip Ad buttons (YouTube, Facebook, Instagram, Apps, Games)
        if (autoSkip || blockAds) {
            val skipButton = findClickableNodeMatchingKeywords(root, skipAdButtonKeywords)
            if (skipButton != null) {
                val clicked = performRobustClick(skipButton)
                if (clicked) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastToastTimestamp >= 2500L) {
                        lastToastTimestamp = now
                        recordAdBlockEvent("Video Ad Skipped", packageName)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(applicationContext, "⚡ বিজ্ঞাপন স্কিপ করা হয়েছে! (Ad Skipped)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }
            }
        }

        // 2. If popup ads blocking is active, check for popup/interstitial close buttons
        if (blockPopup || blockAds) {
            val isLikelyAd = isLikelyAdContainerOrActivity(root, packageName)
            if (isLikelyAd) {
                val closeButton = findClickableNodeMatchingKeywords(root, popupAdCloseKeywords)
                if (closeButton != null) {
                    val clicked = performRobustClick(closeButton)
                    if (clicked) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastToastTimestamp >= 2500L) {
                            lastToastTimestamp = now
                            recordAdBlockEvent("Popup Ad Dismissed", packageName)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(applicationContext, "🛡️ পপ-আপ বিজ্ঞাপন বন্ধ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun performRobustClick(node: AccessibilityNodeInfo): Boolean {
        // Try clicking direct node
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Try climbing parent tree (up to 5 levels)
        var currentParent = node.parent
        var depth = 0
        while (currentParent != null && depth < 5) {
            if (currentParent.isClickable && currentParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            currentParent = currentParent.parent
            depth++
        }

        // Try direct ACTION_CLICK as fallback on the node even if isClickable reports false
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Try clicking clickable child
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable && child.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }

        return false
    }

    private fun isLikelyAdContainerOrActivity(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val rootClass = root.className?.toString()?.lowercase() ?: ""
        val rootPkg = packageName.lowercase()
        if (rootClass.contains("adactivity") || rootClass.contains("interstitialactivity") || rootClass.contains("googleadactivity")) {
            return true
        }
        if (rootPkg.contains("unityads") || rootPkg.contains("applovin") || rootPkg.contains("adcolony") || rootPkg.contains("ironsrc")) {
            return true
        }
        return hasAdIndicatorsInTree(root)
    }

    private fun hasAdIndicatorsInTree(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 20) return false
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (viewId.contains("ad_view") || viewId.contains("ad_container") || viewId.contains("banner_ad") ||
            viewId.contains("native_ad") || viewId.contains("interstitial_ad") ||
            text.equals("ad", ignoreCase = true) || text.equals("sponsored", ignoreCase = true) ||
            desc.equals("advertisement", ignoreCase = true) || desc.equals("sponsored", ignoreCase = true)
        ) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasAdIndicatorsInTree(child, depth + 1)) return true
        }
        return false
    }

    private fun findClickableNodeMatchingKeywords(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 35) return null

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val matches = keywords.any {
            viewId.contains(it) || (text.isNotBlank() && text.contains(it)) || (desc.isNotBlank() && desc.contains(it))
        }

        if (matches) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableNodeMatchingKeywords(child, keywords, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun findBrowserUrlOrKeywords(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 25) return null

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (viewId.contains("url_bar") || viewId.contains("location_bar") || viewId.contains("search_box") ||
            viewId.contains("address") || viewId.contains("omnibox") || viewId.contains("search_src_text")
        ) {
            if (text.isNotBlank()) return text
            if (desc.isNotBlank()) return desc
        }

        // Also check if text matches adult keywords directly
        if (adultKeywords.any { text.contains(it) || desc.contains(it) }) {
            return text.ifBlank { desc }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findBrowserUrlOrKeywords(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun showReminderToast() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastTimestamp < 2000L) return
        lastToastTimestamp = now

        val customMsg = prefs.getString(PREF_REMINDER_MESSAGE, DEFAULT_REMINDER_MESSAGE) ?: DEFAULT_REMINDER_MESSAGE
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "⚠️ $customMsg", Toast.LENGTH_LONG).show()
        }
    }

    private fun showApp24HourLockedToast(appName: String, lockUntilTimestamp: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastTimestamp < 2500L) return
        lastToastTimestamp = now

        val currentTime = System.currentTimeMillis()
        val remainingMillis = (lockUntilTimestamp - currentTime).coerceAtLeast(0L)
        val remainingHours = (remainingMillis / (1000 * 60 * 60)).toInt()
        val remainingMinutes = ((remainingMillis / (1000 * 60)) % 60).toInt()

        val timeStr = when {
            remainingHours > 0 -> "$remainingHours ঘণ্টা $remainingMinutes মিনিট"
            remainingMinutes > 0 -> "$remainingMinutes মিনিট"
            else -> "কয়েক মুহূর্ত"
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "⛔ $appName এর সময়সীমা শেষ!\n২৪ ঘণ্টার লকড আছে ($timeStr বাকি)। Blocker অ্যাপে রিসেট না করা পর্যন্ত $appName বন্ধ থাকবে।",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showShortsInstantBlockToast(shortLabel: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastTimestamp < 2000L) return
        lastToastTimestamp = now

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "🚫 $shortLabel ব্লক করা হয়েছে!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showShorts24HourLockedToast(shortLabel: String, lockUntilTimestamp: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastTimestamp < 2500L) return
        lastToastTimestamp = now

        val currentTime = System.currentTimeMillis()
        val remainingMillis = (lockUntilTimestamp - currentTime).coerceAtLeast(0L)
        val remainingHours = (remainingMillis / (1000 * 60 * 60)).toInt()
        val remainingMinutes = ((remainingMillis / (1000 * 60)) % 60).toInt()

        val timeStr = when {
            remainingHours > 0 -> "$remainingHours ঘণ্টা $remainingMinutes মিনিট"
            remainingMinutes > 0 -> "$remainingMinutes মিনিট"
            else -> "কয়েক মুহূর্ত"
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "⏳ $shortLabel এর দৈনিক লিমিট শেষ!\n২৪ ঘণ্টার জন্য ($timeStr বাকি) অথবা Blocker অ্যাপে রিসেট না করা পর্যন্ত শর্টস বন্ধ থাকবে।",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkAndResetDailyUsage() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString(PREF_TODAY_DATE, "") ?: ""
        val now = System.currentTimeMillis()

        if (savedDate.isEmpty()) {
            prefs.edit().putString(PREF_TODAY_DATE, todayStr).apply()
        } else if (savedDate != todayStr) {
            val editor = prefs.edit().putString(PREF_TODAY_DATE, todayStr)
            val apps = listOf("youtube", "facebook", "instagram")

            apps.forEach { appKey ->
                val isLocked = prefs.getBoolean("app_locked_$appKey", false)
                val lockUntil = prefs.getLong("app_locked_until_$appKey", 0L)
                // Only reset if lock has expired (24 hours passed) or wasn't locked
                if (!isLocked || now >= lockUntil) {
                    editor.putBoolean("app_locked_$appKey", false)
                    editor.putLong("app_locked_until_$appKey", 0L)
                    editor.putLong("app_used_sec_$appKey", 0L)
                }

                val isShortsLocked = prefs.getBoolean("shorts_locked_$appKey", false)
                val shortsLockUntil = prefs.getLong("shorts_locked_until_$appKey", 0L)
                if (!isShortsLocked || now >= shortsLockUntil) {
                    editor.putBoolean("shorts_locked_$appKey", false)
                    editor.putLong("shorts_locked_until_$appKey", 0L)
                    editor.putLong("shorts_used_sec_$appKey", 0L)
                }
            }

            // Custom apps
            val customAppsStr = prefs.getString(PREF_CUSTOM_APPS, "") ?: ""
            customAppsStr.split(";").filter { it.isNotBlank() }.forEach {
                val parts = it.split("#")
                if (parts.size >= 2) {
                    val pkg = parts[1]
                    val isLocked = prefs.getBoolean("app_locked_$pkg", false)
                    val lockUntil = prefs.getLong("app_locked_until_$pkg", 0L)
                    if (!isLocked || now >= lockUntil) {
                        editor.putBoolean("app_locked_$pkg", false)
                        editor.putLong("app_locked_until_$pkg", 0L)
                        editor.putLong("app_used_sec_$pkg", 0L)
                    }
                }
            }
            editor.apply()
        }
    }

    private fun isYouTubeShortsPlayerActive(root: AccessibilityNodeInfo, className: String = ""): Boolean {
        val lowerClass = className.lowercase()
        if (lowerClass.contains("reelwatchactivity") ||
            lowerClass.contains("shortsactivity") ||
            lowerClass.contains("reelitemfragment") ||
            lowerClass.contains("reelwatchfragment") ||
            lowerClass.contains("reelplayerview")) {
            return true
        }

        // Check if YouTube Shorts bottom navigation tab is currently selected
        if (isYouTubeShortsTabSelected(root)) {
            return true
        }

        return hasMatchingPlayerNode(root, youtubePlayerIndicators, youtubeShortsTextKeywords)
    }

    private fun isYouTubeShortsTabSelected(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 25) return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val isShortsTabNode = (viewId.contains("pivot_shorts") || viewId.contains("tab_shorts") ||
                viewId.contains("reel_pivot") ||
                desc.contains("shorts") || desc.contains("শর্টস") ||
                text.equals("shorts", ignoreCase = true) || text.equals("শর্টস", ignoreCase = true))

        if (isShortsTabNode && (node.isSelected || node.isFocused)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isYouTubeShortsTabSelected(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun isFacebookReelsPlayerActive(root: AccessibilityNodeInfo, className: String = ""): Boolean {
        if (className.contains("ReelsViewerActivity", ignoreCase = true) || className.contains("FbShortsViewerFragment", ignoreCase = true)) {
            return true
        }
        return hasMatchingPlayerNode(root, facebookReelsPlayerIndicators, facebookReelsTextKeywords)
    }

    private fun isInstagramReelsPlayerActive(root: AccessibilityNodeInfo, className: String = ""): Boolean {
        if (className.contains("ClipsViewerActivity", ignoreCase = true) || className.contains("ClipsViewerFragment", ignoreCase = true)) {
            return true
        }
        return hasMatchingPlayerNode(root, instagramReelsPlayerIndicators, instagramReelsTextKeywords)
    }

    private fun hasMatchingPlayerNode(
        node: AccessibilityNodeInfo,
        playerIndicators: List<String>,
        textKeywords: List<String> = emptyList(),
        depth: Int = 0
    ): Boolean {
        if (depth > 40) return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (playerIndicators.any { viewId.contains(it) || className.contains(it) }) {
            return true
        }

        if (textKeywords.isNotEmpty() && textKeywords.any { contentDesc.contains(it) || text.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasMatchingPlayerNode(child, playerIndicators, textKeywords, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun recordAdBlockEvent(appLabel: String, packageName: String) {
        val currentTotal = prefs.getInt(PREF_TOTAL_BLOCKED, 0) + 1
        val currentAdsBlocked = prefs.getInt(PREF_ADS_BLOCKED_COUNT, 0) + 1
        val editor = prefs.edit()
            .putInt(PREF_TOTAL_BLOCKED, currentTotal)
            .putInt(PREF_ADS_BLOCKED_COUNT, currentAdsBlocked)

        val now = System.currentTimeMillis()
        editor.putLong(PREF_LAST_BLOCKED_TIME, now)
        editor.putString(PREF_LAST_BLOCKED_APP, appLabel)

        val existingLogs = prefs.getString(PREF_RECENT_LOGS, "") ?: ""
        val newEntry = "$now,$appLabel,$packageName"
        val updatedLogs = (listOf(newEntry) + existingLogs.split(";").filter { it.isNotBlank() })
            .take(20)
            .joinToString(";")

        editor.putString(PREF_RECENT_LOGS, updatedLogs)
        editor.apply()
    }

    private fun recordBlockEvent(appLabel: String, packageName: String) {
        val currentTotal = prefs.getInt(PREF_TOTAL_BLOCKED, 0) + 1
        val editor = prefs.edit().putInt(PREF_TOTAL_BLOCKED, currentTotal)

        when {
            packageName.contains("youtube") -> {
                editor.putInt(PREF_YOUTUBE_BLOCKED, prefs.getInt(PREF_YOUTUBE_BLOCKED, 0) + 1)
            }
            packageName.contains("facebook") -> {
                editor.putInt(PREF_FACEBOOK_BLOCKED, prefs.getInt(PREF_FACEBOOK_BLOCKED, 0) + 1)
            }
            packageName.contains("instagram") -> {
                editor.putInt(PREF_INSTAGRAM_BLOCKED, prefs.getInt(PREF_INSTAGRAM_BLOCKED, 0) + 1)
            }
            else -> {
                editor.putInt(PREF_WEBSITES_BLOCKED, prefs.getInt(PREF_WEBSITES_BLOCKED, 0) + 1)
            }
        }

        val now = System.currentTimeMillis()
        editor.putLong(PREF_LAST_BLOCKED_TIME, now)
        editor.putString(PREF_LAST_BLOCKED_APP, appLabel)

        val existingLogs = prefs.getString(PREF_RECENT_LOGS, "") ?: ""
        val newEntry = "$now,$appLabel,$packageName"
        val updatedLogs = (listOf(newEntry) + existingLogs.split(";").filter { it.isNotBlank() })
            .take(20)
            .joinToString(";")

        editor.putString(PREF_RECENT_LOGS, updatedLogs)
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "shorts_blocker_prefs"
        const val PREF_ENABLED = "master_service_enabled"
        const val PREF_BLOCK_YOUTUBE = "block_youtube"
        const val PREF_BLOCK_FACEBOOK = "block_facebook"
        const val PREF_BLOCK_INSTAGRAM = "block_instagram"
        const val PREF_CUSTOM_APPS = "custom_apps_list"

        const val PREF_BLOCK_ADULT_WEBSITES = "block_adult_websites"
        const val PREF_CUSTOM_WEBSITES = "custom_websites_list"
        const val PREF_REMINDER_MESSAGE = "block_reminder_message"
        const val DEFAULT_REMINDER_MESSAGE = "আল্লাহর দিকে ফিরে আসো"
        const val PREF_PERSISTENT_NOTIFICATION = "persistent_notification_enabled"

        // Ad Blocker preferences
        const val PREF_BLOCK_ADS = "block_ads"
        const val PREF_AUTO_SKIP_VIDEO_ADS = "auto_skip_video_ads"
        const val PREF_BLOCK_POPUP_ADS = "block_popup_ads"
        const val PREF_CUSTOM_AD_FILTERS = "custom_ad_filters_list"
        const val PREF_ADS_BLOCKED_COUNT = "ads_blocked_count"

        const val PREF_TOTAL_BLOCKED = "total_blocked_count"
        const val PREF_YOUTUBE_BLOCKED = "youtube_blocked_count"
        const val PREF_FACEBOOK_BLOCKED = "facebook_blocked_count"
        const val PREF_INSTAGRAM_BLOCKED = "instagram_blocked_count"
        const val PREF_WEBSITES_BLOCKED = "websites_blocked_count"
        const val PREF_LAST_BLOCKED_TIME = "last_blocked_time"
        const val PREF_LAST_BLOCKED_APP = "last_blocked_app"
        const val PREF_RECENT_LOGS = "recent_logs"
        const val PREF_TODAY_DATE = "today_date"

        private const val THROTTLE_INTERVAL_MS = 800L
    }
}

data class TrackedAppInfo(
    val appKey: String,
    val appLabel: String,
    val packageName: String,
    val isCustomApp: Boolean
)
