package dev.gianluca.alvoradaapp.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Reprodução do som do alarme.
 *
 * O detalhe que faz tudo funcionar é o `AudioAttributes` com `USAGE_ALARM`: ele
 * coloca o áudio no canal de volume de alarme, que o modo silencioso não silencia
 * e que o Não Perturbe libera por padrão. Tocar o mesmo arquivo com `USAGE_MEDIA`
 * resultaria num despertador mudo toda vez que o celular estivesse no silencioso.
 */
class AlarmSoundPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var escalationStep = 0

    fun start(
        soundUri: Uri? = null,
        volumePercent: Int = 100,
        escalate: Boolean = true,
        vibrate: Boolean = true,
        /** `false` na pré-escuta do editor: toca uma vez e para sozinho. */
        loop: Boolean = true,
    ) {
        stop()

        val uri = soundUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (uri == null) {
            Log.e(TAG, "Nenhum som de alarme disponível no aparelho")
            return
        }

        val target = (volumePercent.coerceIn(0, 100)) / 100f

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = loop
                if (!loop) setOnCompletionListener { stop() }
                prepare()
                val initial = if (escalate) target * ESCALATION_START else target
                setVolume(initial, initial)
                start()
            }
        } catch (e: Exception) {
            // Som personalizado apagado ou sem permissão de leitura: cai no padrão do
            // sistema em vez de deixar o despertador mudo.
            Log.e(TAG, "Falha ao tocar $uri, tentando som padrão", e)
            if (soundUri != null) {
                start(null, volumePercent, escalate, vibrate, loop)
                return
            }
        }

        if (escalate) scheduleEscalation(target)
        if (vibrate) startVibration()
    }

    /** Sobe o volume em degraus até o alvo — acordar sem susto, mas sem escapatória. */
    private fun scheduleEscalation(target: Float) {
        escalationStep = 0
        val tick = object : Runnable {
            override fun run() {
                val p = player ?: return
                escalationStep++
                val progress = (escalationStep.toFloat() / ESCALATION_STEPS).coerceAtMost(1f)
                val volume = target * (ESCALATION_START + (1f - ESCALATION_START) * progress)
                runCatching { p.setVolume(volume, volume) }
                if (progress < 1f) handler.postDelayed(this, ESCALATION_INTERVAL_MS)
            }
        }
        handler.postDelayed(tick, ESCALATION_INTERVAL_MS)
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching { vibrator?.vibrate(effect, attributes) }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { p ->
            runCatching {
                if (p.isPlaying) p.stop()
                p.release()
            }
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private companion object {
        const val TAG = "AlarmSoundPlayer"
        const val ESCALATION_START = 0.15f
        const val ESCALATION_STEPS = 10
        const val ESCALATION_INTERVAL_MS = 3_000L
        val VIBRATION_PATTERN = longArrayOf(0, 500, 500)
    }
}
