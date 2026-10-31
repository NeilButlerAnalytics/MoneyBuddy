package com.moneybuddy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.moneybuddy.app.data.MonthSummary
import com.moneybuddy.app.data.Transaction
import com.moneybuddy.app.data.paymentTypeLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

internal class ChartSlice(
    val category: String,
    val totalPence: Long,
    val color: Color,
    val transactions: List<Transaction>,
)

/** A request to open the chart overlay, with the chart's position on screen so the overlay can grow out of it. */
internal class ChartRequest(
    val slices: List<ChartSlice>,
    val index: Int,
    val origin: Rect,
)

private class BoundsHolder {
    var coordinates: LayoutCoordinates? = null
}

private val categoryPalette = listOf(
    Color(0xFFF28C28), Color(0xFF39B978), Color(0xFF5B8DEF), Color(0xFFE5989B), Color(0xFF6BCBCA),
    Color(0xFFB388EB), Color(0xFFF2C14E), Color(0xFFE56B6F), Color(0xFF8FBF5A), Color(0xFF4FB0C6),
    Color(0xFFD98E73), Color(0xFF9AA5B1), Color(0xFFC77DBB),
)

internal fun categoryColor(category: String): Color {
    val known = transactionCategories.indexOf(category)
    val index = if (known >= 0) known else (category.hashCode() and Int.MAX_VALUE)
    return categoryPalette[index % categoryPalette.size]
}

internal fun buildSlices(transactions: List<Transaction>): List<ChartSlice> =
    transactions.filter { it.amountPence < 0 }
        .groupBy { it.category }
        .map { (category, list) ->
            ChartSlice(
                category = category,
                totalPence = list.sumOf { -it.amountPence },
                color = categoryColor(category),
                transactions = list.sortedByDescending { it.date },
            )
        }
        .sortedByDescending { it.totalPence }

// Ring width as a fraction of the outer radius, and the room kept free so a detached slice still fits.
private const val RING_THICKNESS = 0.46f
private const val POP_ROOM = 0.24f
private const val POP_SHIFT = 0.45f
private const val POP_GROW = 0.2f
private const val SLICE_GAP_DEGREES = 1.4f

internal const val HIT_NONE = -1
internal const val HIT_HOLE = -2

private class DonutGeometry(val center: Offset, val outer: Float) {
    val ring = outer * RING_THICKNESS
    val inner = outer - ring
    val mid = outer - ring / 2f
}

private fun donutGeometry(size: Size): DonutGeometry =
    DonutGeometry(Offset(size.width / 2f, size.height / 2f), min(size.width, size.height) / 2f * (1f - POP_ROOM))

private fun sliceRanges(slices: List<ChartSlice>): List<ClosedFloatingPointRange<Float>> {
    val total = slices.sumOf { it.totalPence }.toFloat().coerceAtLeast(1f)
    var start = 0f
    return slices.map { slice ->
        val sweep = slice.totalPence / total * 360f
        (start..(start + sweep)).also { start += sweep }
    }
}

private fun clockwiseDegrees(dx: Float, dy: Float): Float {
    var degrees = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (degrees < 0f) degrees += 360f
    return degrees
}

/**
 * Returns the index of the slice at [tap], [HIT_HOLE] for the middle of the ring, or [HIT_NONE] for anywhere else.
 * A detached slice is hit where it is drawn, not where it started.
 */
internal fun hitTestDonut(tap: Offset, size: Size, slices: List<ChartSlice>, popOf: (Int) -> Float): Int {
    if (slices.isEmpty()) return HIT_NONE
    val geometry = donutGeometry(size)
    val ranges = sliceRanges(slices)

    ranges.forEachIndexed { index, range ->
        val pop = popOf(index)
        if (pop > 0.05f) {
            val bisector = Math.toRadians(((range.start + range.endInclusive) / 2f - 90f).toDouble())
            val shift = geometry.ring * POP_SHIFT * pop
            val dx = tap.x - (geometry.center.x + cos(bisector).toFloat() * shift)
            val dy = tap.y - (geometry.center.y + sin(bisector).toFloat() * shift)
            val distance = hypot(dx, dy)
            val degrees = clockwiseDegrees(dx, dy)
            if (distance >= geometry.inner && distance <= geometry.outer + geometry.ring * POP_GROW * pop &&
                degrees >= range.start && degrees < range.endInclusive
            ) {
                return index
            }
        }
    }

    val distance = hypot(tap.x - geometry.center.x, tap.y - geometry.center.y)
    val slop = geometry.ring * 0.08f
    if (distance < geometry.inner - slop) return HIT_HOLE
    if (distance > geometry.outer + slop) return HIT_NONE
    val degrees = clockwiseDegrees(tap.x - geometry.center.x, tap.y - geometry.center.y)
    val found = ranges.indexOfFirst { degrees >= it.start && degrees < it.endInclusive }
    return if (found >= 0) found else ranges.lastIndex
}

