package com.lagradost.webclient.webapp.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLParameter
import org.jetbrains.skia.Image as SkiaImage

/** Same-origin base URL the app was served from — images are proxied through it to dodge cross-origin CORS restrictions on decoding raw bytes. */
object Env {
    val apiBaseUrl: String by lazy { kotlinx.browser.window.location.origin }
}

private object WebImageLoader {
    private val http = HttpClient()
    private val cache = mutableMapOf<String, ImageBitmap?>()

    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        val bitmap = runCatching {
            val proxied = "${Env.apiBaseUrl}/api/image?url=${url.encodeURLParameter()}"
            val bytes = http.get(proxied).body<ByteArray>()
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
        cache[url] = bitmap
        return bitmap
    }
}

/** Coil-AsyncImage-shaped composable backed by a same-origin server image proxy + Skia decode — no Coil/DOM-img dependency. */
@Composable
fun WebAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(model) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(model) {
        bitmap = model?.let { WebImageLoader.load(it) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    }
}
