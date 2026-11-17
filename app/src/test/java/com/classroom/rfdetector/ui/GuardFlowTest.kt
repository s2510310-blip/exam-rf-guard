package com.classroom.rfdetector.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.App
import com.classroom.rfdetector.AppViewModel
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 화면에는 연습 모드가 없으므로 테스트는 뷰모델에서 가상 전파를 켠다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuardFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun launch(): AppViewModel {
        compose.setContent { AppRfTheme { App() } }
        return ViewModelProvider(compose.activity)[AppViewModel::class.java].also { vm -> compose.runOnIdle { vm.setSim(true) } }
    }

    private fun recordEmptyRoom(vm: AppViewModel) {
        compose.onNodeWithText("빈 교실 측정 시작").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.devices.value.size == 5 }
        compose.onNodeWithText("빈 교실 기록 저장").assertIsDisplayed().performClick()
        waitForText("새 전파 없음")
    }

    private fun addEarbuds(vm: AppViewModel) {
        compose.runOnIdle { vm.simAddDevice(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)) }
        waitForText("새 전파 1개 발견")
    }

    @Test fun proctorRecordsEmptyRoomThenSeesAndLocatesNewSignal() {
        val vm = launch()
        try {
            saveScreen("guard-1-ready")
            recordEmptyRoom(vm)
            saveScreen("guard-3-clear")
            addEarbuds(vm)
            saveScreen("guard-4-found")
            compose.onNodeWithText("무선 이어폰").performClick()
            compose.onNodeWithText("가까울수록 빠른 진동").assertExists()
            compose.onNodeWithText("기기 정보").assertExists()
            compose.onNodeWithText("주소").assertExists()
            saveScreen("guard-5-find")
            compose.onNodeWithText("목록으로").performClick()
            assertNull(vm.findTarget.value)
            compose.onNodeWithText("감시 끝내기").performClick()
            compose.onNodeWithText("끝내기").performClick()
            compose.onNodeWithText("감시를 끝냈습니다").assertExists()
            saveScreen("guard-6-ended")
            compose.onNodeWithText("같은 빈 교실 기록으로 다시 감시").performClick()
            waitForText("새 전파 없음")
        } finally { vm.shutdown() }
    }

    @Test fun watchTimerDoesNotCountTheTimeSpentMeasuring() {
        val vm = launch()
        try {
            compose.onNodeWithText("빈 교실 측정 시작").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { vm.devices.value.size == 5 }
            Thread.sleep(3_200) // 측정에 3초 넘게 걸린 상황. 예전에는 감시 경과가 여기서부터 셌다.
            compose.onNodeWithText("빈 교실 기록 저장").assertIsDisplayed().performClick()
            waitForText("새 전파 없음")
            val text = compose.onNode(hasText("감시 중 · ", substring = true)).fetchSemanticsNode().config
                .let { it[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { t -> t.text } }
            val seconds = Regex("""감시 중 · (\d+)초 경과""").find(text)?.groupValues?.get(1)?.toInt()
            assertTrue("Watch time must restart at save, was: $text", seconds != null && seconds <= 1)
        } finally { vm.shutdown() }
    }

    @Test fun backDuringMeasuringOrWatchingAsksBeforeStopping() {
        val vm = launch()
        try {
            compose.onNodeWithText("빈 교실 측정 시작").performClick()
            compose.waitUntil(10_000) { vm.devices.value.isNotEmpty() }
            // Android 11 이하에서는 뒤로 가기가 앱을 닫아 측정·감시가 확인 없이 끝난다.
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("측정을 그만둘까요?").assertExists()
            compose.onNodeWithText("계속 측정").performClick()
            assertTrue(vm.scanning.value)

            compose.waitUntil(10_000) { vm.devices.value.size == 5 }
            compose.onNodeWithText("빈 교실 기록 저장").performClick()
            waitForText("새 전파 없음")
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("감시를 끝낼까요?").assertExists()
            compose.onNodeWithText("계속 감시").performClick()
            assertTrue(vm.scanning.value)
            assertTrue(!compose.activity.isFinishing)
        } finally { vm.shutdown() }
    }

    @Test fun confirmedSignalLeavesTheListAndLeavingTheAppKeepsWatching() {
        val vm = launch()
        try {
            recordEmptyRoom(vm)
            addEarbuds(vm)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("감시 끝내기").assertIsDisplayed()
            compose.onNodeWithText("새 전파 1개 발견").assertExists()
            compose.onNodeWithText("앱을 벗어나 있던 시간이 있습니다").assertExists()

            compose.onNodeWithText("무선 이어폰").performClick()
            compose.onNodeWithText("확인함\n목록에서 빼기").performClick()
            waitForText("새 전파 없음")
            assertEquals(1, vm.acknowledged.value.size)

            compose.onNodeWithText("빈 교실부터\n다시 측정").performClick()
            compose.onNodeWithText("다시 측정").performClick()
            waitForText("빈 교실 기록 저장") // 1단계 측정 화면으로 돌아왔다.
            assertNull(vm.baselineSnapshot.value)
            assertTrue(vm.acknowledged.value.isEmpty())
        } finally { vm.shutdown() }
    }

    @Test fun signalsFromTheEmptyRoomCanBeOpenedForDetails() {
        val vm = launch()
        try {
            recordEmptyRoom(vm)
            compose.onNodeWithText("원래 있던 전파 5종").performClick()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("스마트워치 (가상)", substring = true))
            compose.onNodeWithText("스마트워치 (가상)", substring = true).performClick()
            compose.onNodeWithText("빈 교실 기록에 있던 전파").assertExists()
            compose.onNodeWithText("기기 정보").assertExists()
            compose.onNodeWithText("확인함\n목록에서 빼기").assertDoesNotExist()
            compose.onNodeWithText("목록으로").performClick()
            compose.onNodeWithText("감시 끝내기").assertIsDisplayed()
        } finally { vm.shutdown() }
    }

    @Test fun guideExplainsWhatTheAppCannotCatch() {
        val vm = launch()
        try {
            compose.onNodeWithText("안내").performClick()
            compose.onNodeWithText("이 앱이 못 잡는 것").assertExists()
            compose.onNodeWithText("제대로 잡히는지 확인하기").assertExists()
            saveScreen("guide")
            compose.onNodeWithContentDescription("돌아가기").performClick()
            compose.onNodeWithText("빈 교실 측정 시작").assertIsDisplayed()
        } finally { vm.shutdown() }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun guardFlowStaysReachableOnSmallScreenWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) { AppRfTheme { App() } }
        }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        try {
            compose.runOnIdle { vm.setSim(true) }
            saveScreen("guard-small-ready")
            recordEmptyRoom(vm)
            addEarbuds(vm)
            saveScreen("guard-small-found")
            compose.onNodeWithText("감시 끝내기").assertIsDisplayed()
            compose.onNodeWithText("빈 교실부터\n다시 측정").assertIsDisplayed()
        } finally { vm.shutdown() }
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun saveScreen(name: String) {
        val target = File("build/outputs/ui-captures/$name.png")
        target.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull() ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
