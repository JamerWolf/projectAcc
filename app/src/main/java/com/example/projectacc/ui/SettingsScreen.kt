package com.example.projectacc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

    Column(
        modifier = modifier
            .fillMaxSize()
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
            Text(text = "Limpiar cache", color = Color.White)
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
