package com.markvoronin.reelsonthego

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.markvoronin.reelsonthego.data.PreferencesRepository
import com.markvoronin.reelsonthego.service.MediaButtonService
import com.markvoronin.reelsonthego.service.ReelsAccessibilityService
import com.markvoronin.reelsonthego.ui.theme.ReelsWhileDrivingTheme
import com.markvoronin.reelsonthego.util.Logger
import com.markvoronin.reelsonthego.util.ShizukuManager

class MainActivity : ComponentActivity() {

    private lateinit var prefsRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefsRepository = PreferencesRepository(this)
        ShizukuManager.init()

        setContent {
            ReelsWhileDrivingTheme {
                MainScreen(prefsRepository = prefsRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(prefsRepository: PreferencesRepository) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context, ReelsAccessibilityService::class.java)) }
    var isMediaServiceRunning by remember { mutableStateOf(MediaButtonService.isRunning) }
    var isMasterEnabled by remember { mutableStateOf(prefsRepository.isServiceEnabled) }
    var isGlobalSwipeEnabled by remember { mutableStateOf(prefsRepository.isGlobalSwipeEnabled) }
    var isPrevDoubleTap by remember { mutableStateOf(prefsRepository.isPrevButtonDoubleTap) }
    var selectedSwipeDuration by remember { mutableLongStateOf(prefsRepository.swipeDurationMs) }
    var enabledPackages by remember { mutableStateOf(prefsRepository.enabledPackages) }

    var shizukuAvailable by remember { mutableStateOf(ShizukuManager.isAvailable) }
    var shizukuGranted by remember { mutableStateOf(ShizukuManager.isGranted) }

    val logs by Logger.logs.collectAsState()

