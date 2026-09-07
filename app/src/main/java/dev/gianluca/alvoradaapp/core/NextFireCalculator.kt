package dev.gianluca.alvoradaapp.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * O que vai acontecer no próximo toque de um despertador, já considerando um pulo.
 *
 * Os dois campos existem porque a tela precisa dizer duas coisas ao mesmo tempo: qual
 * toque foi dispensado e quando o despertador volta. Devolver só o instante final
 * apagaria a diferença entre "pulei amanhã" e "não há pulo nenhum".
 */
data class FireOutlook(
    /** Quando ele realmente toca. `null` = nunca mais. */
    val nextFire: Long?,
    /** O toque que está sendo pulado, ou `null` se nenhum pulo está em vigor. */
    val skipped: Long?,
    /**
     * Até quando um checklist o está calando, ou `null` se não está.
     *
     * Separado de [skipped] porque a tela precisa dizer coisas diferentes: "você
     * pulou o toque de amanhã" e "o checklist já foi preenchido, ele volta domingo"
     * não são a mesma frase, e a segunda não deve oferecer o botão de desfazer o
     * pulo.
     */
    val suppressedUntil: Long? = null,
) {
    val isSkipping: Boolean get() = skipped != null
    val isSuppressed: Boolean get() = suppressedUntil != null

    companion object {
        val NONE = FireOutlook(nextFire = null, skipped = null)
    }
}

/**
 * Cálculo do próximo disparo de um despertador recorrente.
 *
 * Puro de propósito: é a única peça do agendamento que dá para testar sem aparelho,
 * e é onde moram os erros que só aparecem meses depois (virada de mês, semana que
 * dá a volta, horário de verão).
 */
object NextFireCalculator {

    /**
     * Até onde procurar um dia válido.
     *
     * Sete dias bastavam quando toda repetição era semanal. Com dias do calendário e
     * ocorrências do mês, os intervalos ficam bem maiores: "dia 31" pula fevereiro
     * inteiro, "5ª sexta" pode faltar por três meses seguidos. Um ano e meio de margem
     * cobre qualquer combinação com folga, e o laço é aritmética de datas — a varredura
     * inteira custa menos que uma única consulta ao banco.
     */
    private const val SCAN_DAYS = 550L

    /**
     * Primeiro instante estritamente depois de [from] em que o alarme deve tocar,
     * ou `null` se a recorrência nunca dispara.
     *
     * Horário de verão: num dia de adiantamento o horário marcado pode simplesmente
     * não existir. `atZone` resolve empurrando para a frente (02:30 vira 03:30), que
     * é o comportamento certo para um despertador — melhor tocar depois do que não tocar.
     */
    fun nextFire(
        hour: Int,
        minute: Int,
        recurrence: Recurrence,
        from: ZonedDateTime,
    ): ZonedDateTime? {
        if (!recurrence.canFire) return null
        val time = LocalTime.of(hour, minute)

        // Data única não precisa de varredura — e, sem este atalho, uma data além do
        // horizonte de [SCAN_DAYS] simplesmente não seria encontrada.
        if (recurrence.kind == RepeatKind.ONCE) {
            val single = (recurrence.anchorDate ?: return null).atTime(time).atZone(from.zone)
            return if (single.isAfter(from)) single else null
        }

        val today = from.toLocalDate()
        for (offset in 0L..SCAN_DAYS) {
            val date = today.plusDays(offset)
            if (!recurrence.matches(date)) continue
            val candidate = date.atTime(time).atZone(from.zone)
            // `isAfter` é estrito: reagendar no exato instante do disparo não pode
            // devolver o mesmo horário e criar um laço infinito.
            if (candidate.isAfter(from)) return candidate
        }
        return null
    }

    /** Atalho para a repetição semanal simples, que é o caso da maioria dos alarmes. */
    fun nextFire(hour: Int, minute: Int, daysMask: Int, from: ZonedDateTime): ZonedDateTime? =
        nextFire(hour, minute, Recurrence.weekly(daysMask), from)

    /** Conveniência para o agendador, que trabalha em epoch millis. */
    fun nextFireMillis(
        hour: Int,
        minute: Int,
        recurrence: Recurrence,
        from: ZonedDateTime,
    ): Long? = nextFire(hour, minute, recurrence, from)?.toInstant()?.toEpochMilli()

    fun nextFireMillis(hour: Int, minute: Int, daysMask: Int, from: ZonedDateTime): Long? =
        nextFire(hour, minute, daysMask, from)?.toInstant()?.toEpochMilli()

    /**
     * Próximo disparo com um toque pulado, para "desligar só a próxima ativação".
     *
     * [skipFireAt] só vale se bater **exatamente** com o toque que aconteceria agora.
     * Essa exigência é o que faz o pulo se desfazer sozinho em todos os caminhos que
     * importam: o horário passou, o despertador foi reconfigurado, a recorrência mudou.
     * Um pulo guardado como "está pulando: sim" precisaria ser limpo à mão em cada um
     * desses casos — e o esquecido viraria um despertador que não toca sem explicação.
     */
    fun outlook(
        hour: Int,
        minute: Int,
        recurrence: Recurrence,
        from: ZonedDateTime,
        skipFireAt: Long?,
        suppressedUntil: Long? = null,
    ): FireOutlook {
        // A supressão por checklist é avaliada primeiro, e simplesmente adianta o
        // ponto de partida da busca: todo disparo anterior a ela deixa de existir.
        // Vale para vários toques, e não para um — um checklist semanal num
        // despertador diário tem sete para calar.
        val suppressing = suppressedUntil != null && suppressedUntil > from.toInstant().toEpochMilli()
        val searchFrom = if (suppressing) {
            Instant.ofEpochMilli(suppressedUntil!!).atZone(from.zone)
        } else {
            from
        }

        val first = nextFireMillis(hour, minute, recurrence, searchFrom)
            ?: return FireOutlook.NONE
        val suppressedMark = suppressedUntil.takeIf { suppressing }

        if (skipFireAt == null || skipFireAt != first) {
            return FireOutlook(first, null, suppressedMark)
        }

        val after = nextFireMillis(
            hour = hour,
            minute = minute,
            recurrence = recurrence,
            from = Instant.ofEpochMilli(first).atZone(from.zone),
        )
        return FireOutlook(nextFire = after, skipped = first, suppressedUntil = suppressedMark)
    }
}
