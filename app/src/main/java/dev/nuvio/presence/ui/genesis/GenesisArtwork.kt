package dev.nuvio.presence.ui.genesis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import dev.nuvio.presence.ArtworkImages
import dev.nuvio.presence.cachedPublicArtwork
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.nuvio.presence.R
import dev.nuvio.presence.ui.themes.GraphicsMode
import dev.nuvio.presence.ui.themes.LocalGraphics

@Composable
internal fun GenesisArtwork(
    url: String, modifier: Modifier, cacheOnly: Boolean = false,
    local: ByteArray? = null, horizon: Boolean = true
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val fade = LocalGraphics.current != GraphicsMode.ECO && genesisMotion(160) != 0
    val safeUrl = if (horizon) cachedPublicArtwork(url) else url
    val data = local ?: safeUrl.takeIf(String::isNotBlank)
    var loaded by remember(data) { mutableStateOf(false) }
    var legacyCache by remember(data) { mutableStateOf(false) }
    Box(modifier.clip(MaterialTheme.shapes.medium).background(colors.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (!loaded) Icon(painterResource(R.drawable.suki_eclipse_notification), null,
            modifier = Modifier.size(36.dp), tint = colors.primary)
        if (data != null) {
            val request = remember(context, data, cacheOnly, fade) {
                ImageRequest.Builder(context).data(data).crossfade(fade).apply {
                    if (cacheOnly) networkCachePolicy(CachePolicy.DISABLED)
                    if (local != null) diskCachePolicy(CachePolicy.DISABLED)
                }.build()
            }
            val loader = if (horizon && !legacyCache) ArtworkImages.loader(context) else context.imageLoader
            key(data, loader) {
                AsyncImage(request, null, imageLoader = loader, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onSuccess = { loaded = true }, onError = {
                    loaded = false
                    if (horizon && cacheOnly) legacyCache = true
                })
            }
        }
    }
}
