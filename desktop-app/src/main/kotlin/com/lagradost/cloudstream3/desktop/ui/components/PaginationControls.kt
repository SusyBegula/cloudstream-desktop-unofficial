package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: String = "episodes",
) {
    if (totalPages <= 1) return

    val startItem = ((currentPage - 1) * pageSize) + 1
    val endItem = minOf(currentPage * pageSize, totalItems)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Showing $startItem–$endItem of $totalItems $itemLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Previous Button
            FilledTonalButton(
                onClick = { onPageChange(currentPage - 1) },
                enabled = currentPage > 1,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DesktopUi.SurfaceElevated,
                    contentColor = DesktopUi.TextPrimary,
                    disabledContainerColor = DesktopUi.SurfaceElevated.copy(alpha = 0.4f),
                    disabledContentColor = DesktopUi.TextMuted.copy(alpha = 0.4f),
                ),
            ) {
                Text("‹ Prev", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            // Page numbers
            val pagesToShow = remember(currentPage, totalPages) {
                val pages = mutableListOf<Int?>()
                if (totalPages <= 7) {
                    for (i in 1..totalPages) pages.add(i)
                } else {
                    pages.add(1)
                    if (currentPage > 4) {
                        pages.add(null) // ellipsis
                    }
                    val start = maxOf(2, currentPage - 1)
                    val end = minOf(totalPages - 1, currentPage + 1)
                    for (i in start..end) {
                        pages.add(i)
                    }
                    if (currentPage < totalPages - 3) {
                        pages.add(null) // ellipsis
                    }
                    pages.add(totalPages)
                }
                pages
            }

            pagesToShow.forEach { page ->
                if (page == null) {
                    Text(
                        text = "...",
                        color = DesktopUi.TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    val isSelected = page == currentPage
                    if (isSelected) {
                        Button(
                            onClick = { },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopUi.Accent,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = "$page",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { onPageChange(page) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DesktopUi.SurfaceElevated,
                                contentColor = DesktopUi.TextPrimary,
                            ),
                        ) {
                            Text(
                                text = "$page",
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Next Button
            FilledTonalButton(
                onClick = { onPageChange(currentPage + 1) },
                enabled = currentPage < totalPages,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DesktopUi.SurfaceElevated,
                    contentColor = DesktopUi.TextPrimary,
                    disabledContainerColor = DesktopUi.SurfaceElevated.copy(alpha = 0.4f),
                    disabledContentColor = DesktopUi.TextMuted.copy(alpha = 0.4f),
                ),
            ) {
                Text("Next ›", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
