package com.example.projectacc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.projectacc.OrderCard
import com.example.projectacc.OrderDisplay
import com.example.projectacc.OrderStateManager

@Composable
fun WhatsAppScreen(modifier: Modifier = Modifier) {
    val whatsAppOrder by OrderStateManager.currentWhatsAppOrder.collectAsState()
    val isGroupAutoRespond by OrderStateManager.isGroupAutoRespondEnabled.collectAsState()
    val isAutoPlate by OrderStateManager.isAutoPlateEnabled.collectAsState()
    val vehiclePlate by OrderStateManager.vehiclePlate.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "WhatsApp",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-Respond switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Responder en grupo",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isGroupAutoRespond) "Popup flotante activo" else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isGroupAutoRespond,
                onCheckedChange = { OrderStateManager.setGroupAutoRespondEnabled(it) }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Auto-Plate switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Placa en privado",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAutoPlate) {
                        if (vehiclePlate.isNotEmpty()) "Pegando placa: $vehiclePlate"
                        else "Placa no configurada"
                    } else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isAutoPlate,
                onCheckedChange = { OrderStateManager.setAutoPlateEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        // WhatsApp order display
        if (whatsAppOrder != null) {
            OrderCard(
                orderDisplay = OrderDisplay.WhatsApp(whatsAppOrder!!),
                onDismiss = { OrderStateManager.clearWhatsAppOrder() }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Esperando servicios de WhatsApp...")
            }
        }
    }
}
