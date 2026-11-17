package com.classroom.rfdetector.core

import java.util.Locale

/** 새 주소가 이 시간 넘게 이어져야 "늘어났다"고 본다. 주소가 바뀌는 순간 옛 주소와 겹치는 것을 거른다. */
const val ESTABLISHED_MS = 30_000L

/** 이름을 숨긴 와이파이 전부가 공유하는 묶음 키. 이름이 같은 것이 아니므로 이름처럼 "원래 있던 것"으로 보면 안 된다. */
const val HIDDEN_WIFI_KEY = "WIFI:hidden"

/**
 * 주소 대신 쓰는 묶음 기준. 블루투스 주소는 10~15분마다 바뀌므로 기기가 알린 이름,
 * 이름이 없으면 회사 코드와 종류 단서로 묶는다. 와이파이는 이름으로 묶는다.
 */
fun signalGroupKey(source: ScanSource, address: String, name: String, manufacturerIds: Set<Int>, hints: Set<SignalHint>): String {
    val advertised = advertisedName(address, name)
    return when {
        advertised != null -> "${source.name}:name:${advertised.lowercase(Locale.ROOT)}"
        source == ScanSource.WIFI -> HIDDEN_WIFI_KEY
        manufacturerIds.isEmpty() && hints.isEmpty() -> "BLE:unknown"
        else -> "BLE:kind:${manufacturerIds.sorted().joinToString(",")}:${hints.primary?.name.orEmpty()}"
    }
}

/** 기기가 실제로 알린 이름. 앱이 대신 붙인 이름(주소 끝자리 등)이면 null이다. */
fun advertisedName(address: String, name: String): String? {
    val tail = address.takeLast(5)
    return name.trim().takeUnless { it.isEmpty() || it == "이름 없는 AP" || it == "Wi-Fi 기기 $tail" || it == "BLE 기기 $tail" }
}

val TrackedDevice.groupKey: String get() = signalGroupKey(source, key, displayName, manufacturerIds, hints)

/**
 * "확인함"으로 목록에서 뺄 때 쓰는 키. 보통은 묶음 키지만, 이름 숨김 와이파이는 서로 다른 기기가 한 묶음에 모이므로
 * 주소마다 따로 뺀다. 묶음째 빼면 숨김 와이파이 하나를 확인한 뒤 새로 켜진 다른 숨김 핫스팟까지 알리지 않게 된다.
 */
val TrackedDevice.acknowledgeKey: String get() = groupKey.let { group ->
    if (group == HIDDEN_WIFI_KEY) "$HIDDEN_WIFI_KEY:${key.uppercase(Locale.ROOT)}" else group
}
val SnapshotSignal.groupKey: String get() = signalGroupKey(source, address, name, manufacturerIds, hints)

/** 빈 교실 기록에서 묶음마다 동시에 몇 개였는지. 저장 순간 잡히지 않던 묶음은 1개로 친다. */
fun baselineGroupCounts(baseline: SignalSnapshot): Map<String, Int> = baseline.signals.groupBy { it.groupKey }
    .mapValues { (_, signals) -> signals.count { it.presence != SignalPresence.HISTORY }.coerceAtLeast(1) }

/** 지금 잡히는 한 묶음. devices는 센 순서다. */
data class SignalGroup(val key: String, val devices: List<TrackedDevice>, val baselineCount: Int, val alert: Boolean) {
    val strongest: TrackedDevice get() = devices.first()
}

/**
 * "원래 있던 전파" 목록의 한 줄. 빈 교실 기록의 묶음마다 하나씩 있고 순서가 바뀌지 않는다.
 * 지금 잡히는지에 따라 live만 달라진다. sample은 빈 교실 기록의 대표, last는 가장 최근에 잡힌 주소다.
 */
data class KnownGroup(val key: String, val sample: SnapshotSignal?, val baselineCount: Int, val live: List<TrackedDevice>, val last: TrackedDevice?)

/** 빈 교실 기록의 묶음별 대표를 고정된 순서로: 와이파이 먼저, 그 안에서는 기록 당시 센 순서. */
fun baselineSamples(baseline: SignalSnapshot): List<SnapshotSignal> = baseline.signals
    .filterNot { it.source == ScanSource.BLE && it.hints.primary == SignalHint.APPLE_FIND_MY }
    .groupBy { it.groupKey }.values.map { group -> group.maxBy { it.rssi ?: Double.NEGATIVE_INFINITY } }
    .sortedWith(compareBy<SnapshotSignal> { it.source != ScanSource.WIFI }.thenByDescending { it.rssi ?: Double.NEGATIVE_INFINITY }.thenBy { it.groupKey })

/** 감시 화면에 보이는 것 전부. 새 전파는 지금 잡히는 것만 담고 누적하지 않는다. */
data class WatchView(
    val wifi: List<SignalGroup> = emptyList(),
    val bluetooth: List<SignalGroup> = emptyList(),
    val far: List<SignalGroup> = emptyList(),
    val findMy: List<TrackedDevice> = emptyList(),
    val knownGroups: List<KnownGroup> = emptyList(),
) {
    val count: Int get() = wifi.size + bluetooth.size
}

/** 빈 교실 기록에 있던 와이파이 주소. 같은 공유기가 내는 다른 주소를 알아보는 데 쓴다. */
fun baselineWifiAddresses(baseline: SignalSnapshot): Set<String> =
    baseline.signals.filter { it.source == ScanSource.WIFI }.map { it.address.uppercase(Locale.ROOT) }.toSet()

/**
 * 공유기 한 대는 이름별·대역별로 주소를 여러 개 쓰는데, 이 주소들은 첫 자리나 끝 자리만 다르다.
 * 빈 교실 기록의 주소와 나머지 다섯 자리가 같으면 같은 공유기가 낸 것으로 본다.
 */
