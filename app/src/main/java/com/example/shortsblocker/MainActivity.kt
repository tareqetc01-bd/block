package com.example.shortsblocker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shortsblocker.ui.components.AppAndShortsDailyLimitCard
import com.example.shortsblocker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: ShortsBlockerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ShortsBlockerTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                ShortsBlockerApp(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsBlockerApp(
    uiState: ShortsBlockerUiState,
    viewModel: ShortsBlockerViewModel,
    onOpenAccessibilitySettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showAddWebsiteDialog by remember { mutableStateOf(false) }
    var showAddAdFilterDialog by remember { mutableStateOf(false) }
    var showReminderEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = IndigoPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Shield Icon",
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Blocker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Shorts, Reels & Ad Protection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Switch(
                            checked = uiState.isMasterEnabled,
                            onCheckedChange = { viewModel.setMasterEnabled(it) },
                            modifier = Modifier.testTag("master_switch")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Block, contentDescription = "Blockers") },
                    label = { Text("Blockers") },
                    modifier = Modifier.testTag("nav_blockers")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Limits") },
                    label = { Text("Limits") },
                    modifier = Modifier.testTag("nav_limits")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Logs") },
                    label = { Text("Logs") },
                    modifier = Modifier.testTag("nav_logs")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                // Accessibility Banner Check
                item {
                    AccessibilityStatusCard(
                        isActive = uiState.isAccessibilityServiceActive,
                        onEnableClick = onOpenAccessibilitySettings
                    )
                }

                when (selectedTab) {
                    0 -> { // Dashboard Tab
                        item {
                            OverviewStatsSection(
                                uiState = uiState,
                                onResetStats = { viewModel.resetStats() }
                            )
                        }

                        item {
                            AppAndShortsDailyLimitCard(
                                uiState = uiState,
                                onSetAppSpecificLimits = { key, appLimit, shortsLimit ->
                                    viewModel.setAppSpecificLimits(key, appLimit, shortsLimit)
                                },
                                onResetAppUsage = { viewModel.resetAppUsage(it) },
                                onResetShortsUsage = { viewModel.resetShortsUsage(it) },
                                onToggleCustomApp = { pkg, enabled -> viewModel.toggleCustomApp(pkg, enabled) },
                                onRemoveCustomApp = { pkg -> viewModel.removeCustomApp(pkg) }
                            )
                        }

                        item {
                            QuickTogglesCard(
                                uiState = uiState,
                                onToggleYouTube = { viewModel.setBlockYouTube(it) },
                                onToggleFacebook = { viewModel.setBlockFacebook(it) },
                                onToggleInstagram = { viewModel.setBlockInstagram(it) },
                                onToggleAdultWebsites = { viewModel.setBlockAdultWebsites(it) },
                                onToggleAds = { viewModel.setBlockAds(it) }
                            )
                        }
                    }

                    1 -> { // Blockers Tab
                        item {
                            PlatformBlockersCard(
                                uiState = uiState,
                                onToggleYouTube = { viewModel.setBlockYouTube(it) },
                                onToggleFacebook = { viewModel.setBlockFacebook(it) },
                                onToggleInstagram = { viewModel.setBlockInstagram(it) }
                            )
                        }

                        item {
                            AdultWebsiteBlockerCard(
                                uiState = uiState,
                                onToggleBlockAdult = { viewModel.setBlockAdultWebsites(it) },
                                onAddWebsiteClick = { showAddWebsiteDialog = true },
                                onToggleWebsite = { domain, enabled -> viewModel.toggleCustomWebsite(domain, enabled) },
                                onDeleteWebsite = { domain -> viewModel.removeCustomWebsite(domain) },
                                onEditReminderClick = { showReminderEditDialog = true }
                            )
                        }

                        item {
                            AdBlockerCard(
                                uiState = uiState,
                                onToggleBlockAds = { viewModel.setBlockAds(it) },
                                onToggleAutoSkip = { viewModel.setAutoSkipVideoAds(it) },
                                onTogglePopupAds = { viewModel.setBlockPopupAds(it) },
                                onAddAdFilterClick = { showAddAdFilterDialog = true },
                                onToggleAdFilter = { domain, enabled -> viewModel.toggleCustomAdFilter(domain, enabled) },
                                onDeleteAdFilter = { domain -> viewModel.removeCustomAdFilter(domain) }
                            )
                        }
                    }

                    2 -> { // Limits Tab
                        item {
                            AppAndShortsDailyLimitCard(
                                uiState = uiState,
                                onSetAppSpecificLimits = { key, appLimit, shortsLimit ->
                                    viewModel.setAppSpecificLimits(key, appLimit, shortsLimit)
                                },
                                onResetAppUsage = { viewModel.resetAppUsage(it) },
                                onResetShortsUsage = { viewModel.resetShortsUsage(it) },
                                onToggleCustomApp = { pkg, enabled -> viewModel.toggleCustomApp(pkg, enabled) },
                                onRemoveCustomApp = { pkg -> viewModel.removeCustomApp(pkg) }
                            )
                        }

                        item {
                            CustomAppsSection(
                                customApps = uiState.customApps,
                                onAddAppClick = { showAddAppDialog = true },
                                onToggleApp = { pkg, enabled -> viewModel.toggleCustomApp(pkg, enabled) },
                                onDeleteApp = { pkg -> viewModel.removeCustomApp(pkg) }
                            )
                        }
                    }

                    3 -> { // Logs Tab
                        item {
                            LogsHeaderCard(
                                totalBlocked = uiState.totalBlockedCount,
                                onClearLogs = { viewModel.resetStats() }
                            )
                        }

                        if (uiState.recentEvents.isEmpty()) {
                            item {
                                EmptyLogsCard()
                            }
                        } else {
                            items(uiState.recentEvents) { event ->
                                LogItemCard(event = event)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Dialogs
    if (showAddAppDialog) {
        AddAppDialog(
            installedApps = uiState.installedApps,
            isLoading = uiState.isLoadingApps,
            onDismiss = { showAddAppDialog = false },
            onAppSelected = { appName, pkgName ->
                viewModel.addCustomApp(name = appName, packageName = pkgName, limitMinutes = 30)
                showAddAppDialog = false
            }
        )
    }

    if (showAddWebsiteDialog) {
        AddWebsiteDialog(
            title = "ওয়েবসাইট যুক্ত করুন (Add Website)",
            placeholder = "example.com",
            onDismiss = { showAddWebsiteDialog = false },
            onConfirm = { domain ->
                viewModel.addCustomWebsite(domain)
                showAddWebsiteDialog = false
            }
        )
    }

    if (showAddAdFilterDialog) {
        AddWebsiteDialog(
            title = "অ্যাড ডোমেইন ফিল্টার (Add Ad Filter)",
            placeholder = "ads.example.com",
            onDismiss = { showAddAdFilterDialog = false },
            onConfirm = { domain ->
                viewModel.addCustomAdFilter(domain)
                showAddAdFilterDialog = false
            }
        )
    }

    if (showReminderEditDialog) {
        EditReminderDialog(
            currentMessage = uiState.reminderMessage,
            onDismiss = { showReminderEditDialog = false },
            onSave = { message ->
                viewModel.setReminderMessage(message)
                showReminderEditDialog = false
            }
        )
    }
}

@Composable
fun AccessibilityStatusCard(
    isActive: Boolean,
    onEnableClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) EmeraldSuccess.copy(alpha = 0.12f) else RoseError.copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) EmeraldSuccess.copy(alpha = 0.35f) else RoseError.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("accessibility_status_card")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isActive) EmeraldSuccess else RoseError,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "সুরক্ষা সক্রিয় রয়েছে (Active)" else "সুরক্ষা নিষ্ক্রিয় (Permission Needed)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) EmeraldSuccess else RoseError
                )
                Text(
                    text = if (isActive)
                        "Shorts, Reels এবং বিজ্ঞাপনের বিরুদ্ধে পর্যবেক্ষণ চলছে।"
                    else
                        "Shorts ও রিলস ব্লক করতে Accessibility সার্ভিস চালু করুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isActive) {
                Button(
                    onClick = onEnableClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_enable_accessibility")
                ) {
                    Text("চালু করুন", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OverviewStatsSection(
    uiState: ShortsBlockerUiState,
    onResetStats: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = IndigoPrimary
                    )
                    Text(
                        text = "ব্লকিং পরিসংখ্যান (Statistics)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onResetStats) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Stats",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBadge(
                    title = "মোট ব্লক",
                    count = uiState.totalBlockedCount,
                    color = IndigoPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = "YouTube",
                    count = uiState.youtubeBlockedCount,
                    color = RoseError,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = "Facebook",
                    count = uiState.facebookBlockedCount,
                    color = Color(0xFF1877F2),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBadge(
                    title = "Instagram",
                    count = uiState.instagramBlockedCount,
                    color = Color(0xFFE1306C),
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = "ওয়েবসাইট",
                    count = uiState.websiteBlockedCount,
                    color = VioletSecondary,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    title = "বিজ্ঞাপন (Ads)",
                    count = uiState.adsBlockedCount,
                    color = EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatBadge(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickTogglesCard(
    uiState: ShortsBlockerUiState,
    onToggleYouTube: (Boolean) -> Unit,
    onToggleFacebook: (Boolean) -> Unit,
    onToggleInstagram: (Boolean) -> Unit,
    onToggleAdultWebsites: (Boolean) -> Unit,
    onToggleAds: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "দ্রুত সুরক্ষা সেটিংস (Quick Protections)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            ToggleRowItem(
                title = "YouTube Shorts ব্লক করুন",
                subtitle = "শর্টস ফিড ও প্লেয়ার স্বয়ংক্রিয়ভাবে বন্ধ করবে",
                checked = uiState.blockYouTube,
                onCheckedChange = onToggleYouTube,
                icon = Icons.Default.PlayArrow,
                iconColor = RoseError
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ToggleRowItem(
                title = "Facebook Reels ব্লক করুন",
                subtitle = "ফেসবুক রিলস ভিডিও ও ট্যাব বন্ধ রাখবে",
                checked = uiState.blockFacebook,
                onCheckedChange = onToggleFacebook,
                icon = Icons.Default.VideoLibrary,
                iconColor = Color(0xFF1877F2)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ToggleRowItem(
                title = "Instagram Reels ব্লক করুন",
                subtitle = "ইনস্টাগ্রাম রিলস ও ক্লিপস ভিউয়ার প্রতিহত করবে",
                checked = uiState.blockInstagram,
                onCheckedChange = onToggleInstagram,
                icon = Icons.Default.CameraAlt,
                iconColor = Color(0xFFE1306C)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ToggleRowItem(
                title = "অশ্লীল সাইট ব্লক (Adult Websites)",
                subtitle = "পর্নোগ্রাফি ও ক্ষতিকর ওয়েবসাইট ব্রাউজারে ব্লক করবে",
                checked = uiState.blockAdultWebsites,
                onCheckedChange = onToggleAdultWebsites,
                icon = Icons.Default.Lock,
                iconColor = RoseError
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            ToggleRowItem(
                title = "বিজ্ঞাপন স্কিপ ও ব্লক (Ad Blocker)",
                subtitle = "ভিডিও বিজ্ঞাপন স্বয়ংক্রিয় স্কিপ ও পপআপ বন্ধ করবে",
                checked = uiState.blockAds,
                onCheckedChange = onToggleAds,
                icon = Icons.Default.Shield,
                iconColor = EmeraldSuccess
            )
        }
    }
}

@Composable
fun PlatformBlockersCard(
    uiState: ShortsBlockerUiState,
    onToggleYouTube: (Boolean) -> Unit,
    onToggleFacebook: (Boolean) -> Unit,
    onToggleInstagram: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = RoseError
                )
                Text(
                    text = "Shorts & Reels ব্লকার",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ToggleRowItem(
                title = "YouTube Shorts",
                subtitle = "শর্টস স্ক্রল ও শর্টস ট্যাব পুরোপুরি বন্ধ থাকবে",
                checked = uiState.blockYouTube,
                onCheckedChange = onToggleYouTube,
                icon = Icons.Default.PlayArrow,
                iconColor = RoseError
            )

            ToggleRowItem(
                title = "Facebook Reels",
                subtitle = "ফেসবুক ওয়াচ ও রিলস ফিড স্বয়ংক্রিয়ভাবে ব্লক হবে",
                checked = uiState.blockFacebook,
                onCheckedChange = onToggleFacebook,
                icon = Icons.Default.VideoLibrary,
                iconColor = Color(0xFF1877F2)
            )

            ToggleRowItem(
                title = "Instagram Reels",
                subtitle = "ইনস্টাগ্রাম রিলস টগল ও ফুলস্ক্রিন রিলস প্রতিহত হবে",
                checked = uiState.blockInstagram,
                onCheckedChange = onToggleInstagram,
                icon = Icons.Default.CameraAlt,
                iconColor = Color(0xFFE1306C)
            )
        }
    }
}

@Composable
fun AdultWebsiteBlockerCard(
    uiState: ShortsBlockerUiState,
    onToggleBlockAdult: (Boolean) -> Unit,
    onAddWebsiteClick: () -> Unit,
    onToggleWebsite: (String, Boolean) -> Unit,
    onDeleteWebsite: (String) -> Unit,
    onEditReminderClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = RoseError
                )
                Text(
                    text = "অশ্লীল সাইট ও ডোমেইন ব্লকার",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ToggleRowItem(
                title = "অশ্লীল ওয়েবসাইট স্বয়ংক্রিয় ব্লকিং",
                subtitle = "Chrome, Firefox, Opera সহ সকল ব্রাউজারে পর্ন সাইট বন্ধ করবে",
                checked = uiState.blockAdultWebsites,
                onCheckedChange = onToggleBlockAdult,
                icon = Icons.Default.Security,
                iconColor = RoseError
            )

            // Islamic Reminder Message Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccess.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditReminderClick() }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ব্লক করার সময় দেখানো বার্তা (Reminder):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "\"${uiState.reminderMessage}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Reminder",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Custom blocked websites section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "কাস্টম ব্লক ডোমেইন (${uiState.customBlockedWebsites.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddWebsiteClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("যুক্ত করুন")
                }
            }

            if (uiState.customBlockedWebsites.isEmpty()) {
                Text(
                    text = "কোনো কাস্টম ওয়েবসাইট যুক্ত করা হয়নি। যে কোনো ওয়েবসাইট ব্লক করতে উপরে 'যুক্ত করুন' বাটনে চাপুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.customBlockedWebsites.forEach { website ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = website.domain,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = website.isEnabled,
                                onCheckedChange = { onToggleWebsite(website.domain, it) }
                            )
                            IconButton(onClick = { onDeleteWebsite(website.domain) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = RoseError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdBlockerCard(
    uiState: ShortsBlockerUiState,
    onToggleBlockAds: (Boolean) -> Unit,
    onToggleAutoSkip: (Boolean) -> Unit,
    onTogglePopupAds: (Boolean) -> Unit,
    onAddAdFilterClick: () -> Unit,
    onToggleAdFilter: (String, Boolean) -> Unit,
    onDeleteAdFilter: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = EmeraldSuccess
                )
                Text(
                    text = "স্মার্ট বিজ্ঞাপন ব্লকার (Ad Blocker)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ToggleRowItem(
                title = "বিজ্ঞাপন ব্লকিং চালু রাখুন",
                subtitle = "বিজ্ঞাপন শনাক্ত ও বন্ধ রাখবে",
                checked = uiState.blockAds,
                onCheckedChange = onToggleBlockAds,
                icon = Icons.Default.CheckCircle,
                iconColor = EmeraldSuccess
            )

            ToggleRowItem(
                title = "অটো-স্কিপ ভিডিও বিজ্ঞাপন (Auto Skip)",
                subtitle = "YouTube ভিডিওতে Skip Ad বাটন স্বয়ংক্রিয়ভাবে ক্লিক করবে",
                checked = uiState.autoSkipVideoAds,
                onCheckedChange = onToggleAutoSkip,
                icon = Icons.Default.FastForward,
                iconColor = IndigoPrimary
            )

            ToggleRowItem(
                title = "পপআপ বিজ্ঞাপন বন্ধ (Close Popups)",
                subtitle = "অ্যাপ ও ব্রাউজারের বিরক্তিকর পপআপ বন্ধ করবে",
                checked = uiState.blockPopupAds,
                onCheckedChange = onTogglePopupAds,
                icon = Icons.Default.Close,
                iconColor = AmberWarning
            )

            // Custom Ad Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "কাস্টম অ্যাড ফিল্টার (${uiState.customAdFilters.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddAdFilterClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ফিল্টার যোগ")
                }
            }

            if (uiState.customAdFilters.isNotEmpty()) {
                uiState.customAdFilters.forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = filter.domain,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = filter.isEnabled,
                                onCheckedChange = { onToggleAdFilter(filter.domain, it) }
                            )
                            IconButton(onClick = { onDeleteAdFilter(filter.domain) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = RoseError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomAppsSection(
    customApps: List<CustomApp>,
    onAddAppClick: () -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onDeleteApp: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = IndigoPrimary
                    )
                    Text(
                        text = "অন্যান্য অ্যাপ মনিটরিং (${customApps.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onAddAppClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("অ্যাপ যোগ")
                }
            }

            if (customApps.isEmpty()) {
                Text(
                    text = "কোনো কাস্টম অ্যাপ যুক্ত নেই। যেকোনো আসক্তিকর অ্যাপের ব্যবহারের সময়সীমা নির্দিষ্ট করতে উপরে 'অ্যাপ যোগ' বাটনে চাপুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                customApps.forEach { app ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val limitText = if (app.dailyLimitMinutes > 0)
                                    "দৈনিক লিমিট: ${app.dailyLimitMinutes} মিনিট"
                                else
                                    "লিমিট: আনলিমিটেড"
                                Text(
                                    text = "$limitText | ব্যবহৃত: ${app.todayUsedSeconds / 60} মিনিট",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = app.isEnabled,
                                    onCheckedChange = { onToggleApp(app.packageName, it) }
                                )
                                IconButton(onClick = { onDeleteApp(app.packageName) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = RoseError,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogsHeaderCard(
    totalBlocked: Int,
    onClearLogs: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ব্লকিং হিস্টোরি ও লগ (History)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "সর্বমোট ব্লক করা ইভেন্ট: $totalBlocked টি",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(onClick = onClearLogs) {
                Text("মুছে ফেলুন")
            }
        }
    }
}

@Composable
fun EmptyLogsCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldSuccess,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "কোনো সাম্প্রতিক ব্লক নেই",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "আপনি সুরক্ষিত রয়েছেন। Shorts বা রিলস খোলা হলে এখানে হিস্টোরি জমা হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LogItemCard(event: BlockEvent) {
    val dateFormat = remember { SimpleDateFormat("hh:mm:ss a, dd MMM", Locale.getDefault()) }
    val formattedTime = remember(event.timestamp) { dateFormat.format(Date(event.timestamp)) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = RoseError.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = RoseError,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${event.appName} ব্লক করা হয়েছে",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ToggleRowItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun AddAppDialog(
    installedApps: List<InstalledAppItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAppSelected: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "অ্যাপ নির্বাচন করুন", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("অ্যাপ খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "কোনো অ্যাপ পাওয়া যায়নি",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps) { app ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppSelected(app.appName, app.packageName) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = IndigoPrimary
                                    )
                                    Column {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun AddWebsiteDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var domainInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ডোমেইন বা সাইটের নাম লিখুন:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (domainInput.isNotBlank()) {
                        onConfirm(domainInput.trim())
                    }
                }
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun EditReminderDialog(
    currentMessage: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var messageInput by remember { mutableStateOf(currentMessage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "রিমাইন্ডার বার্তা পরিবর্তন", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Shorts বা অশ্লীল সাইট ব্লক করার পর স্ক্রিনে কোন বার্তাটি দেখতে চান?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("আল্লাহর দিকে ফিরে আসো") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(messageInput.trim()) }) {
                Text("সেভ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
