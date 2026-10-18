package io.github.peicheng0413.classpass

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 掃到時的聲音與震動：成功嗶一聲＋短震；失敗是錯誤音＋兩下震動。 */
class Feedback(context: Context) {
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun ok() {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun error() {
        tone.startTone(ToneGenerator.TONE_SUP_ERROR, 500)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), -1))
    }

    fun release() = tone.release()
}
