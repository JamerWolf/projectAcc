package com.example.projectacc

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projectacc.ui.SavedLocationsScreen
import com.example.projectacc.ui.SettingsScreen
import com.example.projectacc.ui.WhatsAppScreen
import com.example.projectacc.ui.theme.ProjectAccTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var isServiceEnabled by mutableStateOf(false)
    private var isNotificationListenerEnabled by mutableStateOf(false)
    private var isOverlayEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Request location permission (foreground + background)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1002)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+, request background location separately
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION), 1003)
            }
        }

        setContent {
            ProjectAccTheme {
                var showSettings by remember { mutableStateOf(false) }
                var showSavedLocations by remember { mutableStateOf(false) }

                when {
                    showSavedLocations -> {
                        SavedLocationsScreen(onBack = { showSavedLocations = false })
                    }
                    showSettings -> {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            onOpenSavedLocations = { showSettings = false; showSavedLocations = true }
                        )
                    }
                    else -> {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                TopAppBar(
                                    title = { Text("Picap Assistant") },
                                    actions = {
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Configuracion"
                                            )
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            val allPermissionsGranted = isServiceEnabled && isOverlayEnabled && isNotificationListenerEnabled

                            if (allPermissionsGranted) {
                                MainScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    isNotificationListenerEnabled = isNotificationListenerEnabled,
                                    onOpenNotificationSettings = { openNotificationListenerSettings() }
                                )
                            } else {
                                ActivationScreen(
                                    isAccessibilityEnabled = isServiceEnabled,
                                    isOverlayEnabled = isOverlayEnabled,
                                    isNotificationListenerEnabled = isNotificationListenerEnabled,
                                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                    onOpenOverlaySettings = { openOverlaySettings() },
                                    onOpenNotificationSettings = { openNotificationListenerSettings() },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabled = checkAccessibilityServiceEnabled()
        isNotificationListenerEnabled = checkNotificationListenerEnabled()
        isOverlayEnabled = Settings.canDrawOverlays(this)
    }

    /**
     * Verifica si el servicio de accesibilidad de esta aplicación está habilitado en los ajustes del sistema.
     * 
     * @return true si el servicio está habilitado, false en caso contrario.
     */
    private fun checkAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.name == MyAccessibilityService::class.java.name) {
                return true
            }
        }
        return false
    }

    /**
     * Abre la pantalla de configuración de accesibilidad del sistema para que el usuario pueda activar el servicio.
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    /**
     * Verifica si el servicio de escucha de notificaciones de esta aplicación está habilitado.
     *
     * @return true si el servicio está habilitado, false en caso contrario.
     */
    private fun checkNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    /**
     * Abre la pantalla de configuración de acceso a notificaciones del sistema
     * para que el usuario pueda habilitar el servicio NotificationInterceptorService.
     */
    private fun openNotificationListenerSettings() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    /**
     * Abre la pantalla de configuración de overlay (draw over other apps)
     * para que el usuario pueda habilitar los popups flotantes.
     */
    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}

/**
 * Pantalla que muestra los permisos requeridos y su estado.
 * El usuario debe activar todos antes de usar la app.
 */
@Composable
fun ActivationScreen(
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    isNotificationListenerEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Permisos Requeridos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Activa los permisos para usar la app",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Accessibility permission
        PermissionCard(
            title = "1. Servicio de Accesibilidad",
            description = "Permite escuchar eventos de Picap y WhatsApp para detectar servicios automaticamente.",
            isEnabled = isAccessibilityEnabled,
            onActivate = onOpenAccessibilitySettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Overlay permission
        PermissionCard(
            title = "2. Ventanas Flotantes",
            description = "Permite mostrar popups encima de otras apps (WhatsApp) para aceptar servicios rapidamente.",
            isEnabled = isOverlayEnabled,
            onActivate = onOpenOverlaySettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Notification Listener permission
        PermissionCard(
            title = "3. Escuchador de Notificaciones",
            description = "Permite detectar notificaciones de Picap y mensajes de WhatsApp para automatizar respuestas.",
            isEnabled = isNotificationListenerEnabled,
            onActivate = onOpenNotificationSettings
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isAccessibilityEnabled && isOverlayEnabled && isNotificationListenerEnabled) {
            Text(
                text = "Todos los permisos activados ✓",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isEnabled) "✅ Activado" else "❌ No activado",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (!isEnabled) {
                Button(onClick = onActivate) {
                    Text(text = "Activar")
                }
            }
        }
    }
}

/**
 * Main screen with tabs: Picap and WhatsApp.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isNotificationListenerEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Picap", "WhatsApp")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(text = title) }
                )
            }
        }

        when (selectedTab) {
            0 -> PicapContent(
                isNotificationListenerEnabled = isNotificationListenerEnabled,
                onOpenNotificationSettings = onOpenNotificationSettings
            )
            1 -> WhatsAppScreen()
        }
    }
}

/**
 * Picap tab content - the original ActiveScreen logic.
 */
@Composable
fun PicapContent(
    isNotificationListenerEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit
) {
    val order by OrderStateManager.currentOrder.collectAsState()
    val isAutoClickEnabled by OrderStateManager.isAutoClickEnabled.collectAsState()
    val isNotificationClickEnabled by OrderStateManager.isNotificationClickEnabled.collectAsState()
    val scannedServiceIds by OrderStateManager.scannedServiceIds.collectAsState()
    val autoAcceptMaxKm by OrderStateManager.autoAcceptMaxKm.collectAsState()
    val isAutoAcceptByKmEnabled by OrderStateManager.isAutoAcceptByKmEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Servicio Activo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Switch de Auto-Clic
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Auto-Capturar orden",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAutoClickEnabled) "Buscando ordenes..." else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "IDs en memoria: ${if (scannedServiceIds.isEmpty()) "ninguno" else scannedServiceIds.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = isAutoClickEnabled,
                onCheckedChange = { OrderStateManager.setAutoClickEnabled(it) }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Switch de Auto-Clic por Notificacion
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Auto-Clic por Notificacion",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isNotificationClickEnabled) "Esperando notificacion..." else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = isNotificationClickEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !isNotificationListenerEnabled) {
                        onOpenNotificationSettings()
                    } else {
                        OrderStateManager.setNotificationClickEnabled(enabled)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        // Switch de Auto-Aceptar por distancia
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Aceptar por distancia",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAutoAcceptByKmEnabled) {
                        if (autoAcceptMaxKm >= 5.0) "Cualquier distancia (Sin limite)"
                        else "Aceptar si esta a menos de ${String.format("%.1f", autoAcceptMaxKm)} km"
                    } else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isAutoAcceptByKmEnabled,
                onCheckedChange = { OrderStateManager.setAutoAcceptByKmEnabled(it) }
            )
        }

        if (isAutoAcceptByKmEnabled) {
            Slider(
                value = autoAcceptMaxKm.toFloat(),
                onValueChange = { OrderStateManager.setAutoAcceptMaxKm(it.toDouble()) },
                valueRange = 0.5f..5.0f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Orden o mensaje de espera
        if (order != null) {
            OrderCard(
                orderDisplay = OrderDisplay.Picap(order!!),
                onDismiss = { OrderStateManager.clearOrder() }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Esperando nuevas ordenes...")
            }
        }
    }
}
