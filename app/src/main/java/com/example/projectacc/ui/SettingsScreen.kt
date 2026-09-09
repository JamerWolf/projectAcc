package com.example.projectacc.ui

import androidx.compose.foundation.background
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.projectacc.OrderStateManager
import com.example.projectacc.update.UpdateChecker

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSavedLocations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vehiclePlate by OrderStateManager.vehiclePlate.collectAsState()
    var plateInput by remember { mutableStateOf(vehiclePlate) }
    val isPopupSoundEnabled by OrderStateManager.isPopupSoundEnabled.collectAsState()

    // Auto-accept settings
    val autoAcceptMinGanancia1 by OrderStateManager.autoAcceptMinGanancia1.collectAsState()
    val autoAcceptMinGanancia2 by OrderStateManager.autoAcceptMinGanancia2.collectAsState()
    val autoAcceptMaxKmCond2 by OrderStateManager.autoAcceptMaxKmCond2.collectAsState()
    val isAutoAcceptByKmEnabled by OrderStateManager.isAutoAcceptByKmEnabled.collectAsState()
    val autoAcceptMaxKm by OrderStateManager.autoAcceptMaxKm.collectAsState()

    var ganancia1Input by remember { mutableStateOf(autoAcceptMinGanancia1.toInt().toString()) }
    var ganancia2Input by remember { mutableStateOf(autoAcceptMinGanancia2.toInt().toString()) }
    var kmCond2Input by remember { mutableStateOf(autoAcceptMaxKmCond2.toString()) }
    var kmUmbralInput by remember { mutableStateOf(autoAcceptMaxKm.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Configuracion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
        Text(
            text = "Version: $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Placa del vehiculo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = plateInput,
            onValueChange = {
                plateInput = it.uppercase()
                OrderStateManager.setVehiclePlate(plateInput.trim())
            },
            label = { Text("Ej: ABC123") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Saved locations section
        Text(
            text = "Ubicaciones guardadas",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Coordenadas exactas para direcciones frecuentes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onOpenSavedLocations() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "📍 Gestionar ubicaciones")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sound switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sonido del popup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Reproducir sonido al recibir un servicio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isPopupSoundEnabled,
                onCheckedChange = { OrderStateManager.setPopupSoundEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-accept section
        Text(
            text = "Aceptación automática Picap",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configurar condiciones para aceptar órdenes automáticamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = ganancia1Input,
            onValueChange = {
                ganancia1Input = it.filter { c -> c.isDigit() }
                OrderStateManager.setAutoAcceptMinGanancia1(ganancia1Input.toDoubleOrNull() ?: 18000.0)
            },
            label = { Text("Ganancia mínima (aceptar siempre)") },
            suffix = { Text("COP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Si la ganancia es igual o mayor, se acepta sin importar los km.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = ganancia2Input,
            onValueChange = {
                ganancia2Input = it.filter { c -> c.isDigit() }
                OrderStateManager.setAutoAcceptMinGanancia2(ganancia2Input.toDoubleOrNull() ?: 14000.0)
            },
            label = { Text("Ganancia mínima 2da condición") },
            suffix = { Text("COP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = kmCond2Input,
            onValueChange = {
                kmCond2Input = it.filter { c -> c.isDigit() || c == '.' }
                OrderStateManager.setAutoAcceptMaxKmCond2(kmCond2Input.toDoubleOrNull() ?: 4.5)
            },
            label = { Text("Km máximo 2da condición") },
            suffix = { Text("km") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Si la ganancia es igual o mayor Y los km de recogida son menores o iguales, se acepta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aceptar por distancia",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Aceptar si km de recogida <= umbral.",
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
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = kmUmbralInput,
                onValueChange = {
                    kmUmbralInput = it.filter { c -> c.isDigit() || c == '.' }
                    OrderStateManager.setAutoAcceptMaxKm(kmUmbralInput.toDoubleOrNull() ?: 2.0)
                },
                label = { Text("Km máximo umbral") },
                suffix = { Text("km") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Si es 5.0 o más, acepta sin límite de distancia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // WhatsApp auto-accept section
        Text(
            text = "Aceptación automática WhatsApp",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configurar condiciones para aceptar servicios de WhatsApp automáticamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // WhatsApp auto-accept state
        val whatsappAutoAcceptMinGanancia1 by OrderStateManager.whatsappAutoAcceptMinGanancia1.collectAsState()
        val whatsappAutoAcceptMinGanancia2 by OrderStateManager.whatsappAutoAcceptMinGanancia2.collectAsState()
        val whatsappAutoAcceptMaxKmCond2 by OrderStateManager.whatsappAutoAcceptMaxKmCond2.collectAsState()
        val isWhatsappAutoAcceptByKmEnabled by OrderStateManager.isWhatsappAutoAcceptByKmEnabled.collectAsState()
        val whatsappAutoAcceptMaxKm by OrderStateManager.whatsappAutoAcceptMaxKm.collectAsState()

        var whatsappGanancia1Input by remember { mutableStateOf(whatsappAutoAcceptMinGanancia1.toInt().toString()) }
        var whatsappGanancia2Input by remember { mutableStateOf(whatsappAutoAcceptMinGanancia2.toInt().toString()) }
        var whatsappKmCond2Input by remember { mutableStateOf(whatsappAutoAcceptMaxKmCond2.toString()) }
        var whatsappKmUmbralInput by remember { mutableStateOf(whatsappAutoAcceptMaxKm.toString()) }

        OutlinedTextField(
            value = whatsappGanancia1Input,
            onValueChange = {
                whatsappGanancia1Input = it.filter { c -> c.isDigit() }
                OrderStateManager.setWhatsappAutoAcceptMinGanancia1(whatsappGanancia1Input.toDoubleOrNull() ?: 18000.0)
            },
            label = { Text("Ganancia mínima (aceptar siempre)") },
            suffix = { Text("COP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Si la ganancia es igual o mayor, se acepta sin importar los km.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = whatsappGanancia2Input,
            onValueChange = {
                whatsappGanancia2Input = it.filter { c -> c.isDigit() }
                OrderStateManager.setWhatsappAutoAcceptMinGanancia2(whatsappGanancia2Input.toDoubleOrNull() ?: 14000.0)
            },
            label = { Text("Ganancia mínima 2da condición") },
            suffix = { Text("COP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = whatsappKmCond2Input,
            onValueChange = {
                whatsappKmCond2Input = it.filter { c -> c.isDigit() || c == '.' }
                OrderStateManager.setWhatsappAutoAcceptMaxKmCond2(whatsappKmCond2Input.toDoubleOrNull() ?: 4.5)
            },
            label = { Text("Km máximo 2da condición") },
            suffix = { Text("km") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Si la ganancia es igual o mayor Y los km de recogida son menores o iguales, se acepta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aceptar por distancia",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Aceptar si km de recogida <= umbral.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isWhatsappAutoAcceptByKmEnabled,
                onCheckedChange = { OrderStateManager.setWhatsappAutoAcceptByKmEnabled(it) }
            )
        }

        if (isWhatsappAutoAcceptByKmEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = whatsappKmUmbralInput,
                onValueChange = {
                    whatsappKmUmbralInput = it.filter { c -> c.isDigit() || c == '.' }
                    OrderStateManager.setWhatsappAutoAcceptMaxKm(whatsappKmUmbralInput.toDoubleOrNull() ?: 2.0)
                },
                label = { Text("Km máximo umbral") },
                suffix = { Text("km") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Si es 5.0 o más, acepta sin límite de distancia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Update section
        val scope = rememberCoroutineScope()
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var updateMessage by remember { mutableStateOf<String?>(null) }

        Text(
            text = "Actualizaciones",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Verificar si hay una nueva versión disponible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                isCheckingUpdate = true
                updateMessage = null
                scope.launch {
                    try {
                        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                        val updateInfo = UpdateChecker.checkForUpdate(versionName)
                        isCheckingUpdate = false
                        if (updateInfo != null) {
                            updateMessage = "Nueva versión: v${updateInfo.version}"
                            // Download and install
                            val downloadId = UpdateChecker.downloadApk(
                                context,
                                updateInfo.downloadUrl,
                                updateInfo.tagName
                            )
                            // Show toast on main thread
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Descargando actualización...", Toast.LENGTH_LONG).show()
                            }
                            // Register receiver on main thread to open install intent when download completes
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context, intent: Intent) {
                                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                    if (id == downloadId) {
                                        // Get the file path using COLUMN_LOCAL_FILENAME
                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        val query = DownloadManager.Query().setFilterById(downloadId)
                                        val cursor = dm.query(query)
                                        if (cursor.moveToFirst()) {
                                            val filePathIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                                            val filePath = cursor.getString(filePathIdx)
                                            if (filePath != null) {
                                                val installIntent = UpdateChecker.getInstallIntent(ctx, filePath)
                                                installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ctx.startActivity(installIntent)
                                            }
                                        }
                                        cursor.close()
                                        ctx.unregisterReceiver(this)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
                            }
                        } else {
                            updateMessage = "Estás en la última versión"
                        }
                    } catch (e: Exception) {
                        isCheckingUpdate = false
                        updateMessage = "Error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCheckingUpdate
        ) {
            Text(text = if (isCheckingUpdate) "Verificando..." else "🔄 Verificar actualizaciones")
        }

        if (updateMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = updateMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (updateMessage!!.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cache section
        Text(
            text = "Cache de servicios",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Eliminar los IDs de servicios ya escaneados para volver a capturarlos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { OrderStateManager.clearAllCache() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(text = "Limpiar cache", color = MaterialTheme.colorScheme.onError)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
