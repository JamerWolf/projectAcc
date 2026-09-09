package com.example.projectacc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projectacc.location.SavedLocation
import com.example.projectacc.location.SavedLocationManager

@Composable
fun SavedLocationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var locations by remember { mutableStateOf(SavedLocationManager.loadLocations(context)) }
    var showDialog by remember { mutableStateOf(false) }
    var editingLocation by remember { mutableStateOf<SavedLocation?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
    ) {
        Text(
            text = "Ubicaciones guardadas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configura coordenadas exactas para direcciones frecuentes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                editingLocation = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "+ Agregar ubicación")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (locations.isEmpty()) {
            Text(
                text = "No hay ubicaciones guardadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(locations) { location ->
                    SavedLocationCard(
                        location = location,
                        onEdit = {
                            editingLocation = it
                            showDialog = true
                        },
                        onDelete = {
                            SavedLocationManager.deleteLocation(it.id, context)
                            locations = SavedLocationManager.loadLocations(context)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Volver")
        }
    }

    if (showDialog) {
        SavedLocationDialog(
            location = editingLocation,
            onSave = { saved ->
                if (editingLocation != null) {
                    SavedLocationManager.updateLocation(saved, context)
                } else {
                    SavedLocationManager.addLocation(saved, context)
                }
                locations = SavedLocationManager.loadLocations(context)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun SavedLocationCard(
    location: SavedLocation,
    onEdit: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${location.lat}, ${location.lon}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (location.matches.isNotEmpty()) {
                Text(
                    text = "Match: ${location.matches.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = { onEdit(location) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Editar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onDelete(location) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = "Eliminar", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}

@Composable
private fun SavedLocationDialog(
    location: SavedLocation?,
    onSave: (SavedLocation) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(location?.name ?: "") }
    var lat by remember { mutableStateOf(location?.lat?.toString() ?: "") }
    var lon by remember { mutableStateOf(location?.lon?.toString() ?: "") }
    var matchesText by remember { mutableStateOf(location?.matches?.joinToString(", ") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (location != null) "Editar ubicación" else "Nueva ubicación",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej: Tienda Cruz Verde)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitud (ej: 7.9121287)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Longitud (ej: -72.523726)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = matchesText,
                    onValueChange = { matchesText = it },
                    label = { Text("Coincidencias (separadas por coma)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Palabras que aparecen en la dirección del servicio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val latValue = lat.toDoubleOrNull() ?: 0.0
                    val lonValue = lon.toDoubleOrNull() ?: 0.0
                    val matches = matchesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(
                        SavedLocation(
                            id = location?.id ?: System.currentTimeMillis().toString(),
                            name = name,
                            lat = latValue,
                            lon = lonValue,
                            matches = matches
                        )
                    )
                },
                enabled = name.isNotEmpty() && lat.isNotEmpty() && lon.isNotEmpty()
            ) {
                Text(text = "Guardar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cancelar")
            }
        }
    )
}
