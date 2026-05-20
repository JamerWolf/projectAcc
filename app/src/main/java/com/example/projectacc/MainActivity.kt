package com.example.projectacc

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projectacc.ui.theme.ProjectAccTheme

class MainActivity : ComponentActivity() {
    private var isServiceEnabled by mutableStateOf(false)
    private var isNotificationListenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjectAccTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isServiceEnabled) {
                        ActiveScreen(
                            modifier = Modifier.padding(innerPadding),
                            isNotificationListenerEnabled = isNotificationListenerEnabled,
                            onOpenNotificationSettings = { openNotificationListenerSettings() }
                        )
                    } else {
                        ActivationScreen(
                            onActivateClick = { openAccessibilitySettings() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabled = checkAccessibilityServiceEnabled()
        isNotificationListenerEnabled = checkNotificationListenerEnabled()
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
}

/**
 * Pantalla que se muestra cuando el servicio de accesibilidad no está activo.
 * 
 * @param onActivateClick Acción a ejecutar cuando el usuario pulsa el botón de activar.
 * @param modifier Modificador para personalizar el diseño.
 */
@Composable
fun ActivationScreen(onActivateClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "El servicio de accesibilidad no está activo.", modifier = Modifier.padding(16.dp))
        Button(onClick = onActivateClick) {
            Text(text = "Activar en Ajustes")
        }
    }
}

/**
 * Pantalla principal cuando el servicio está activo.
 * Observa el StateFlow de OrderStateManager para mostrar la orden actual o un mensaje de espera.
 * Incluye un Switch para activar/desactivar el modo Auto-Clic (Francotirador).
 *
 * @param modifier Modificador para personalizar el diseño.
 */
@Composable
fun ActiveScreen(
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Título
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

        // Switch de Auto-Clic por Notificación
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Auto-Clic por Notificación",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isNotificationClickEnabled) "Esperando notificación..." else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = isNotificationClickEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !isNotificationListenerEnabled) {
                        // No tiene permiso: abrir ajustes de notificaciones
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

        // Switch de Auto-Aceptar por distancia (Condición 3)
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
                        if (autoAcceptMaxKm >= 5.0) "Cualquier distancia (Sin límite)"
                        else "Aceptar si está a menos de ${String.format("%.1f", autoAcceptMaxKm)} km"
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

        // Slider: solo se muestra cuando el switch está activo
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
                order = order!!,
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
