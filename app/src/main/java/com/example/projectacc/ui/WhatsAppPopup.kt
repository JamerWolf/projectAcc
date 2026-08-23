package com.example.projectacc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projectacc.model.WhatsAppService

@Composable
fun WhatsAppPopup(
    service: WhatsAppService,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nuevo servicio WhatsApp",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Servicio #${service.id}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (service.needsReturnIcon()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🔄",
                            fontSize = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (service.empresa.isNotEmpty()) {
                    DetailRow(label = "Empresa", value = service.empresa)
                }
                if (service.servicio.isNotEmpty()) {
                    DetailRow(label = "Tipo", value = service.servicio)
                }
                if (service.ciudad.isNotEmpty()) {
                    DetailRow(label = "Ciudad", value = service.ciudad)
                }
                if (service.origen.isNotEmpty()) {
                    DetailRow(label = "Origen", value = service.origen)
                }
                if (service.destino.isNotEmpty()) {
                    DetailRow(label = "Destino", value = service.destino)
                }
                if (service.pago.isNotEmpty()) {
                    DetailRow(label = "Pago", value = service.pago)
                }
                if (service.valorCobrar.isNotEmpty()) {
                    DetailRow(label = "Valor a cobrar", value = service.valorCobrar)
                }
                if (service.requisitos.isNotEmpty()) {
                    DetailRow(label = "Requisitos", value = service.requisitos)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = "Aceptar", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cerrar")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
