package dev.gianluca.alvoradaapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A geometria quadrada da V2.
 *
 * O Material 3 arredonda muito por padrão — 12dp no `medium`, 16dp no `large`, e
 * botões e chips totalmente arredondados. Trocar as cinco medidas aqui re-veste
 * `Card`, `TopAppBar`, `Chip`, `Button`, `AlertDialog`, `TextField`, `DropdownMenu`
 * e o menu lateral de uma vez, sem tocar em nenhuma tela.
 *
 * Não é canto vivo: 2–8dp continua absorvendo o serrilhado da diagonal e evita o
 * ar de protótipo que o raio zero dá numa tela pequena.
 */
val AlvoradaShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)
