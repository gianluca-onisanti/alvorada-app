package dev.gianluca.alvoradaapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens de cor do app.
 *
 * O roxo é a identidade e não se mexe: [Purple] continua sendo o primary do tema
 * escuro e [PurpleDeep] o do claro, os mesmos da V1. O registro futurista da V2 vem
 * de outro lugar — cantos quadrados, tipografia angular, superfícies translúcidas
 * com fio de contorno — e não de acrescentar uma cor nova que competiria com o
 * significado que verde e âmbar já carregam nas telas.
 *
 * O fundo escuro desceu de `#120E22` para [InkBase] de propósito: o gradiente de
 * fundo precisa de espaço para subir até a família de superfícies sem clarear demais.
 * Sem variação no fundo, "translúcido" não se lê — fica só um cartão apagado.
 */

// ---- identidade

/** O roxo do app. Primary do tema escuro. */
val Purple = Color(0xFF7C5CFF)

/** Roxo mais escuro, legível sobre fundo claro. Primary do tema claro. */
val PurpleDeep = Color(0xFF5B3FD9)

/** Roxo claro, para o fio de contorno e o brilho das superfícies de vidro. */
val PurpleGlow = Color(0xFFA78BFF)

/** Verde de "cumprido". Secondary nos dois temas. */
val Mint = Color(0xFF3DDC97)
val MintDeep = Color(0xFF1F9E6E)

/** Âmbar de destaque. Tertiary no escuro; no claro ele vira container, não papel de texto. */
val Amber = Color(0xFFFFB020)

// ---- família ink (tema escuro)

val InkBase = Color(0xFF0B0716)
val InkSurface = Color(0xFF120E22)
val InkElevated = Color(0xFF1E1834)
val InkVariant = Color(0xFF241C42)
val InkText = Color(0xFFEDE9F7)
val InkTextMuted = Color(0xFFB9B0D4)

// ---- vidro
//
// A translucidez é composta em tempo de desenho a partir da paleta do tema, e não
// fixada aqui, para funcionar nos dois temas com um valor só. Ver `GlassCard`.

/** Opacidade da superfície de vidro sobre o fundo. */
const val GLASS_ALPHA = 0.62f

/** Opacidade do fio de contorno que dá a borda do vidro. */
const val GLASS_BORDER_ALPHA = 0.22f

/** Opacidade do realce no topo da superfície, que sugere a espessura do vidro. */
const val GLASS_SHEEN_ALPHA = 0.10f