fun isSameAccessPoint(address: String, baselineWifi: Set<String>): Boolean {
    val candidate = address.uppercase(Locale.ROOT)
    if (candidate in baselineWifi) return true
    if (candidate.length != 17) return false
    return baselineWifi.any { known -> known.length == 17 && (known.substring(3) == candidate.substring(3) || known.dropLast(2) == candidate.dropLast(2)) }
}

fun watchView(devices: List<TrackedDevice>, baseline: Map<String, Int>, acknowledged: Set<String>, nowMs: Long, baselineWifi: Set<String> = emptySet(),
    samples: List<SnapshotSignal> = emptyList()): WatchView {
    val live = devices.filter { it.presence != SignalPresence.HISTORY }
    // 전원을 끈 아이폰과 에어태그가 내는 신호. 제출한 휴대폰 수만큼 잡힐 수 있어 알리지 않고 따로 센다.
    val (findMy, others) = live.partition { it.source == ScanSource.BLE && it.hints.primary == SignalHint.APPLE_FIND_MY }
    // 이름을 숨긴 것까지 포함해, 빈 교실에 있던 공유기가 뒤늦게 내보인 다른 주소는 원래 있던 것이다.
    val rest = others.filterNot { it.source == ScanSource.WIFI && isSameAccessPoint(it.key, baselineWifi) }
        // 확인함으로 뺀 이름 숨김 와이파이는 그 주소만 뺀다.
        .filterNot { it.groupKey == HIDDEN_WIFI_KEY && it.acknowledgeKey in acknowledged }
    val flagged = mutableListOf<SignalGroup>()
    rest.groupBy { it.groupKey }.forEach { (key, members) ->
        val sorted = members.sortedByDescending { it.rssiEma ?: Double.NEGATIVE_INFINITY }
        val before = baseline[key] ?: 0
        val wifi = sorted.first().source == ScanSource.WIFI
        val established = members.count { nowMs - (it.periods.lastOrNull()?.firstSeenMs ?: it.firstSeenMs) >= ESTABLISHED_MS }
        val group = when {
            key in acknowledged -> null
            // 빈 교실에 없던 묶음은 바로 보인다. 와이파이 주소는 바뀌지 않으므로 바로 알린다.
            before == 0 -> SignalGroup(key, sorted, 0, alert = wifi || established > 0)
            // 이름을 숨긴 와이파이는 이름이 같다는 근거가 없다. 빈 교실 공유기의 주소는 위에서 이미 걸렀으므로
            // 남은 것은 모두 새 전파다. 주소가 바뀌지 않으므로 바로 알린다.
            wifi && key == HIDDEN_WIFI_KEY -> SignalGroup(key, sorted, 0, alert = true)
            // 같은 이름의 와이파이는 주소가 몇 개든 원래 있던 것이다.
            wifi -> null
            established > before -> SignalGroup(key, sorted, before, alert = true)
            else -> null
        }
        if (group != null) flagged += group
    }
    val byStrength = compareByDescending<SignalGroup> { it.strongest.rssiEma ?: Double.NEGATIVE_INFINITY }
    val (far, near) = flagged.sortedWith(byStrength).partition { (it.strongest.rssiEma ?: -127.0) < WEAK_SIGNAL_DBM }
    return WatchView(
        wifi = near.filter { it.strongest.source == ScanSource.WIFI },
        bluetooth = near.filter { it.strongest.source != ScanSource.WIFI },
        far = far,
        findMy = findMy,
        knownGroups = knownGroups(devices, baseline, acknowledged, baselineWifi, samples),
    )
}

/**
 * 빈 교실 기록의 묶음을 고정된 순서로 나열하고, 그 뒤에 감시 중 원래 있던 것으로 판단된 묶음
 * (같은 공유기의 다른 주소, 확인함으로 표시한 것)을 처음 잡힌 순서로 붙인다. 사라져도 줄은 남는다.
 */
private fun knownGroups(devices: List<TrackedDevice>, baseline: Map<String, Int>, acknowledged: Set<String>, baselineWifi: Set<String>,
    samples: List<SnapshotSignal>): List<KnownGroup> {
    val byStrength = compareByDescending<TrackedDevice> { it.rssiEma ?: Double.NEGATIVE_INFINITY }
    // 이름 숨김 와이파이는 한 묶음에 서로 다른 기기가 모이므로, 새로 잡혀 위에서 알리는 주소는 "원래 있던 것"에 넣지 않는다.
    fun isKnown(device: TrackedDevice) = device.groupKey != HIDDEN_WIFI_KEY || device.acknowledgeKey in acknowledged || isSameAccessPoint(device.key, baselineWifi)
    val members = devices.filterNot { it.source == ScanSource.BLE && it.hints.primary == SignalHint.APPLE_FIND_MY }.filter(::isKnown).groupBy { it.groupKey }
    fun row(key: String, sample: SnapshotSignal?): KnownGroup {
        val all = members[key].orEmpty()
        return KnownGroup(key, sample, baseline[key] ?: 0, all.filter { it.presence != SignalPresence.HISTORY }.sortedWith(byStrength), all.maxByOrNull { it.lastSeenMs })
    }
    val fromEmptyRoom = samples.map { row(it.groupKey, it) }
    val recorded = samples.map { it.groupKey }.toSet()
    val later = members.filter { (key, all) -> key !in recorded && (key in acknowledged || all.any { it.acknowledgeKey in acknowledged } || all.any { it.source == ScanSource.WIFI && isSameAccessPoint(it.key, baselineWifi) }) }
        .entries.sortedWith(compareBy({ entry -> entry.value.minOf { it.firstSeenMs } }, { it.key })).map { row(it.key, null) }
    return fromEmptyRoom + later
}
