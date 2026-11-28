package dev.nuvio.presence.ui.genesis

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.nuvio.presence.R

@Composable
internal fun GenesisOnboarding(onExtensions: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Image(painterResource(R.drawable.suki_eclipse_splash_icon), null, Modifier.size(88.dp)) },
        title = { Text("Sua presença no Discord") },
        text = { Text("Escolha os apps que o SukiRPC pode sincronizar. Para conectar uma TV, abra Dispositivos.") },
        confirmButton = { Button(onClick = onExtensions) { Text("Abrir extensões") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } }
    )
}