private fun DrawScope.drawDonut(slices: List<ChartSlice>, holeColor: Color, popOf: (Int) -> Float) {
    val geometry = donutGeometry(size)
    val ranges = sliceRanges(slices)
    val focus = slices.indices.maxOfOrNull(popOf) ?: 0f

    if (holeColor != Color.Transparent) drawCircle(holeColor, radius = geometry.inner, center = geometry.center)

    // Detached slices are drawn last so they sit in front of the rest of the ring.
    slices.indices.sortedBy(popOf).forEach { index ->
        val range = ranges[index]
        val sweep = range.endInclusive - range.start
        val pop = popOf(index)
        val gap = if (slices.size > 1) min(SLICE_GAP_DEGREES, sweep * 0.5f) else 0f
        val ringWidth = geometry.ring * (1f + POP_GROW * pop)
        val radius = geometry.mid + geometry.ring * POP_GROW / 2f * pop
        val bisector = Math.toRadians(((range.start + range.endInclusive) / 2f - 90f).toDouble())
        val shift = geometry.ring * POP_SHIFT * pop
        drawArc(
            color = slices[index].color.copy(alpha = 1f - 0.45f * focus * (1f - pop)),
            startAngle = range.start - 90f + gap / 2f,
            sweepAngle = max(sweep - gap, 0.1f),
            useCenter = false,
            topLeft = Offset(
                geometry.center.x - radius + cos(bisector).toFloat() * shift,
                geometry.center.y - radius + sin(bisector).toFloat() * shift,
            ),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = ringWidth),
        )
    }
}

@Composable
private fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    holeColor: Color = Color.Transparent,
    popOf: (Int) -> Float = { 0f },
    onTap: (Int) -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
) {
    val currentPopOf by rememberUpdatedState(popOf)
    val currentOnTap by rememberUpdatedState(onTap)
    Box(modifier) {
        Canvas(
            Modifier.matchParentSize().pointerInput(slices) {
                detectTapGestures { tap ->
                    currentOnTap(hitTestDonut(tap, size.toSize(), slices, currentPopOf))
                }
            },
        ) { drawDonut(slices, holeColor, popOf) }
        content()
    }
}

