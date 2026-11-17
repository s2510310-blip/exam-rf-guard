package com.classroom.rfdetector.core

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnapshotStoreTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun savedSnapshotsSurviveNewStoreInstanceWithNullFieldsAndUnicode() {
        val snapshot = SignalSnapshot("3층 제1시험실", 3000, true,
            listOf(SnapshotSignal("AA:BB:CC:DD:EE:FF", ScanSource.BLE, "이어폰", null, "Apple · 광고")))
        SnapshotStore(directory.root).save(listOf(snapshot))
        assertEquals(listOf(snapshot), SnapshotStore(directory.root).load())
        val next = snapshot.copy(capturedAtMs = 4000)
        SnapshotStore(directory.root).save(listOf(next, snapshot))
        assertEquals(listOf(next, snapshot), SnapshotStore(directory.root).load())
    }

    @Test fun missingFileIsAnEmptyArchive() {
        assertTrue(SnapshotStore(directory.root).load().isEmpty())
    }

    @Test fun presenceCompanyIdsAndHintsSurviveReopen() {
        val signal = SnapshotSignal("earbud", ScanSource.BLE, "AirPods Pro", -65.0, "Apple", SignalPresence.HISTORY,
            setOf(76), setOf(SignalHint.APPLE_AUDIO))
        val snapshot = SignalSnapshot("시험실", 40000, false, listOf(signal))
        SnapshotStore(directory.root).save(listOf(snapshot))
        assertEquals(snapshot, SnapshotStore(directory.root).load().single())
    }

    @Test fun oldVersionRemainsReadable() {
        File(directory.root, "signal-snapshots.json").writeText("""{"version":1,"snapshots":[{"id":"old","area":"시험실","label":"입실 전","capturedAtMs":2000,"scanStartedAtMs":0,"simulation":false,"signals":[{"address":"old","source":"BLE","name":"old","rssi":-65,"firstSeenMs":1000,"lastSeenMs":2000,"observations":3,"manufacturer":"unknown","model":null}]}]}""")
        val signal = SnapshotStore(directory.root).load().single().signals.single()
        assertEquals(SnapshotSignal("old", ScanSource.BLE, "old", -65.0, "unknown"), signal)
    }

    /** 2.1.0이 저장한 파일 그대로. 지금은 쓰지 않는 항목이 들어 있어도 읽혀야 한다. */
    @Test fun fileWrittenByRelease210RemainsReadable() {
        File(directory.root, "signal-snapshots.json").writeText("""{"version":2,"snapshots":[{"id":"3f0c","area":"3층 제1시험실","label":"빈 교실","capturedAtMs":5000,"scanStartedAtMs":1000,"simulation":false,"signals":[{"address":"AA:BB:CC:DD:EE:FF","source":"BLE","name":"AirPods Pro","rssi":-61.5,"firstSeenMs":1000,"lastSeenMs":4000,"observations":7,"manufacturer":"Apple, Inc. · BLE 광고","model":"AirPods Pro","presence":"QUIET","manufacturerIds":[76],"referenceRssiAt1m":null,"hints":["APPLE_AUDIO"],"periods":[{"first":1000,"last":4000,"count":7}]},{"address":"10:00:00:00:00:01","source":"WIFI","name":"이름 없는 AP","rssi":null,"firstSeenMs":1500,"lastSeenMs":1500,"observations":1,"manufacturer":"제조사 미확인","model":null,"presence":"HISTORY","manufacturerIds":[],"referenceRssiAt1m":null,"hints":[],"periods":[{"first":1500,"last":1500,"count":1}]}]}]}""")
        assertEquals(SignalSnapshot("3층 제1시험실", 5000, false, listOf(
            SnapshotSignal("AA:BB:CC:DD:EE:FF", ScanSource.BLE, "AirPods Pro", -61.5, "Apple, Inc. · BLE 광고", SignalPresence.QUIET, setOf(76), setOf(SignalHint.APPLE_AUDIO)),
            SnapshotSignal("10:00:00:00:00:01", ScanSource.WIFI, "이름 없는 AP", null, "제조사 미확인", SignalPresence.HISTORY),
        )), SnapshotStore(directory.root).load().single())
    }

    @Test(expected = IllegalArgumentException::class) fun unsupportedVersionIsNotSilentlyDiscarded() {
        File(directory.root, "signal-snapshots.json").writeText("{\"version\":99,\"snapshots\":[]}")
        SnapshotStore(directory.root).load()
    }

    @Test fun saveRemovesTemporaryFilesLeftByAnInterruptedWrite() {
        val stale = File(directory.root, "signal-snapshots-123456.tmp").apply { writeText("{\"version\":2,\"snapshots\":[") }
        val snapshot = SignalSnapshot("3층 제1시험실", 1000, false, listOf(SnapshotSignal("a", ScanSource.BLE, "n", -60.0, "m")))
        SnapshotStore(directory.root).save(listOf(snapshot))
        assertFalse(stale.exists())
        assertEquals(listOf("signal-snapshots.json"), directory.root.list()!!.toList())
        assertEquals(listOf(snapshot), SnapshotStore(directory.root).load())
    }
}
