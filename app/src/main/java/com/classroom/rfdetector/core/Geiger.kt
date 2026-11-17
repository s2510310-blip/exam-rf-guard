package com.classroom.rfdetector.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 지예거 모드: 화면을 안 봐도 진동 주기로 근접을 알린다.
 * 바로 옆 350ms / 가까움 800ms / 중간 1.6s / 멀다 3s 주기로 짧게 울린다.
 */
class GeigerController(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    private companion object {
        /** 등급이 바뀌었는지 이 간격으로 확인한다. 주기는 바꾸지 않는다. */
        const val TICK_MS = 50L
    }

    @Volatile
    private var cls: DistanceClass = DistanceClass.UNKNOWN

    fun setClass(c: DistanceClass) {
        cls = c
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val pacer = GeigerPacer()
            while (isActive) {
                val c = cls
                if (pacer.due(c, SystemClock.elapsedRealtime())) {
                    val dur = if (c == DistanceClass.VERY_CLOSE) 130L else 60L
                    runCatching {
                        vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { vibrator.cancel() }
    }
}

/**
 * 마지막 진동 뒤 흐른 시간이 현재 등급의 주기에 이르면 울릴 차례로 본다.
 * 주기를 통째로 자고 나서 등급을 읽으면, 멀리(3초) 있다가 다가가도 최대 3초 동안 이전 간격대로 기다린다.
 */
class GeigerPacer {
    private var lastPulseMs: Long? = null

    fun due(cls: DistanceClass, nowMs: Long): Boolean {
        val period = when (cls) {
            DistanceClass.VERY_CLOSE -> 350L
            DistanceClass.CLOSE -> 800L
            DistanceClass.NEAR -> 1600L
            DistanceClass.FAR -> 3000L
            DistanceClass.UNKNOWN -> return false
        }
        val last = lastPulseMs
        if (last != null && nowMs - last < period) return false
        lastPulseMs = nowMs
        return true
    }
}
