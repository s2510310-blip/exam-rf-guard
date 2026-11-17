package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class SignalGroupsTest {
    private val apple = setOf(76)
    private val tracker = DeviceTracker()

    private fun ble(address: String, atMs: Long, name: String? = null, rssi: Int = -60, ids: Set<Int> = apple, hints: Set<SignalHint> = setOf(SignalHint.APPLE_ACTIVE)) =
        tracker.update(RfObservation(address, ScanSource.BLE, name, rssi, null, false, atMs, ids, hints))

    private fun wifi(address: String, atMs: Long, name: String?, rssi: Int = -60) =
        tracker.update(RfObservation(address, ScanSource.WIFI, name, rssi, null, name == null, atMs))

    private fun baselineAt(nowMs: Long) = baselineGroupCounts(
        SignalSnapshot.capture("A", nowMs, false, tracker.snapshot(nowMs, includeInactive = true)))

    private fun view(baseline: Map<String, Int>, nowMs: Long, acknowledged: Set<String> = emptySet()) =
        watchView(tracker.snapshot(nowMs, includeInactive = true), baseline, acknowledged, nowMs)

    @Test fun sameDeviceWithAChangedAddressIsNotCountedAgain() {
        ble("AA:00:00:00:00:01", 0); ble("AA:00:00:00:00:02", 0)
        val baseline = baselineAt(1_000)
        assertEquals(mapOf("BLE:kind:76:APPLE_ACTIVE" to 2), baseline)

        // 15분 뒤: 한 대가 주소를 바꿨다. 옛 주소가 아직 "잡히는 중"으로 겹치는 동안에도 늘어난 것으로 보지 않는다.
        val later = 900_000L
        ble("AA:00:00:00:00:01", later); ble("AA:00:00:00:00:02", later - 5_000); ble("AA:00:00:00:00:03", later)
        assertEquals(0, view(baseline, later + 1_000).count)
        // 옛 주소가 사라지고 새 주소만 남아도 그대로다.
        ble("AA:00:00:00:00:01", later + 40_000); ble("AA:00:00:00:00:03", later + 40_000)
        val settled = view(baseline, later + 41_000)
        assertEquals(0, settled.count)
    }

    @Test fun oneMoreDeviceOfTheSameKindIsFlaggedOnceItPersists() {
        ble("AA:00:00:00:00:01", 0); ble("AA:00:00:00:00:02", 0)
        val baseline = baselineAt(1_000)
        val later = 600_000L
        listOf(0L, 10_000L, 20_000L, 30_000L, 40_000L).forEach { step ->
            ble("AA:00:00:00:00:01", later + step); ble("AA:00:00:00:00:02", later + step); ble("AA:00:00:00:00:09", later + step, rssi = -50)
        }
        val group = view(baseline, later + 41_000).bluetooth.single()
        assertEquals(2, group.baselineCount)
        assertEquals(3, group.devices.size)
        assertEquals("AA:00:00:00:00:09", group.strongest.key)
        assertTrue(group.alert)
    }

    @Test fun kindAbsentFromTheEmptyRoomShowsAtOnceAndAlertsAfterItPersists() {
        ble("AA:00:00:00:00:01", 0)
        val baseline = baselineAt(1_000)
        ble("BB:00:00:00:00:01", 100_000, name = "Galaxy Buds2 Pro", ids = setOf(117), hints = emptySet())
        val first = view(baseline, 101_000).bluetooth.single()
        assertEquals("BLE:name:galaxy buds2 pro", first.key)
        assertFalse(first.alert)
        ble("BB:00:00:00:00:01", 110_000, name = "Galaxy Buds2 Pro", ids = setOf(117), hints = emptySet())
        ble("BB:00:00:00:00:01", 120_000, name = "Galaxy Buds2 Pro", ids = setOf(117), hints = emptySet())
        ble("BB:00:00:00:00:01", 131_000, name = "Galaxy Buds2 Pro", ids = setOf(117), hints = emptySet())
        val persisted = view(baseline, 132_000)
        assertTrue(persisted.bluetooth.single().alert)
        // 사라지면 목록에서 빠진다. 누적하지 않는다.
        assertEquals(0, view(baseline, 200_000).count)
        // 확인함으로 표시한 묶음은 주소가 바뀌어도 다시 뜨지 않는다.
        ble("BB:00:00:00:00:77", 300_000, name = "Galaxy Buds2 Pro", ids = setOf(117), hints = emptySet())
        assertEquals(0, view(baseline, 301_000, acknowledged = setOf("BLE:name:galaxy buds2 pro")).count)
    }

    @Test fun wifiIsJudgedByNameAndNewNamesAlertImmediately() {
        wifi("10:00:00:00:00:01", 0, "SCHOOL")
        wifi("10:00:00:00:00:02", 0, null)
        val snapshot = SignalSnapshot.capture("A", 1_000, false, tracker.snapshot(1_000, includeInactive = true))
        val baseline = baselineGroupCounts(snapshot)
        assertEquals(mapOf("WIFI:name:school" to 1, "WIFI:hidden" to 1), baseline)
        // 같은 이름의 다른 주소(5GHz, 다른 층 공유기)와, 같은 공유기가 내는 이름 없는 주소 추가는 원래 있던 것이다.
        wifi("10:00:00:00:00:03", 100_000, "SCHOOL"); wifi("10:00:00:00:00:04", 100_000, null); wifi("10:00:00:00:00:01", 100_000, "SCHOOL")
        fun now(nowMs: Long) = watchView(tracker.snapshot(nowMs, includeInactive = true), baseline, emptySet(), nowMs, baselineWifiAddresses(snapshot))
        assertEquals(0, now(101_000).count)
        wifi("20:11:22:33:44:55", 130_000, "Galaxy S24 1234", rssi = -55)
        val hotspot = now(131_000).wifi.single()
        assertTrue(hotspot.alert)
        assertEquals(0, hotspot.baselineCount)
    }

    @Test fun hiddenHotspotIsFlaggedEvenWhenTheEmptyRoomAlreadyHadAHiddenNetwork() {
        wifi("10:00:00:00:00:02", 0, null, rssi = -50)
        val snapshot = SignalSnapshot.capture("A", 1_000, false, tracker.snapshot(1_000, includeInactive = true))
        val baseline = baselineGroupCounts(snapshot)
        val routers = baselineWifiAddresses(snapshot)
        // 빈 교실에 있던 이름 없는 와이파이는 계속 원래 있던 것이다.
        wifi("10:00:00:00:00:02", 100_000, null, rssi = -50)
        assertEquals(0, watchView(tracker.snapshot(101_000, includeInactive = true), baseline, emptySet(), 101_000, routers).count)
        // 전혀 다른 주소의 이름 없는 핫스팟은 이름이 숨겨져 있어도 새 전파다.
        wifi("da:a1:19:3b:7c:01", 100_000, null, rssi = -48)
        val flagged = watchView(tracker.snapshot(101_000, includeInactive = true), baseline, emptySet(), 101_000, routers).wifi.single()
        assertEquals("da:a1:19:3b:7c:01", flagged.strongest.key)
    }

    @Test fun confirmingOneHiddenNetworkDoesNotHideTheNextOne() {
        wifi("10:00:00:00:00:01", 0, "SCHOOL")
        val snapshot = SignalSnapshot.capture("A", 1_000, false, tracker.snapshot(1_000, includeInactive = true))
        val baseline = baselineGroupCounts(snapshot)
        val routers = baselineWifiAddresses(snapshot)
        fun now(nowMs: Long, acknowledged: Set<String>) =
            watchView(tracker.snapshot(nowMs, includeInactive = true), baseline, acknowledged, nowMs, routers)

        wifi("da:a1:19:3b:7c:01", 100_000, null, rssi = -50)
        val first = now(101_000, emptySet()).wifi.single().strongest
        // 감독관이 첫 번째 숨김 와이파이를 확인하고 목록에서 뺀다.
        val acknowledged = setOf(first.acknowledgeKey)
        assertEquals(0, now(101_000, acknowledged).count)

        // 나중에 다른 숨김 핫스팟이 켜지면 그것은 알린다.
        wifi("3e:55:01:9a:20:7f", 150_000, null, rssi = -52)
        assertEquals("3e:55:01:9a:20:7f", now(151_000, acknowledged).wifi.single().devices.single().key)
    }

    @Test fun hiddenNetworkFromARouterAlreadyInTheEmptyRoomIsNotNew() {
        wifi("b4:a9:4f:c7:31:a2", 0, "KT_GiGA_5G_319F", rssi = -41)
        val snapshot = SignalSnapshot.capture("A", 1_000, false, tracker.snapshot(1_000, includeInactive = true))
        val baseline = baselineGroupCounts(snapshot)
        val routers = baselineWifiAddresses(snapshot)
        // 실제로 잡힌 사례: 같은 공유기가 측정 뒤에 내보인 이름 없는 와이파이. 첫 자리만 다르다.
        wifi("c6:a9:4f:c7:31:a2", 100_000, null, rssi = -42)
        // 끝 자리만 다른 손님용 주소도 같은 공유기다.
        wifi("b4:a9:4f:c7:31:a7", 100_000, "KT_Guest", rssi = -45)
        // 전혀 다른 임의 주소의 이름 없는 와이파이는 새 전파다.
        wifi("da:a1:19:3b:7c:01", 100_000, null, rssi = -50)
        val devices = tracker.snapshot(101_000, includeInactive = true)
        assertEquals("Without the router list both would be flagged", 2, watchView(devices, baseline, emptySet(), 101_000).wifi.size)
        val view = watchView(devices, baseline, emptySet(), 101_000, routers)
        assertEquals("da:a1:19:3b:7c:01", view.wifi.single().strongest.key)
    }

    @Test fun knownListKeepsTheEmptyRoomOrderAndOnlyItsStateChanges() {
        wifi("10:00:00:00:00:01", 0, "SCHOOL", rssi = -70)
        ble("AA:00:00:00:00:01", 0, rssi = -50); ble("AA:00:00:00:00:02", 0, rssi = -80)
        ble("BB:00:00:00:00:01", 0, name = "Class TV", rssi = -60, ids = setOf(117), hints = emptySet())
        ble("CC:00:00:00:00:01", 0, hints = setOf(SignalHint.APPLE_FIND_MY))
        val snapshot = SignalSnapshot.capture("A", 1_000, false, tracker.snapshot(1_000, includeInactive = true))
        val counts = baselineGroupCounts(snapshot)
        val samples = baselineSamples(snapshot)
        val order = listOf("WIFI:name:school", "BLE:kind:76:APPLE_ACTIVE", "BLE:name:class tv")
        assertEquals("Wi-Fi first, then by strength when recorded; Find My beacons have their own bucket", order, samples.map { it.groupKey })
        fun rows(nowMs: Long) = watchView(tracker.snapshot(nowMs, includeInactive = true), counts, emptySet(), nowMs, baselineWifiAddresses(snapshot), samples).knownGroups

        val first = rows(1_000)
        assertEquals(order, first.map { it.key })
        assertEquals(listOf(1, 2, 1), first.map { it.live.size })
        assertEquals(2, first[1].baselineCount)

        // 10분 뒤: TV만 잡히고, Apple 기기는 주소를 바꿨고 훨씬 세졌다. 그래도 순서는 그대로다.
        ble("BB:00:00:00:00:01", 600_000, name = "Class TV", rssi = -90, ids = setOf(117), hints = emptySet())
        ble("AA:00:00:00:00:09", 600_000, rssi = -30)
        val later = rows(601_000)
        assertEquals(order, later.map { it.key })
        assertEquals(listOf(0, 1, 1), later.map { it.live.size })
        assertEquals("AA:00:00:00:00:09", later[1].live.single().key)
        assertNotNull("A row that is not received now still knows what it was", later[0].last)

        // 같은 공유기의 다른 주소가 뒤늦게 잡히면 맨 뒤에 붙고, 사라져도 줄은 남는다.
        wifi("12:00:00:00:00:01", 700_000, null)
        assertEquals(order + "WIFI:hidden", rows(701_000).map { it.key })
        val gone = rows(900_000)
        assertEquals(order + "WIFI:hidden", gone.map { it.key })
        assertEquals(0, gone.last().live.size)
    }

    @Test fun weakFindMyAndUnnamedSignalsGoToTheirOwnBuckets() {
        ble("AA:00:00:00:00:01", 0, ids = emptySet(), hints = emptySet())
        val baseline = baselineAt(1_000)
        assertEquals(mapOf("BLE:unknown" to 1), baseline)
        val now = 100_000L
        ble("CC:00:00:00:00:01", now, hints = setOf(SignalHint.APPLE_FIND_MY))
        ble("CC:00:00:00:00:02", now, hints = setOf(SignalHint.APPLE_FIND_MY))
        ble("DD:00:00:00:00:01", now, name = "Far Watch", rssi = -93, ids = emptySet(), hints = emptySet())
        ble("AA:00:00:00:00:05", now, ids = emptySet(), hints = emptySet())
        val result = view(baseline, now + 1_000)
        assertEquals(2, result.findMy.size)
        assertEquals("BLE:name:far watch", result.far.single().key)
        assertEquals(0, result.count)
    }

    @Test fun newHiddenNetworkIsNotListedAsOneThatWasThereBefore() {
        wifi("b4:a9:4f:c7:31:a2", 0, "KT_GiGA_5G_319F", rssi = -41)
        wifi("b4:a9:4f:c7:31:a3", 0, null, rssi = -41)
        val snapshot = SignalSnapshot.capture("A", 1_000, false, tracker.snapshot(1_000, includeInactive = true))
        val baseline = baselineGroupCounts(snapshot)
        val samples = baselineSamples(snapshot)
        // 빈 교실 공유기가 낸 이름 없는 와이파이와, 처음 보는 이름 없는 핫스팟이 함께 잡힌다.
        wifi("b4:a9:4f:c7:31:a3", 100_000, null, rssi = -41)
        wifi("da:a1:19:3b:7c:01", 100_000, null, rssi = -50)
        val view = watchView(tracker.snapshot(101_000, includeInactive = true), baseline, emptySet(), 101_000, baselineWifiAddresses(snapshot), samples)
        assertEquals("da:a1:19:3b:7c:01", view.wifi.single().strongest.key)
        val known = view.knownGroups.single { it.key == HIDDEN_WIFI_KEY }
        assertEquals(listOf("b4:a9:4f:c7:31:a3"), known.live.map { it.key })
    }
}
