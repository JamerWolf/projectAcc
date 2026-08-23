package com.example.projectacc

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projectacc.model.WhatsAppService

sealed class OrderDisplay {
    data class Picap(val order: PicapOrder) : OrderDisplay()
    data class WhatsApp(val service: WhatsAppService) : OrderDisplay()
}

/**
 * Abre Google Maps con una ruta desde el origen al destino.
 * Si Google Maps no está instalado, abre la ruta en el navegador como fallback.
 */
private fun openRouteInMaps(context: android.content.Context, origin: String, destination: String) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(destination)}&travelmode=driving")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    // Fallback: si Google Maps no está instalado, abrir en navegador
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        // Abrir sin forzar Google Maps (se abrirá en el navegador)
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(fallbackIntent)
    }
}

/**
 * Composable que muestra los detalles de una orden (Picap o WhatsApp) en una Card de Material3.
 *
 * @param orderDisplay La orden a mostrar (Picap o WhatsApp).
 * @param onDismiss Callback que se ejecuta cuando el usuario pulsa "Eliminar".
 */
@Composable
fun OrderCard(orderDisplay: OrderDisplay, onDismiss: () -> Unit) {
    when (orderDisplay) {
        is OrderDisplay.Picap -> PicapOrderCard(order = orderDisplay.order, onDismiss = onDismiss)
        is OrderDisplay.WhatsApp -> WhatsAppOrderCard(service = orderDisplay.service, onDismiss = onDismiss)
    }
}

@Composable
private fun PicapOrderCard(order: PicapOrder, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Source indicator + Title
            Text(
                text = "\uD83D\uDE97 Orden #${order.id}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Ganancia
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83D\uDCB0", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = order.ganancia,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seccion de Recogida
            Text(
                text = "Recogida",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83D\uDCCD", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = order.tiempoRecogida,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = order.direccionRecogida,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Seccion de Entrega
            Text(
                text = "Entrega",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83C\uDFC1", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = order.tiempoEntrega,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = order.direccionEntrega,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones: Ver Ruta + Eliminar
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        openRouteInMaps(context, order.direccionRecogida, order.direccionEntrega)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "Ver Ruta", color = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text(text = "Eliminar", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WhatsAppOrderCard(service: WhatsAppService, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Source indicator + Title + Return icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\uD83D\uDCF1 Servicio #${service.id}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (service.needsReturnIcon()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔄",
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (service.empresa.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83C\uDFE2", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = service.empresa,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (service.servicio.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = service.servicio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ciudad
            if (service.ciudad.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83C\uDFD9\uFE0F", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = service.ciudad,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Origen
            if (service.origen.isNotEmpty()) {
                Text(
                    text = "Origen",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83D\uDCCD", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = service.origen,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Destino
            if (service.destino.isNotEmpty()) {
                Text(
                    text = "Destino",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83C\uDFC1", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = service.destino,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pago
            if (service.pago.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83D\uDCB3", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pago: ${service.pago}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Valor
            if (service.valorCobrar.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\uD83D\uDCB0", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = service.valorCobrar,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Requisitos
            if (service.requisitos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Requisitos",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = service.requisitos,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones: Ver Ruta + Eliminar
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        openRouteInMaps(context, service.origen, service.destino)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "Ver Ruta", color = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text(text = "Eliminar", color = Color.White)
                }
            }
        }
    }
}
