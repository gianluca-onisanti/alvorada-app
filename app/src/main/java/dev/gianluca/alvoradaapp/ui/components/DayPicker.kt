package dev.gianluca.alvoradaapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gianluca.alvoradaapp.core.DayMask
import java.time.DayOfWeek

/**
 * Seletor de dias da semana.
 *
 * [selectableDays] restringe quais dias podem ser marcados. Na Fase 2 é o que garante
 * a invariante do schema — uma missão não pode existir num dia em que o despertador
 * não toca — fazendo a UI impedir o estado inválido em vez de validá-lo depois.
 */
@Composable
fun DayPicker(
    mask: Int,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    selectableDays: Int = DayMask.EVERY_DAY,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            val selectable = DayMask.contains(selectableDays, day)
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = DayMask.contains(mask, day),
                onClick = { onToggle(day) },
                enabled = selectable,
                label = { Text(DayMask.shortLabel(day).take(1)) },
            )
        }
    }
}