@Composable
internal fun SpendingCard(
    summary: MonthSummary,
    hidden: Boolean,
    onOpen: (ChartRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slices = remember(summary.transactions) { buildSlices(summary.transactions) }
    val total = slices.sumOf { it.totalPence }
    val holder = remember { BoundsHolder() }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        BoxWithConstraints(Modifier.padding(20.dp)) {
            val stacked = maxWidth < 440.dp
            val chartSize = min(maxWidth.value, 250f).dp
            Column {
                Text("Spending breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (slices.isEmpty()) "No spending this month" else "Tap a section to see its transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (slices.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val chart: @Composable () -> Unit = {
                        DonutChart(
                            slices = slices,
                            modifier = Modifier
                                .size(chartSize)
                                .onGloballyPositioned { holder.coordinates = it }
                                .graphicsLayer { alpha = if (hidden) 0f else 1f }
                                .semantics { contentDescription = "Spending breakdown chart. Tap a section for its transactions." },
                            onTap = { hit ->
                                val bounds = holder.coordinates?.boundsInRoot()
                                if (hit >= 0 && bounds != null) onOpen(ChartRequest(slices, hit, bounds))
                            },
                        ) {
                            Column(
                                Modifier.align(Alignment.Center).fillMaxWidth(0.36f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    formatPounds(total),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    "spent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (stacked) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            chart()
                            Spacer(Modifier.height(16.dp))
                            SliceLegend(slices, total)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            chart()
                            Box(Modifier.weight(1f)) { SliceLegend(slices, total) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliceLegend(slices: List<ChartSlice>, total: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(slice.color, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    slice.category,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    formatPounds(slice.totalPence),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "  ${percentOf(slice.totalPence, total)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun percentOf(part: Long, whole: Long): Int =
    if (whole <= 0L) 0 else (part * 100f / whole).roundToInt()

/**
 * Full-screen focus view for the spending chart. [progress] runs 0 to 1 as the chart grows out of its card and back
 * again; it is owned by the caller so the screen behind the overlay can blur in step with it.
 */
@Composable
internal fun SpendingChartOverlay(
    request: ChartRequest,
    origin: Rect,
    progress: Animatable<Float, AnimationVector1D>,
    onClosed: () -> Unit,
) {
    val slices = request.slices
    val total = slices.sumOf { it.totalPence }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Int?>(request.index) }
    var shownIndex by remember { mutableIntStateOf(request.index) }
    var closing by remember { mutableStateOf(false) }
    val pops = remember(slices.size) { List(slices.size) { Animatable(0f) } }
    val surface = MaterialTheme.colorScheme.surface

    LaunchedEffect(Unit) { progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    LaunchedEffect(selected) {
        // The first slice waits for the chart to grow before it detaches.
        val wait = if (progress.value < 0.9f && selected != null) 300 else 0
        pops.forEachIndexed { index, pop ->
            launch {
                pop.animateTo(
                    if (index == selected) 1f else 0f,
                    tween(500, delayMillis = if (index == selected) wait else 0, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    fun close() {
        if (closing) return
        closing = true
        selected = null
        scope.launch {
            progress.animateTo(0f, tween(550, easing = FastOutSlowInEasing))
            onClosed()
        }
    }

    BackHandler { close() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val margin = 24.dp
        val chartSide: androidx.compose.ui.unit.Dp
        val chartX: androidx.compose.ui.unit.Dp
        val chartY: androidx.compose.ui.unit.Dp
        val panelX: androidx.compose.ui.unit.Dp
        val panelY: androidx.compose.ui.unit.Dp
        val panelWidth: androidx.compose.ui.unit.Dp
        val panelHeight: androidx.compose.ui.unit.Dp
        if (wide) {
            val column = maxWidth * 0.48f
            chartSide = minOf(maxHeight * 0.88f, column - margin)
            chartX = (column - chartSide) / 2f + margin / 2f
            chartY = (maxHeight - chartSide) / 2f
            panelX = column + margin / 2f
            panelWidth = maxWidth - panelX - margin
            panelHeight = minOf(maxHeight - margin * 2f, 680.dp)
            panelY = (maxHeight - panelHeight) / 2f
        } else {
            chartSide = minOf(maxWidth - margin * 2f, maxHeight * 0.44f)
            chartX = (maxWidth - chartSide) / 2f
            chartY = margin
            panelX = margin
            panelY = chartY + chartSide + margin / 2f
            panelWidth = maxWidth - margin * 2f
            panelHeight = maxHeight - panelY - margin
        }
        val target = with(density) {
            Rect(chartX.toPx(), chartY.toPx(), (chartX + chartSide).toPx(), (chartY + chartSide).toPx())
        }
        val panelSlide = with(density) { 80.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        )

        DonutChart(
            slices = slices,
            holeColor = surface,
            popOf = { pops[it].value },
            onTap = { hit ->
                when {
                    hit >= 0 && !closing -> {
                        selected = hit
                        shownIndex = hit
                    }
                    hit == HIT_NONE -> close()
                }
            },
            modifier = Modifier
                .offset(chartX, chartY)
                .size(chartSide)
                .graphicsLayer {
                    val p = progress.value
                    val scale = lerp(origin.width / target.width, 1f, p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = lerp(origin.left - target.left, 0f, p)
                    translationY = lerp(origin.top - target.top, 0f, p)
                }
                .drawBehind {
                    // A soft shadow under the ring makes the chart feel lifted towards the viewer.
                    val radius = size.minDimension / 2f * (1f - POP_ROOM)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.5f * progress.value), Color.Transparent),
                            center = center,
                            radius = radius * 1.35f,
                        ),
                        radius = radius * 1.35f,
                    )
                },
        ) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth(0.36f)) {
                Crossfade(targetState = selected, label = "chart centre", modifier = Modifier.align(Alignment.Center)) { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (index == null) {
                            Text(formatPounds(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("spent", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val slice = slices[index]
                            Text(
                                slice.category,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = slice.color,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                formatPounds(slice.totalPence),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${percentOf(slice.totalPence, total)}% of spending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier.offset(panelX, panelY).size(panelWidth, panelHeight),
            contentAlignment = if (wide) Alignment.Center else Alignment.TopCenter,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = ((progress.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        alpha = p
                        if (wide) translationX = (1f - p) * panelSlide else translationY = (1f - p) * panelSlide
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                colors = CardDefaults.cardColors(containerColor = surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                AnimatedContent(
                    targetState = shownIndex,
                    transitionSpec = {
                        (slideInHorizontally(tween(380)) { it / 5 } + fadeIn(tween(380))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(260)))
                    },
                    label = "slice details",
                ) { index ->
                    SliceDetails(slices[index], total)
                }
            }
        }
    }
}

@Composable
private fun SliceDetails(slice: ChartSlice, total: Long) {
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(slice.color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(slice.category, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "${formatPounds(slice.totalPence)} · ${percentOf(slice.totalPence, total)}% of spending · " +
                "${slice.transactions.size} transaction${if (slice.transactions.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(slice.transactions, key = { it.id }) { transaction ->
                SliceTransactionRow(transaction, slice.totalPence)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
private fun SliceTransactionRow(transaction: Transaction, categoryTotal: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(transaction.merchant, fontWeight = FontWeight.SemiBold)
            Text(
                formatLongDate(transaction.date) +
                    if (transaction.type.isNotBlank()) " · ${paymentTypeLabel(transaction.type)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (transaction.note.isNotBlank()) {
                Text(
                    "Note: ${transaction.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (transaction.isDemo) {
                Text("Demo transaction", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF28C28))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPounds(transaction.amountPence), fontWeight = FontWeight.Bold)
            Text(
                "${percentOf(-transaction.amountPence, categoryTotal)}% of category",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatLongDate(isoDate: String): String =
    runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK))
    }.getOrDefault(isoDate)
