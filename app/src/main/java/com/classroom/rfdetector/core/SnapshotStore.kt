package com.classroom.rfdetector.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import org.json.JSONArray
import org.json.JSONObject

/** Local, versioned storage. Atomic writes keep the previous file intact on failure. */
class SnapshotStore(private val directory: File) {
    private val file = File(directory, "signal-snapshots.json")

    fun load(): List<SignalSnapshot> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.getInt("version") in 1..2) { "지원하지 않는 스냅샷 파일 버전입니다." }
        val snapshots = root.getJSONArray("snapshots")
        return (0 until snapshots.length()).map { index -> decode(snapshots.getJSONObject(index)) }
    }

    fun save(snapshots: List<SignalSnapshot>) {
        val root = JSONObject().put("version", 2).put("snapshots", JSONArray().apply { snapshots.forEach { put(encode(it)) } })
        // 저장 도중 프로세스가 죽으면 임시 파일이 남는다. 주변 기기의 이름과 주소가 담긴 옛 빈 교실 기록이 계속 남지 않게 지운다.
        directory.listFiles { f -> f.name.startsWith("signal-snapshots-") && f.name.endsWith(".tmp") }?.forEach { it.delete() }
        val temporary = File.createTempFile("signal-snapshots-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(root.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun encode(snapshot: SignalSnapshot) = JSONObject().apply {
        put("area", snapshot.area)
        put("capturedAtMs", snapshot.capturedAtMs)
        put("simulation", snapshot.simulation)
        put("signals", JSONArray().apply { snapshot.signals.forEach { signal ->
            put(JSONObject().apply {
                put("address", signal.address); put("source", signal.source.name); put("name", signal.name)
                put("rssi", signal.rssi ?: JSONObject.NULL)
                put("manufacturer", signal.manufacturer)
                put("presence", signal.presence.name)
                put("manufacturerIds", JSONArray(signal.manufacturerIds.sorted()))
                put("hints", JSONArray(signal.hints.map { it.name }))
            })
        } })
    }

    private fun decode(json: JSONObject): SignalSnapshot {
        val rows = json.getJSONArray("signals")
        val signals = (0 until rows.length()).map { index -> rows.getJSONObject(index).let { row ->
            SnapshotSignal(row.getString("address"), ScanSource.valueOf(row.getString("source")), row.getString("name"),
                if (row.isNull("rssi")) null else row.getDouble("rssi"),
                row.getString("manufacturer"),
                SignalPresence.valueOf(row.optString("presence", SignalPresence.RECENT.name)),
                row.optJSONArray("manufacturerIds")?.let { ids -> (0 until ids.length()).map { ids.getInt(it) }.toSet() } ?: emptySet(),
                // 이 버전이 모르는 단서 이름은 버린다.
                row.optJSONArray("hints")?.let { names -> (0 until names.length()).mapNotNull { index ->
                    SignalHint.entries.firstOrNull { it.name == names.getString(index) }
                }.toSet() } ?: emptySet())
        } }
        return SignalSnapshot(json.getString("area"), json.getLong("capturedAtMs"),
            json.getBoolean("simulation"), signals)
    }
}
