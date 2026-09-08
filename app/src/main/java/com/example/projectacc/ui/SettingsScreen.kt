package com.example.projectacc.ui

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
import com.example.projectacc.OrderStateManager

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Configuracion",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Placa del vehiculo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = plateInput,
            onValueChange = { plateInput = it.uppercase() },
            label = { Text("Ej: ABC123") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                OrderStateManager.setVehiclePlate(plateInput.trim())
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Guardar")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Saved locations section
        Text(
            text = "Ubicaciones guardadas",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
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
                    fontWeight = FontWeight.SemiBold
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
            fontWeight = FontWeight.SemiBold
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
            onValueChange = { ganancia1Input = it.filter { c -> c.isDigit() } },
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
            onValueChange = { ganancia2Input = it.filter { c -> c.isDigit() } },
            label = { Text("Ganancia mínima 2da condición") },
            suffix = { Text("COP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = kmCond2Input,
            onValueChange = { kmCond2Input = it.filter { c -> c.isDigit() || c == '.' } },
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
                    fontWeight = FontWeight.SemiBold
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
                onValueChange = { kmUmbralInput = it.filter { c -> c.isDigit() || c == '.' } },
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

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                OrderStateManager.setAutoAcceptMinGanancia1(ganancia1Input.toDoubleOrNull() ?: 18000.0)
                OrderStateManager.setAutoAcceptMinGanancia2(ganancia2Input.toDoubleOrNull() ?: 14000.0)
                OrderStateManager.setAutoAcceptMaxKmCond2(kmCond2Input.toDoubleOrNull() ?: 4.5)
                OrderStateManager.setAutoAcceptMaxKm(kmUmbralInput.toDoubleOrNull() ?: 2.0)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Guardar")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cache section
        Text(
            text = "Cache de servicios",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
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

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Volver")
        }
    }
}
