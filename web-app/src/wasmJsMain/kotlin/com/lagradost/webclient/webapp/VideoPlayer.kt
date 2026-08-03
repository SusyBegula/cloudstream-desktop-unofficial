@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.lagradost.webclient.webapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

private const val VIDEO_ELEMENT_ID = "cs-web-video"

@JsFun(
    """
    (elementId, topOffsetPx) => {
        if (document.getElementById(elementId)) return;
        const video = document.createElement('video');
        video.id = elementId;
        video.controls = true;
        video.autoplay = true;
        video.style.position = 'fixed';
        video.style.top = topOffsetPx + 'px';
        video.style.left = '0';
        video.style.width = '100vw';
        video.style.height = 'calc(100vh - ' + topOffsetPx + 'px)';
        video.style.background = 'black';
        video.style.zIndex = '1000';
        document.body.appendChild(video);
    }
    """,
)
private external fun jsCreateVideoElement(elementId: String, topOffsetPx: Int)

@JsFun(
    """
    (elementId, url, isHls, startPositionMs) => {
        const video = document.getElementById(elementId);
        if (!video) return;
        function seekOnce() {
            if (startPositionMs > 0) {
                video.currentTime = startPositionMs / 1000;
            }
            video.removeEventListener('loadedmetadata', seekOnce);
        }
        video.addEventListener('loadedmetadata', seekOnce);
        function attachHls() {
            if (isHls && window.Hls && window.Hls.isSupported()) {
                if (video._hls) { video._hls.destroy(); }
                const hls = new window.Hls();
                video._hls = hls;
                hls.loadSource(url);
                hls.attachMedia(video);
                hls.on(window.Hls.Events.MANIFEST_PARSED, function () { video.play(); });
            } else {
                video.src = url;
                video.play();
            }
        }
        if (isHls && !window.Hls) {
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/hls.js@1/dist/hls.min.js';
            script.onload = attachHls;
            document.head.appendChild(script);
        } else {
            attachHls();
        }
    }
    """,
)
private external fun jsAttachAndPlay(elementId: String, url: String, isHls: Boolean, startPositionMs: Double)

@JsFun(
    """
    (elementId) => {
        const video = document.getElementById(elementId);
        if (video) {
            if (video._hls) { video._hls.destroy(); video._hls = null; }
            try { video.pause(); } catch (e) {}
            video.removeAttribute('src');
            video.remove();
        }
    }
    """,
)
private external fun jsDestroyVideoElement(elementId: String)

@JsFun(
    """
    (elementId) => {
        const video = document.getElementById(elementId);
        return video ? video.currentTime * 1000 : -1;
    }
    """,
)
private external fun jsGetVideoCurrentTimeMs(elementId: String): Double

@JsFun(
    """
    (elementId) => {
        const video = document.getElementById(elementId);
        return (video && isFinite(video.duration)) ? video.duration * 1000 : -1;
    }
    """,
)
private external fun jsGetVideoDurationMs(elementId: String): Double

@JsFun(
    """
    (elementId) => {
        const video = document.getElementById(elementId);
        return !!(video && (video.ended || video.error));
    }
    """,
)
private external fun jsIsVideoDoneOrErrored(elementId: String): Boolean

@JsFun(
    """
    () => { const video = document.getElementById('cs-web-video'); if (video && video.requestFullscreen) video.requestFullscreen(); }
    """,
)
private external fun jsRequestVideoFullscreen()

/** Polled (not callback-based — simpler/more reliable JS interop) video playback state, for resume-tracking and auto-fallback. */
object VideoPlayerState {
    fun currentPositionMs(): Long = jsGetVideoCurrentTimeMs(VIDEO_ELEMENT_ID).toLong().coerceAtLeast(0)
    fun durationMs(): Long = jsGetVideoDurationMs(VIDEO_ELEMENT_ID).toLong().coerceAtLeast(0)
    fun isDoneOrErrored(): Boolean = jsIsVideoDoneOrErrored(VIDEO_ELEMENT_ID)
    fun requestFullscreen() = jsRequestVideoFullscreen()
}

/**
 * Mounts a real HTML <video> element (positioned below [topOffsetPx], outside Compose's
 * canvas) and drives it via hls.js for HLS or a native src for progressive/DASH-via-HLS
 * sources. This is the piece Compose-for-Web itself cannot do — its canvas has no native
 * <video> embedding, so we manage a sibling DOM element directly through JS interop.
 */
@Composable
fun VideoOverlay(streamUrl: String?, mimeType: String?, topOffsetPx: Int = 56, startPositionMs: Long = 0) {
    DisposableEffect(streamUrl) {
        if (streamUrl != null) {
            jsCreateVideoElement(VIDEO_ELEMENT_ID, topOffsetPx)
            val isHls = mimeType?.contains("mpegurl", ignoreCase = true) == true
            jsAttachAndPlay(VIDEO_ELEMENT_ID, streamUrl, isHls, startPositionMs.toDouble())
        }
        onDispose {
            jsDestroyVideoElement(VIDEO_ELEMENT_ID)
        }
    }
}
