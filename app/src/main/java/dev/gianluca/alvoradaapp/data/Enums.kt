package dev.gianluca.alvoradaapp.data

import kotlinx.serialization.Serializable

/** Ciclo de vida de um disparo concreto de um despertador. */
@Serializable
enum class OccurrenceStatus {
    SCHEDULED,
    FIRED,
    SNOOZED,
    DISMISSED,

    /** Tocou até o fim sem nenhuma interação. */
    MISSED,
}

/**
 * Ciclo de vida de uma missão num dia específico.
 *
 * PENDING → o alarme ainda não foi dispensado.
 * AWAITING_EVIDENCE → dispensado, relógio da janela de evidência correndo.
 * COMPLETED → cumprida (com foto, se exigida).
 * FAILED → o teto de cobranças estourou ou o dia virou sem conclusão.
 */
@Serializable
enum class MissionStatus {
    PENDING,
    AWAITING_EVIDENCE,
    COMPLETED,
    FAILED,
}

/** Origem de cada lançamento no extrato de pontos. */
@Serializable
enum class PointsReason {
    MISSION_ON_TIME,
    MISSION_LATE,
    SNOOZE_PENALTY,
    PERFECT_DAY,
    REWARD_PURCHASE,
    CHECKLIST_ITEM,
    CHECKLIST_COMPLETE,
}

/**
 * Ciclo de vida de uma rodada de checklist.
 *
 * OPEN → dentro da rodada, ainda dá para marcar.
 * COMPLETED → todos os itens marcados; o despertador de cada um fica calado até a
 *   rodada virar.
 * EXPIRED → a rodada acabou sem tudo marcado. Fechada pela varredura diária.
 *
 * Não existe estado de falha com peso: uma rodada expirada não custa nada além de
 * não ter rendido os pontos. É a mesma política de "missão não cumprida é
 * informação, não repreensão".
 */
@Serializable
enum class ChecklistCycleStatus {
    OPEN,
    COMPLETED,
    EXPIRED,
}