    // Runtime permission launcher for notifications (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            MediaButtonService.startService(context)
            isMediaServiceRunning = true
        } else {
            Toast.makeText(context, "Notification permission required for background listener notification", Toast.LENGTH_LONG).show()
        }
    }

    // Re-check accessibility service status when app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context, ReelsAccessibilityService::class.java)
                isMediaServiceRunning = MediaButtonService.isRunning
                shizukuAvailable = ShizukuManager.isAvailable
                shizukuGranted = ShizukuManager.isGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reels While Driving") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Steering Wheel Swiping",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Intercept Next/Prev media buttons to scroll reels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMasterEnabled,
                        onCheckedChange = { checked ->
                            isMasterEnabled = checked
                            prefsRepository.isServiceEnabled = checked
                        }
                    )
                }
            }

            // Remap Previous Button Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remap Prev Button to Double-Tap (Like)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Single pressing PREV button double-taps screen to like reels instead of scrolling back",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPrevDoubleTap,
                        onCheckedChange = { checked ->
                            isPrevDoubleTap = checked
                            prefsRepository.isPrevButtonDoubleTap = checked
                        }
                    )
                }
            }

            // Swipe Speed Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Swipe Speed / Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Adjust how fast the scroll gesture fling executes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PreferencesRepository.SWIPE_SPEED_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = selectedSwipeDuration == option.durationMs,
                                onClick = {
                                    selectedSwipeDuration = option.durationMs
                                    prefsRepository.swipeDurationMs = option.durationMs
                                    Toast.makeText(context, "Set swipe duration to ${option.durationMs}ms", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(option.label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Accessibility Status Card
            val accessCardContainerColor = if (isAccessibilityEnabled) {
                if (isDark) Color(0xFF1C3A27) else Color(0xFFE8F5E9)
            } else {
                if (isDark) Color(0xFF3E1E1E) else Color(0xFFFFEBEE)
            }

            val accessCardTextColor = if (isAccessibilityEnabled) {
                if (isDark) Color(0xFFE8F5E9) else Color(0xFF1B5E20)
            } else {
                if (isDark) Color(0xFFFFEBEE) else Color(0xFF7A1C1C)
            }

            val accessCardStatusColor = if (isAccessibilityEnabled) {
                if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
            } else {
                if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
            }

            val accessButtonContainerColor = if (isAccessibilityEnabled) {
                if (isDark) Color(0xFF2E7D32) else Color(0xFF388E3C)
            } else {
                if (isDark) Color(0xFFC62828) else Color(0xFFD32F2F)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = accessCardContainerColor,
                    contentColor = accessCardTextColor
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accessibility Permission",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accessCardTextColor
                        )
                        Text(
                            text = if (isAccessibilityEnabled) "ACTIVE" else "INACTIVE",
                            color = accessCardStatusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Required to dispatch swipe gestures on TikTok / Reels when media buttons are pressed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accessCardTextColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accessButtonContainerColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(if (isAccessibilityEnabled) "Accessibility Settings" else "Grant Accessibility Permission")
                    }
                }
            }

            // Bluetooth Media Session Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth MediaSession Listener",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Foreground service to capture steering wheel commands on car head units",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMediaServiceRunning,
                            onCheckedChange = { running ->
                                if (running) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                            MediaButtonService.startService(context)
                                            isMediaServiceRunning = true
                                        } else {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        MediaButtonService.startService(context)
                                        isMediaServiceRunning = true
                                    }
                                } else {
                                    MediaButtonService.stopService(context)
                                    isMediaServiceRunning = false
                                }
                            }
                        )
                    }
                }
            }

            // Shizuku Integration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shizuku / ADB Integration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (shizukuGranted) "ACTIVE" else if (shizukuAvailable) "AVAILABLE" else "OFFLINE",
                            color = if (shizukuGranted) Color(0xFF2E7D32) else if (shizukuAvailable) Color(0xFFF57C00) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Provides system-level touch injection and bypasses OEM accessibility blocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (shizukuAvailable && !shizukuGranted) {
                        Button(
                            onClick = {
                                ShizukuManager.requestPermission()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Shizuku Permission")
                        }
                    } else if (shizukuGranted) {
                        OutlinedButton(
                            onClick = {
                                ShizukuManager.grantMediaKeyPermissions(context)
                                Toast.makeText(context, "Granted System MediaKey permissions via Shizuku!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant System MediaKey Permissions (ADB)")
                        }
                    } else {
                        Text(
                            text = "Shizuku app is not running. Install and start Shizuku via Wireless Debugging if Accessibility is blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Target Apps Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Target Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose which apps convert steering wheel buttons into swipes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Global Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Global Swiping (All Apps)")
                        Switch(
                            checked = isGlobalSwipeEnabled,
                            onCheckedChange = { checked ->
                                isGlobalSwipeEnabled = checked
                                prefsRepository.isGlobalSwipeEnabled = checked
                            }
                        )
                    }

                    if (!isGlobalSwipeEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        PreferencesRepository.SUPPORTED_APPS.forEach { app ->
                            val isAppEnabled = enabledPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(app.displayName)
                                Switch(
                                    checked = isAppEnabled,
                                    onCheckedChange = { checked ->
                                        prefsRepository.togglePackage(app.packageName, checked)
                                        enabledPackages = prefsRepository.enabledPackages
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Live Debug Logs Console Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Key & Event Console",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { Logger.clear() }
                        ) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color.Black)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = "Waiting for button presses or events...\nPress steering wheel buttons to see keycodes in real time.",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        } else {
                            logs.forEach { entry ->
                                Text(
                                    text = "[${entry.timestamp}] ${entry.message}",
                                    color = if (entry.isError) Color(0xFFFF8A80) else Color(0xFFB9F6CA),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Test Gestures Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Test Gesture Commands",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val service = ReelsAccessibilityService.getInstance()
                                    if (service != null) {
                                        service.swipeUp(force = true)
                                        Toast.makeText(context, "Dispatched Swipe Up (Next)", Toast.LENGTH_SHORT).show()
                                    } else if (ShizukuManager.isGranted) {
                                        val displayMetrics = context.resources.displayMetrics
                                        ShizukuManager.swipeUp(displayMetrics.widthPixels, displayMetrics.heightPixels, selectedSwipeDuration)
                                        Toast.makeText(context, "Dispatched Swipe Up via Shizuku", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Enable Accessibility or Shizuku first!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Swipe Up (Next)")
                            }

                            OutlinedButton(
                                onClick = {
                                    val service = ReelsAccessibilityService.getInstance()
                                    if (service != null) {
                                        service.swipeDown(force = true)
                                        Toast.makeText(context, "Dispatched Swipe Down (Prev)", Toast.LENGTH_SHORT).show()
                                    } else if (ShizukuManager.isGranted) {
                                        val displayMetrics = context.resources.displayMetrics
                                        ShizukuManager.swipeDown(displayMetrics.widthPixels, displayMetrics.heightPixels, selectedSwipeDuration)
                                        Toast.makeText(context, "Dispatched Swipe Down via Shizuku", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Enable Accessibility or Shizuku first!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Swipe Down (Prev)")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val service = ReelsAccessibilityService.getInstance()
                                if (service != null) {
                                    service.doubleTap(force = true)
                                    Toast.makeText(context, "Dispatched Double Tap (Like)", Toast.LENGTH_SHORT).show()
                                } else if (ShizukuManager.isGranted) {
                                    val displayMetrics = context.resources.displayMetrics
                                    ShizukuManager.doubleTap(displayMetrics.widthPixels, displayMetrics.heightPixels)
                                    Toast.makeText(context, "Dispatched Double Tap via Shizuku", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Enable Accessibility or Shizuku first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Double Tap (Like Reel)")
                        }
                    }
                }
            }

            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "How to use",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Grant Accessibility permission above.\n" +
                                "2. Enable the Bluetooth MediaSession listener.\n" +
                                "3. Connect your phone to your car's Bluetooth.\n" +
                                "4. Open Instagram Reels, TikTok, or YouTube Shorts.\n" +
                                "5. Press Next / Prev buttons on your steering wheel to scroll!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val expectedComponentName = ComponentName(context, serviceClass)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)

    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}
