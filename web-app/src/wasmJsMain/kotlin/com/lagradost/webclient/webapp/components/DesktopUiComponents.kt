package com.lagradost.webclient.webapp.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.webclient.webapp.theme.DesktopUi
import kotlinx.coroutines.launch

/** Web-client port of desktop-app's ui/components/DesktopUiComponents.kt (color tokens live in theme/AppTheme.kt instead). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DesktopUi.TextPrimary)
        trailing?.invoke()
    }
}

@Composable
fun CategoryRowWithHeader(
    title: String,
    modifier: Modifier = Modifier,
    itemCount: Int,
    scrollStep: Int = 4,
    isInfinite: Boolean = false,
    onViewAll: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val initialIndex = remember(isInfinite, itemCount) {
        if (isInfinite && itemCount > 0) (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % itemCount) else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val canScrollBack by remember(isInfinite) { derivedStateOf { isInfinite || listState.canScrollBackward } }
    val canScrollForward by remember(isInfinite) { derivedStateOf { isInfinite || listState.canScrollForward } }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DesktopUi.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onViewAll != null) {
                    TextButton(onClick = onViewAll) { Text("View All", color = DesktopUi.Accent) }
                    Spacer(Modifier.width(8.dp))
                }
                ScrollChevron(
                    enabled = canScrollBack,
                    onClick = {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex - scrollStep).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                )
                Spacer(Modifier.width(8.dp))
                ScrollChevron(
                    enabled = canScrollForward,
                    onClick = {
                        scope.launch {
                            val last = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + scrollStep
                            val maxBound = if (isInfinite) Int.MAX_VALUE else (itemCount - 1).coerceAtLeast(0)
                            listState.animateScrollToItem(last.coerceAtMost(maxBound))
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                )
            }
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun ScrollChevron(enabled: Boolean, onClick: () -> Unit, icon: ImageVector) {
    val alpha = if (enabled) 1f else 0.35f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = 2.dp).size(40.dp),
        shape = CircleShape,
        color = DesktopUi.SurfaceElevated.copy(alpha = alpha),
        shadowElevation = if (enabled) 4.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = null, tint = if (enabled) DesktopUi.Accent else DesktopUi.TextMuted, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun PosterTitleLabel(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(DesktopUi.SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = DesktopUi.TextPrimary,
            lineHeight = 16.sp,
        )
    }
}

@Composable
fun Modifier.posterHoverEffect(): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.05f else 1f, tween(180), label = "posterScale")
    val elevation by animateFloatAsState(if (hovered) 12f else 4f, tween(180), label = "posterElevation")
    val primary = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(if (hovered) primary else Color.Transparent, tween(180), label = "posterBorderColor")
    return this
        .scale(scale)
        .hoverable(interaction)
        .shadow(elevation.dp, RoundedCornerShape(12.dp))
        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
}
