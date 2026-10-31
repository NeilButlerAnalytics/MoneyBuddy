package com.moneybuddy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneybuddy.app.data.DateRange
import com.moneybuddy.app.data.PeriodGrain
import com.moneybuddy.app.data.SpendingPeriod
import com.moneybuddy.app.data.formatRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Chooses the days the spending chart covers: a week or a month (rolling, or so far), or a custom range. Every
 * change is passed straight to [onChange], so the chart recalculates as soon as a choice is made.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodSelector(
    period: SpendingPeriod,
    range: DateRange,
    onChange: (SpendingPeriod) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = period.grain == PeriodGrain.WEEK,
                onClick = { onChange(period.copy(grain = PeriodGrain.WEEK)) },
                label = { Text("Week") },
            )
            FilterChip(
                selected = period.grain == PeriodGrain.MONTH,
                onClick = { onChange(period.copy(grain = PeriodGrain.MONTH)) },
                label = { Text("Month") },
            )
            FilterChip(
                selected = period.grain == PeriodGrain.CUSTOM,
                onClick = {
                    // Start from the days already on screen, so the first custom range is a familiar one.
                    onChange(
                        period.copy(
                            grain = PeriodGrain.CUSTOM,
                            customStart = period.customStart ?: range.start,
                            customEnd = period.customEnd ?: range.end,
                        ),
                    )
                },
                label = { Text("Custom range") },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (period.grain) {
                PeriodGrain.WEEK -> {
                    FilterChip(
                        selected = period.rolling,
                        onClick = { onChange(period.copy(rolling = true)) },
                        label = { Text("Last 7 days") },
                    )
                    FilterChip(
                        selected = !period.rolling,
                        onClick = { onChange(period.copy(rolling = false)) },
                        label = { Text("This week so far") },
                    )
                }
                PeriodGrain.MONTH -> {
                    FilterChip(
                        selected = period.rolling,
                        onClick = { onChange(period.copy(rolling = true)) },
                        label = { Text("Last 30 days") },
                    )
                    FilterChip(
                        selected = !period.rolling,
                        onClick = { onChange(period.copy(rolling = false)) },
                        label = { Text("This month so far") },
                    )
                }
                PeriodGrain.CUSTOM -> OutlinedButton(onClick = { picking = true }) { Text("Change dates") }
            }
        }
        Text(
            formatRange(range),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (picking) {
        DateRangeDialog(
            initial = range,
            onDismiss = { picking = false },
            onConfirm = { chosen ->
                picking = false
                onChange(period.copy(grain = PeriodGrain.CUSTOM, customStart = chosen.start, customEnd = chosen.end))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initial: DateRange,
    onDismiss: () -> Unit,
    onConfirm: (DateRange) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start.toUtcMillis(),
        initialSelectedEndDateMillis = initial.end.toUtcMillis(),
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = { if (start != null && end != null) onConfirm(DateRange(start.toUtcDate(), end.toUtcDate())) },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
    }
}

// The date picker reports each chosen day as midnight UTC.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
