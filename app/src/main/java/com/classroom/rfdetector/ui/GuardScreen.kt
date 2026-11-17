package com.classroom.rfdetector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.BuildConfig
import com.classroom.rfdetector.RadioProblem
import com.classroom.rfdetector.core.acknowledgeKey
import com.classroom.rfdetector.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class GuardPhase { READY, COLLECT, WATCH, ENDED }

private const val COLLECT_SECONDS = 120
private val okDim = Color(0xFF12301F)
private val warnDim = Color(0xFF3A2910)

/** 새 전파 0개일 때의 안내문. 멀리서 약하게 잡히는 새 전파가 있으면 "모두 빈 교실에 있던 것"이라고 하지 않는다. */
internal fun noNewSignalsText(farCount: Int): String =
    if (farCount > 0) "가까이에서 잡히는 새 전파는 없습니다. 멀리서 약하게 잡히는 새 전파 ${farCount}종은 아래 접힌 목록에 있습니다."
    else "지금 잡히는 전파는 모두 빈 교실 기록에 있던 것입니다."

private fun clock(timeMs: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
private fun minSec(seconds: Int) = if (seconds >= 60) "${seconds / 60}분 ${seconds % 60}초" else "${seconds}초"

/**
 * 감독관 화면. 빈 교실 기록 → 시험 중 감시의 두 단계만 있고,
 * 빈 교실에 없던 블루투스·와이파이가 지금 잡히는지를 보여 준다.
 */
@Composable
fun GuardScreen(vm: AppViewModel, permissionMissing: Boolean, onStart: () -> Unit, onRequestPermissions: () -> Unit, onOpenSettings: () -> Unit, onFind: (String) -> Unit) {
    val devices by vm.devices.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val simulation by vm.simMode.collectAsState()
    val baselineSet by vm.baselineSet.collectAsState()
    val baseline by vm.baselineSnapshot.collectAsState()
    val saved by vm.savedBaseline.collectAsState()
    val busy by vm.snapshotBusy.collectAsState()
    val error by vm.snapshotError.collectAsState()
    val session by vm.scanSession.collectAsState()
    val tick by vm.healthTick.collectAsState()
    val watch by vm.watch.collectAsState()
    val problems by vm.problems.collectAsState()
    val silenceMissing by vm.silenceMissing.collectAsState()
    val airplaneOff by vm.airplaneOff.collectAsState()
    var openGroup by remember { mutableStateOf<String?>(null) }
    var farOpen by rememberSaveable { mutableStateOf(false) }
    var findMyOpen by rememberSaveable { mutableStateOf(false) }
    var knownOpen by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }

    val phase = when {
        scanning && baselineSet -> GuardPhase.WATCH
        scanning -> GuardPhase.COLLECT
        baseline != null -> GuardPhase.ENDED
        else -> GuardPhase.READY
    }
    val elapsed = session?.let { ((maxOf(tick, it.startedAtMs) - it.startedAtMs) / 1000).toInt() } ?: 0
    // 빈 교실 기록은 2분 이상, 와이파이 탐색이 두 번 끝난 뒤에 저장한다. 와이파이를 못 쓰는 경우 3분 뒤에는 막지 않는다.
    val collected = simulation || elapsed >= COLLECT_SECONDS + 60 || (elapsed >= COLLECT_SECONDS && vm.wifi.completedScans >= 2)
    // 권한, 비행기 모드, 와이파이·블루투스·위치가 모두 갖춰져야 측정과 감시를 시작할 수 있다.
    // 디버그 빌드는 무선 ADB가 끊기지 않게 비행기 모드 단계만 건너뛴다. 측정 화면의 경고는 그대로 뜬다.
    val missing = if (simulation) emptyList() else listOfNotNull(
        SetupStep.PERMISSIONS.takeIf { permissionMissing },
        SetupStep.SILENCE.takeIf { silenceMissing },
        SetupStep.AIRPLANE.takeIf { airplaneOff && !BuildConfig.DEBUG },
        SetupStep.WIFI.takeIf { RadioProblem.WIFI_OFF in problems },
        SetupStep.BLUETOOTH.takeIf { RadioProblem.BLUETOOTH_OFF in problems },
        SetupStep.LOCATION.takeIf { RadioProblem.LOCATION_OFF in problems },
    )
    val blocked = missing.isNotEmpty()
    val toggle: (String) -> Unit = { key -> openGroup = if (openGroup == key) null else key }
    // 측정·감시 중이 아닐 때 빠진 준비가 있으면, 하나씩 마칠 때까지 준비 화면만 보여 준다.
    if (blocked && !scanning) {
        SetupFlow(missing, vm::checkExamMode, onRequestPermissions, onOpenSettings)
        return
    }
    // 측정·감시 중 뒤로 가기는 Android 11 이하에서 앱을 닫아, 확인 없이 측정·감시가 끝난다. 끝내기 버튼처럼 먼저 묻는다.
    BackHandler(enabled = scanning) { confirmStop = true }

    Column(Modifier.fillMaxSize()) {
        GuardSteps(phase)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (phase) {
                GuardPhase.READY -> {
                    item {
                        GuardPanel {
                            Text("수험생이 들어오기 전,\n빈 교실에서 시작하세요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("빈 교실의 블루투스와 와이파이를 먼저 기록합니다. 그 뒤에 새로 잡히는 것만 알려 드립니다.", color = RfColors.textDim)
                        }
                    }
                    item {
                        GuardPanel {
                            GuardHowTo("1", "빈 교실에서 2분 측정하고 기록 저장")
                            GuardHowTo("2", "시험 중에는 이 화면을 켜 두기 · 전원 버튼을 누르거나 다른 앱으로 가면 감시가 멈춤")
                            GuardHowTo("3", "새 전파가 뜨면 눌러서 다가가며 확인")
                        }
                    }
                    saved?.let { record ->
                        item {
                            OutlinedButton(onClick = { vm.useSavedBaseline(); onStart() }, modifier = Modifier.fillMaxWidth()) {
                                Text("${clock(record.capturedAtMs)}에 저장한 기록으로 바로 감시")
                            }
                        }
                    }
                }
                GuardPhase.COLLECT -> {
                    item {
                        GuardPanel {
                            Text(if (collected) "충분히 모았습니다" else "빈 교실 전파를 모으는 중", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GuardCount("블루투스", devices.count { it.source == ScanSource.BLE }, Modifier.weight(1f))
                                GuardCount("와이파이", devices.count { it.source == ScanSource.WIFI }, Modifier.weight(1f))
                            }
                            LinearProgressIndicator(progress = { (elapsed / COLLECT_SECONDS.toFloat()).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
                            Text(when {
                                collected -> "아래 버튼을 누르면 지금까지 잡힌 ${devices.size}개를 이 교실에 원래 있던 전파로 기록하고 감시를 시작합니다."
                                elapsed < COLLECT_SECONDS -> "${minSec(COLLECT_SECONDS - elapsed)} 남음. 교실 안을 천천히 한 바퀴 걸어 보세요. 늦게 잡히는 전파까지 기록해야 시험 중에 새 전파로 잘못 뜨지 않습니다."
                                else -> "와이파이 탐색이 끝나기를 기다리는 중입니다. 잠시 뒤 저장할 수 있습니다."
                            }, color = if (collected) RfColors.primary else RfColors.textDim)
                        }
                    }
                    problemItems(problems)
                    examModeItems(airplaneOff && !simulation, silenceMissing && !simulation)
                    item {
                        GuardPanel {
                            Text("지금 수험생이 있으면 안 됩니다", fontWeight = FontWeight.SemiBold)
                            Text("측정하는 동안 잡힌 전파는 모두 원래 있던 것으로 기록됩니다. 감독관 본인의 시계와 이어폰은 지금 켜 두면 함께 기록되어 나중에 알리지 않습니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { OutlinedButton(onClick = { vm.stopScan() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("초기화") } }
                }
                GuardPhase.WATCH, GuardPhase.ENDED -> {
                    val watching = phase == GuardPhase.WATCH
                    val found = watch.count > 0
                    item {
                        val incomplete = watching && problems.isNotEmpty()
                        Surface(color = if (!watching || incomplete) RfColors.surfaceAlt else if (found) warnDim else okDim, shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    val accent = if (!watching) RfColors.textDim else if (found || incomplete) RfColors.warning else RfColors.ok
                                    Box(Modifier.size(48.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                                        if (watching && found) Text("${watch.count}", color = RfColors.bg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                                        else if (incomplete) Text("!", color = RfColors.bg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                                        else RfGlyph(RfSymbol.CHECK, size = 28.dp, color = RfColors.bg)
                                    }
                                    Text(when {
                                        !watching -> "감시를 끝냈습니다"
                                        found -> "새 전파 ${watch.count}개 발견"
                                        incomplete -> "감시가 완전하지 않습니다"
                                        else -> "새 전파 없음"
                                    }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (watching) accent else RfColors.textPrimary)
                                }
                                Text(if (watching) "감시 중 · ${minSec(elapsed)} 경과" else "지금은 전파를 받지 않습니다")
                                if (watching) Text(when {
                                    found -> "빈 교실에 없던 전파가 지금 잡히고 있습니다. 줄을 눌러 다가가며 세기를 확인하세요. 사라지면 목록에서 빠집니다."
                                    incomplete -> "아래 문제를 해결하기 전까지는 전파가 있어도 표시되지 않을 수 있습니다."
                                    else -> noNewSignalsText(watch.far.size)
                                }, color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    examModeItems(airplaneOff && !simulation, silenceMissing && !simulation)
                    if (watching) {
                        item { Text("이 화면이 켜져 있는 동안에만 감시합니다. 화면은 자동으로 꺼지지 않으니 전원 버튼을 누르거나 다른 앱으로 가지 마세요. 충전기를 꽂아 두면 좋습니다.${if (silenceMissing || simulation) "" else " 감시 중에는 전화·알림·알람이 울리지 않게 휴대폰을 무음으로 바꿔 둡니다."}", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall) }
                        problemItems(problems)
                        session?.takeIf { it.pauses > 0 }?.let {
                            item { GuardNotice("앱을 벗어나 있던 시간이 있습니다", "${it.pauses}회, 총 ${minSec((it.pausedMs / 1000).toInt())} 동안은 전파를 받지 못했습니다. 화면을 끄거나 다른 앱을 쓰는 동안에는 감시가 멈추고, 돌아오면 자동으로 이어집니다.") }
                        }
                        item { GuardSectionTitle(RfSymbol.WIFI, "와이파이", if (watch.wifi.isEmpty()) "빈 교실에 없던 와이파이 없음" else "빈 교실에 없던 와이파이 ${watch.wifi.size}개") }
                        watch.wifi.forEach { group -> groupRows(group, openGroup == group.key, { toggle(group.key) }, onFind) }
                        item { GuardSectionTitle(RfSymbol.BLUETOOTH, "블루투스", if (watch.bluetooth.isEmpty()) "빈 교실 때보다 늘어난 기기 없음" else "빈 교실 때보다 늘어난 기기 ${watch.bluetooth.size}종") }
                        watch.bluetooth.forEach { group -> groupRows(group, openGroup == group.key, { toggle(group.key) }, onFind) }
                        if (watch.far.isNotEmpty()) item { FoldRow("멀리서 약하게 잡히는 것 ${watch.far.size}종", farOpen) { farOpen = !farOpen } }
                        if (farOpen) watch.far.forEach { group -> groupRows(group, openGroup == group.key, { toggle(group.key) }, onFind) }
                        if (watch.findMy.isNotEmpty()) item { FoldRow("꺼 둔 아이폰·에어태그 신호 ${watch.findMy.size}개", findMyOpen) { findMyOpen = !findMyOpen } }
                        if (findMyOpen) {
                            item { Text("전원을 끈 아이폰과 에어태그는 ‘나의 찾기’ 신호를 계속 냅니다. 꺼서 제출한 휴대폰도 여기에 잡히므로 알리지 않고 개수만 보여 줍니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall) }
                            items(watch.findMy, key = { "findmy-${it.trackingKey}" }) { device -> SignalCard(device) { onFind(device.trackingKey) } }
                        }
                        item { FoldRow("원래 있던 전파 ${watch.knownGroups.size}종", knownOpen) { knownOpen = !knownOpen } }
                        if (knownOpen) {
                            item { Text("빈 교실 기록 순서대로 고정해 둔 목록입니다. 순서는 바뀌지 않고, 지금 잡히는지만 표시가 바뀝니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall) }
                            watch.knownGroups.forEach { group -> knownRows(group, openGroup == "known-${group.key}", { toggle("known-${group.key}") }, onFind) }
                        }
                    }
                }
            }
            error?.let { message -> item { Text(message, color = RfColors.warning, style = MaterialTheme.typography.bodySmall) } }
        }
        Surface(color = RfColors.surface) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (phase == GuardPhase.WATCH || phase == GuardPhase.ENDED) Text("이 앱은 와이파이와 저전력 블루투스(BLE) 신호만 봅니다. 아무것도 잡히지 않아도 부정행위가 없다는 뜻은 아닙니다. LTE만 쓰는 기기는 보이지 않습니다.", color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
                when (phase) {
                    GuardPhase.READY -> MainButton("빈 교실 측정 시작", onClick = onStart)
                    GuardPhase.COLLECT -> MainButton(if (collected || elapsed >= COLLECT_SECONDS) "빈 교실 기록 저장" else "빈 교실 기록 저장 · ${minSec(COLLECT_SECONDS - elapsed)} 뒤",
                        enabled = devices.isNotEmpty() && !busy && collected) { vm.takeBaseline() }
                    GuardPhase.WATCH -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { confirmRestart = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text("빈 교실부터\n다시 측정") }
                        Button(onClick = { confirmStop = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RfColors.surfaceAlt, contentColor = RfColors.textPrimary)) { Text("감시 끝내기", fontWeight = FontWeight.Bold) }
                    }
                    GuardPhase.ENDED -> {
                        MainButton("같은 빈 교실 기록으로 다시 감시", onClick = onStart)
                        OutlinedButton(onClick = { vm.resetBaseline() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("빈 교실부터 다시 측정") }
                    }
                }
            }
        }
    }

    if (confirmRestart) AlertDialog(onDismissRequest = { confirmRestart = false }, title = { Text("빈 교실부터 다시 측정할까요?") },
        text = { Text("지금의 빈 교실 기록과 목록을 비우고 2분 측정부터 다시 합니다.\n\n수험생이 있는 상태에서 다시 측정하면 수험생의 기기가 원래 있던 전파로 기록되어, 이후에는 알리지 않습니다.") },
        confirmButton = { TextButton(onClick = { confirmRestart = false; if (blocked) { vm.stopScan(); vm.resetBaseline() } else vm.restartFromEmptyRoom() }) { Text("다시 측정") } },
        dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("취소") } })
    if (confirmStop && phase == GuardPhase.COLLECT) AlertDialog(onDismissRequest = { confirmStop = false }, title = { Text("측정을 그만둘까요?") },
        text = { Text("지금까지 모은 빈 교실 전파는 저장되지 않습니다.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; vm.stopScan() }) { Text("그만두기") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("계속 측정") } })
    else if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false }, title = { Text("감시를 끝낼까요?") },
        text = { Text("끝내면 전파를 더 받지 않습니다. 빈 교실 기록은 남아 있어 같은 기록으로 다시 감시할 수 있습니다.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; vm.stopScan() }) { Text("끝내기") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("계속 감시") } })
}

/** 한 전파에 다가가며 세기를 보는 화면. */
@Composable
fun FindScreen(vm: AppViewModel, trackingKey: String, onBack: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val geigerOn by vm.geigerOn.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val watch by vm.watch.collectAsState()
    val device = devices.firstOrNull { it.trackingKey == trackingKey }
    val close = { vm.findTarget.value = null; vm.setGeiger(false); onBack() }
    if (device == null) { LaunchedEffect(Unit) { close() }; return }
    val (kind, symbol) = plainKind(device)
    val live = device.presence != SignalPresence.HISTORY
    val accent = if (live) classAccent(device.distanceClass) else RfColors.textDim
    // 새 전파로 올라온 것만 "확인함"으로 뺄 수 있다. 원래 있던 전파는 뺄 목록이 없다.
    val flagged = (watch.wifi + watch.bluetooth + watch.far).any { it.key == device.groupKey }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).background(if (flagged) warnDim else RfColors.surfaceAlt, CircleShape), contentAlignment = Alignment.Center) { RfGlyph(symbol, size = 26.dp, color = if (flagged) RfColors.warning else RfColors.primary) }
                Column {
                    Text(cardLabels(device).first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!flagged) Text("빈 교실 기록에 있던 전파", color = RfColors.primary, style = MaterialTheme.typography.labelMedium)
                    Text(if (kind in unknownKinds) cardLabels(device).second else advertisedName(device.key, device.displayName)?.let { "기기가 알린 이름: $it" } ?: "이름을 알리지 않는 기기",
                        color = RfColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            kindNote(device)?.let { Text(it) }
            GuardPanel {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProximityBars(device, barWidth = 44.dp, maxHeight = 150.dp)
                    Text(proximityLabel(device), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = accent)
                    if (live) estimateDistance(device.rssiEma, device.referenceRssiAt1m)?.let { Text("${it.label} 추정", color = RfColors.textDim) }
                }
            }
            Text(if (live) "휴대폰을 들고 천천히 걸어 보세요. 막대가 늘어나는 쪽에 기기가 있습니다." else "지금은 잡히지 않습니다. 다시 나타나면 막대가 올라갑니다.")
            Surface(color = RfColors.surface, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("가까울수록 빠른 진동", Modifier.weight(1f))
                    Switch(checked = geigerOn, enabled = scanning, onCheckedChange = { on -> vm.findTarget.value = if (on) device.trackingKey else null; vm.setGeiger(on) })
                }
            }
            GuardPanel {
                Text("기기 정보", fontWeight = FontWeight.Bold)
                infoRows(device).forEach { (label, value) ->
                    Column {
                        Text(label, color = RfColors.textDim, style = MaterialTheme.typography.labelSmall)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("모두 기기가 스스로 알린 값이라 바꾸거나 흉내 낼 수 있습니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            }
            Text("${presenceText(device)} · 처음 잡힌 시각 ${clock(device.firstSeenMs)}" + if (device.periods.size > 1) " · 사라졌다 다시 나타남 ${device.periods.size - 1}회" else "", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            Text("거리는 전파 세기로 어림한 값이라 벽과 사람에 따라 달라집니다. 종류는 기기가 스스로 알린 정보로 추정한 것이며, 이 표시만으로 부정행위라고 판단하지 마세요.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
        }
        Surface(color = RfColors.surface) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (flagged) OutlinedButton(onClick = { vm.acknowledge(device.acknowledgeKey); close() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text("확인함\n목록에서 빼기") }
                Button(onClick = close, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RfColors.surfaceAlt, contentColor = RfColors.textPrimary)) { Text("목록으로", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** 사용 순서, 동작 확인 방법, 이 앱이 잡는 것과 못 잡는 것. */
@Composable
fun GuideScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GuardPanel {
            Text("사용 순서", fontWeight = FontWeight.Bold)
            GuardHowTo("1", "수험생이 들어오기 전, 빈 교실에서 2분 측정하고 기록을 저장합니다.")
            GuardHowTo("2", "시험 중에는 화면을 켜 둡니다. 빈 교실에 없던 전파가 잡히면 주황색으로 바뀝니다.")
            GuardHowTo("3", "줄을 눌러 걸어가며 막대가 늘어나는 쪽을 찾습니다.")
        }
        GuardPanel {
            Text("시험 중에는 화면을 끄지 마세요", fontWeight = FontWeight.Bold)
            Text("이 앱은 화면에 떠 있는 동안에만 전파를 받습니다. 측정과 감시 중에는 화면이 자동으로 꺼지지 않습니다.", style = MaterialTheme.typography.bodyMedium)
            GuideList("전원 버튼을 누르지 않습니다.", "다른 앱으로 가거나 홈 화면으로 나가지 않습니다.", "전화가 와서 화면이 바뀌지 않게 비행기 모드를 켜고, 블루투스와 와이파이만 다시 켭니다.", "화면을 계속 켜 두면 배터리가 빨리 닳으니 충전기를 연결하고 밝기를 낮춥니다.")
            Text("측정과 감시 중에는 전화·알림·알람이 울리지 않게 앱이 휴대폰을 무음(방해 금지)으로 바꾸고, 끝내면 원래대로 돌립니다. 권한, 비행기 모드, 와이파이·블루투스·위치 중 하나라도 빠져 있으면 ‘시작 전 준비’ 화면이 먼저 나오고, 모두 마쳐야 측정을 시작할 수 있습니다. 위치 찾기의 진동은 그대로 울립니다.", style = MaterialTheme.typography.bodyMedium)
            Text("화면이 꺼지거나 앱을 벗어난 동안의 전파는 기록되지 않습니다. 돌아오면 감시를 자동으로 이어 가고, 비어 있던 횟수와 시간을 감시 화면에 표시합니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
        }
        GuardPanel {
            Text("제대로 잡히는지 확인하기", fontWeight = FontWeight.Bold)
            Text("빈 교실 기록을 저장한 뒤 차례로 해 보세요.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            GuideList("무선 이어폰 케이스를 엽니다. 30초쯤 뒤 블루투스 칸에 뜹니다.", "다른 휴대폰의 핫스팟을 켭니다. 1분쯤 안에 와이파이 칸에 뜹니다.", "15분 넘게 그대로 둡니다. 개수가 늘지 않아야 합니다.", numbered = true)
            Text("확인을 마친 뒤 처음부터 다시 하려면 ‘빈 교실부터 다시 측정’을 누릅니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
        }
        GuardPanel {
            Text("이 앱이 보는 것", fontWeight = FontWeight.Bold)
            Text("와이파이와 저전력 블루투스(BLE)가 주변에 내보내는 알림 신호만 봅니다. 기기끼리 이미 연결되어 주고받는 내용은 보지 못합니다.", style = MaterialTheme.typography.bodyMedium)
        }
        Surface(color = okDim, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("잡는 것", fontWeight = FontWeight.Bold, color = RfColors.ok)
                GuideList("자체 와이파이를 여는 카메라", "핫스팟을 켠 휴대폰", "와이파이 에그(휴대용 와이파이)")
                Text("와이파이를 켜면 30초~1분 안에 보입니다. 빈 교실 기록에 없던 이름만 알리며, 이름과 설정에 따라 알리지 않는 경우가 있습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color = warnDim, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("때에 따라 잡는 것", fontWeight = FontWeight.Bold, color = RfColors.warning)
                GuideList("무선 이어폰", "스마트워치, 스마트 안경", "블루투스를 켜 둔 채 숨긴 휴대폰")
                Text("연결된 뒤에도 알림을 내는 제품(에어팟, 아이폰, 애플워치 등)은 계속 보입니다. 연결되면 알림을 멈추는 제품은 연결 전에만 보이고, 사라지면 목록에서도 빠집니다. 그래서 입실 직후와 시험 시작 직전에는 화면을 꼭 확인하세요.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color = RfColors.dangerDim, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("이 앱이 못 잡는 것", fontWeight = FontWeight.Bold, color = Color(0xFFFF9A9A))
                GuideList("블루투스와 와이파이를 끈 채 숨긴 휴대폰, LTE 모듈", "선으로 연결한 카메라, 저장만 하는 카메라", "목걸이와 귓속 초소형 수신기", "구형(클래식) 블루투스만 쓰는 기기", "화장실 등 교실 밖에서 쓰는 기기")
                Text("이것들은 전파 탐지기(LTE 송신), 금속 탐지기(휴대폰·모듈), 눈으로 하는 확인(귀·목 뒤·단추)으로 보완해야 합니다. 앱에 아무것도 잡히지 않아도 부정행위가 없다는 뜻은 아닙니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("새 전파는 확인이 필요하다는 표시일 뿐 부정행위의 증거가 아닙니다. 옆 교실, 복도, 감독관 본인의 기기도 잡힙니다.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
    }
}

/** 안내 화면의 목록. 항목이 두 줄로 넘어가도 표시(· 또는 번호) 아래로 글이 들어가지 않게 맞춘다. */
@Composable
private fun GuideList(vararg items: String, numbered: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, text ->
            Row {
                Text(if (numbered) "${index + 1}." else "·", Modifier.width(if (numbered) 24.dp else 14.dp), style = MaterialTheme.typography.bodyMedium)
                Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun plainKind(device: TrackedDevice) = plainKind(device.source, device.key, device.displayName, device.hints)

/** 종류는 기기가 스스로 알린 이름과 광고 데이터에서 읽은 추정이다. */
private fun plainKind(source: ScanSource, address: String, name: String, hints: Set<SignalHint>): Pair<String, RfSymbol> {
    val advertised = advertisedName(address, name)
    val audio = if (source == ScanSource.BLE) AudioNameCatalog.match(advertised) else null
    val wearable = if (source == ScanSource.BLE) WearableNameCatalog.match(advertised) else null
    val hint = hints.primary
    return when {
        source == ScanSource.WIFI -> (hint?.label ?: if (advertised == null) "이름을 숨긴 와이파이" else "와이파이 · 공유기나 휴대폰 핫스팟") to RfSymbol.WIFI
        audio?.kind == AudioKind.EARPHONES -> "무선 이어폰" to RfSymbol.EARPHONES
        audio?.kind == AudioKind.HEADPHONES -> "헤드폰" to RfSymbol.HEADPHONES
        audio != null -> "오디오 기기" to RfSymbol.HEADPHONES
        wearable?.kind == WearableKind.SMART_GLASSES -> "스마트 안경" to RfSymbol.GLASSES
        wearable?.kind == WearableKind.SMARTWATCH -> "스마트워치" to RfSymbol.WATCH
        wearable?.kind == WearableKind.CAMERA -> "카메라" to RfSymbol.CAMERA
        hint == null -> "블루투스 기기 · 종류 모름" to RfSymbol.BLUETOOTH
        hint == SignalHint.GLASSES -> hint.label to RfSymbol.GLASSES
        hint == SignalHint.WATCH -> hint.label to RfSymbol.WATCH
        hint in setOf(SignalHint.EARPHONES, SignalHint.APPLE_AUDIO, SignalHint.HEARING_AID, SignalHint.FAST_PAIR) -> hint.label to RfSymbol.EARPHONES
        else -> hint.label to RfSymbol.BLUETOOTH
    }
}

private val unknownKinds = setOf("블루투스 기기 · 종류 모름", "와이파이 · 공유기나 휴대폰 핫스팟")

/** 카드의 제목과 둘째 줄. 종류를 알면 종류가 제목이고, 모르면 기기가 알린 이름(없으면 제조사)이 제목이다. */
private fun cardLabels(kind: String, advertised: String?, maker: String?, wifi: Boolean): Pair<String, String> = when {
    kind !in unknownKinds -> kind to (advertised ?: maker ?: "이름 없음")
    advertised != null -> advertised to if (wifi) "와이파이" else "블루투스 · 종류 모름"
    maker != null -> maker to "이름 없음 · 제조사만 확인됨"
    else -> kind to "이름 없음"
}

private fun cardLabels(device: TrackedDevice) = cardLabels(plainKind(device).first, advertisedName(device.key, device.displayName), maker(device), device.source == ScanSource.WIFI)

private fun maker(device: TrackedDevice): String? = device.identity.advertisedCompanies.firstOrNull() ?: device.identity.macOrganization

/** 받을 수 있었던 정보 전부. 이름이 없는 전파도 이것으로 판단한다. */
private fun infoRows(device: TrackedDevice): List<Pair<String, String>> = buildList {
    val wifi = device.source == ScanSource.WIFI
    if (device.hints.isNotEmpty()) add("종류 단서" to device.hints.sortedBy { it.ordinal }.joinToString(" / ") { it.label })
    if (device.identity.advertisedCompanies.isNotEmpty()) add("제조사 · 블루투스 회사 코드" to device.identity.advertisedCompanies.joinToString(", "))
    add("제조사 · 주소 앞자리" to when {
        isLocallyAdministered(device.key) == true && wifi -> "임의 주소라 조회할 수 없음. 휴대폰 핫스팟이 주로 이런 주소를 씁니다."
        isLocallyAdministered(device.key) == true -> "임의 주소라 조회할 수 없음. 휴대폰과 이어폰은 주소를 주기적으로 바꿉니다."
        else -> device.identity.macOrganization ?: "등록부에 없음"
    })
    if (wifi && device.hidden) add("와이파이 이름" to "숨김")
    device.details.forEach { (label, value) -> add(label to value) }
    add("세기" to "지금 ${device.rssiEma?.let { "%.0f dBm".format(it) } ?: "모름"} · 가장 셀 때 ${device.peakRssi?.let { "$it dBm" } ?: "모름"}")
    add("받은 횟수" to "${device.observationCount}회 · 처음 ${clock(device.firstSeenMs)} · 마지막 ${device.lastSeenAgeSeconds}초 전")
    add("주소" to device.key)
}

/** 의심으로 읽히기 쉬운 종류에 붙이는 설명. */
private fun kindNote(device: TrackedDevice): String? = when {
    SignalHint.HEARING_AID in device.hints -> "보청기는 허용된 의료기기일 수 있습니다."
    SignalHint.APPLE_FIND_MY in device.hints -> "에어태그이거나, 전원을 끈 아이폰이 내는 신호일 수 있습니다. 꺼서 제출한 휴대폰도 이렇게 보일 수 있습니다."
    SignalHint.HOTSPOT in device.hints -> "와이파이 이름이 휴대폰 핫스팟의 기본 이름과 비슷합니다. 이름은 바꿀 수 있어 확정은 아닙니다."
    else -> null
}

private fun presenceText(device: TrackedDevice) = when (device.presence) {
    SignalPresence.RECENT -> "지금 잡히는 중"
    SignalPresence.QUIET -> "잠시 끊김"
    SignalPresence.HISTORY -> "사라짐"
}

private fun proximityLabel(device: TrackedDevice) = if (device.presence == SignalPresence.HISTORY) "안 잡힘" else device.distanceClass.label

private fun LazyListScope.problemItems(problems: List<RadioProblem>) {
    items(problems, key = { "problem-$it" }) { problem ->
        when (problem) {
            RadioProblem.BLUETOOTH_OFF -> GuardNotice("블루투스가 꺼져 있습니다", "휴대폰 위쪽을 쓸어내려 블루투스를 켜 주세요. 켜면 바로 다시 받습니다.")
            RadioProblem.WIFI_OFF -> GuardNotice("와이파이가 꺼져 있습니다", "켜야 핫스팟과 와이파이 카메라를 찾을 수 있습니다.")
            RadioProblem.LOCATION_OFF -> GuardNotice("위치(GPS)가 꺼져 있습니다", "Android는 위치가 꺼져 있으면 와이파이 목록을 주지 않습니다. 위치를 켜 주세요.")
        }
    }
}

/** 한 묶음에 주소가 하나면 바로 위치 찾기로, 여럿이면 펼쳐서 각각의 세기를 보여 준다. */
private fun LazyListScope.groupRows(group: SignalGroup, open: Boolean, onToggle: () -> Unit, onFind: (String) -> Unit) {
    val single = group.devices.size == 1
    item(key = "group-${group.key}") {
        SignalCard(group.strongest, when {
            single -> null
            group.baselineCount == 0 -> "같은 전파 ${group.devices.size}개 · ${if (open) "접기" else "펼치기"}"
            else -> "빈 교실 때 ${group.baselineCount}개 → 지금 ${group.devices.size}개 · ${if (open) "접기" else "펼치기"}"
        }) { if (single) onFind(group.strongest.trackingKey) else onToggle() }
    }
    if (open && !single) {
        item(key = "note-${group.key}") { Text("같은 종류라 어느 것이 새로 온 것인지는 구분할 수 없습니다. 가까이 잡히는 것부터 확인하세요.", color = RfColors.textDim, style = MaterialTheme.typography.bodySmall) }
        items(group.devices, key = { "member-${it.trackingKey}" }) { device ->
            Box(Modifier.padding(start = 18.dp)) { SignalCard(device) { onFind(device.trackingKey) } }
        }
    }
}

/** 원래 있던 전파 한 줄. 잡히는 주소가 하나면 바로 상세로, 여럿이면 펼친다. 안 잡히면 누를 것이 없다. */
private fun LazyListScope.knownRows(group: KnownGroup, open: Boolean, onToggle: () -> Unit, onFind: (String) -> Unit) {
    item(key = "known-${group.key}") {
        KnownCard(group, open) { if (group.live.size == 1) onFind(group.live.first().trackingKey) else if (group.live.size > 1) onToggle() }
    }
    if (open && group.live.size > 1) items(group.live, key = { "known-member-${it.trackingKey}" }) { device ->
        Box(Modifier.padding(start = 18.dp)) { SignalCard(device, calm = true) { onFind(device.trackingKey) } }
    }
}

@Composable
private fun KnownCard(group: KnownGroup, open: Boolean, onClick: () -> Unit) {
    val shown = group.live.firstOrNull() ?: group.last
    val sample = group.sample
    val (kind, symbol) = if (shown != null) plainKind(shown) else plainKind(sample!!.source, sample.address, sample.name, sample.hints)
    val (title, subtitle) = if (shown != null) cardLabels(shown)
        else cardLabels(kind, advertisedName(sample!!.address, sample.name), sample.manufacturer.takeUnless { it == "제조사 미확인" }, sample.source == ScanSource.WIFI)
    val live = group.live.isNotEmpty()
    val strongest = group.live.firstOrNull()
    Surface(onClick = onClick, enabled = live, color = RfColors.surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(RfColors.surfaceAlt, CircleShape), contentAlignment = Alignment.Center) {
                RfGlyph(symbol, size = 24.dp, color = if (live) RfColors.primary else RfColors.textDim)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = if (live) RfColors.textPrimary else RfColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = RfColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                if (group.live.size > 1 || group.baselineCount > 1) Text("빈 교실 때 ${group.baselineCount}개 · 지금 ${group.live.size}개" + if (group.live.size > 1) " · ${if (open) "접기" else "펼치기"}" else "",
                    color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (strongest != null) ProximityBars(strongest)
                Text(if (strongest != null) proximityLabel(strongest) else "안 잡힘", color = if (strongest != null) classAccent(strongest.distanceClass) else RfColors.textDim, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RfColors.primary, contentColor = RfColors.bg)) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GuardSteps(phase: GuardPhase) {
    // 감시를 끝낸 뒤에는 두 단계 모두 완료로 보여 준다.
    val current = when (phase) { GuardPhase.READY, GuardPhase.COLLECT -> 1; GuardPhase.WATCH -> 2; GuardPhase.ENDED -> 3 }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("빈 교실 기록", "시험 중 감시").forEachIndexed { index, title ->
            val step = index + 1
            val active = step == current
            Surface(color = if (active) MaterialTheme.colorScheme.secondaryContainer else RfColors.surface, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).semantics { contentDescription = "$step 단계 $title · ${if (active) "현재" else if (step < current) "완료" else "다음"}" }) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step < current) RfGlyph(RfSymbol.CHECK, size = 18.dp)
                    else Text("$step", color = if (active) RfColors.primary else RfColors.textDim, fontWeight = FontWeight.Bold)
                    Text(title, color = if (active) RfColors.primary else RfColors.textDim, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GuardPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = RfColors.surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun GuardNotice(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Surface(color = warnDim, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = RfColors.warning, fontWeight = FontWeight.Bold)
            Text(body, color = RfColors.warning, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

private fun LazyListScope.examModeItems(airplaneOff: Boolean, silenceMissing: Boolean) {
    if (airplaneOff) item(key = "exam-airplane") {
        val context = LocalContext.current
        GuardNotice("비행기 모드가 꺼져 있습니다", "전화나 문자가 오면 감시 화면이 가려지고 감시가 멈춥니다. 비행기 모드를 켠 뒤 와이파이와 블루투스만 다시 켜 주세요. 꺼져 있으면 측정을 시작할 수 없습니다.") {
            TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }) { Text("비행기 모드 설정 열기") }
        }
    }
    if (silenceMissing) item(key = "exam-silence") { SilenceNotice() }
}

@Composable
private fun SilenceNotice() {
    val context = LocalContext.current
    GuardNotice("휴대폰을 무음으로 바꿀 권한이 없습니다", "허용하면 측정과 감시 중에 전화·알림·알람이 울리지 않게 자동으로 무음으로 바꾸고, 끝내면 원래대로 돌립니다. 허용하기 전에는 측정을 시작할 수 없습니다.") {
        TextButton(onClick = { openSilenceAccess(context) }) { Text("방해 금지 권한 설정 열기") }
    }
}

/** 앱 목록이 아니라 이 앱의 허용 스위치 화면으로 바로 간다. 그 화면이 없는 기기에서는 목록을 연다. */
private fun openSilenceAccess(context: android.content.Context) {
    runCatching {
        context.startActivity(android.content.Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS", android.net.Uri.parse("package:${context.packageName}")))
    }.onFailure {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }
}

/** 측정을 시작하기 전에 갖춰야 하는 것. 이 순서대로 하나씩 마친다. 비행기 모드를 켜면 와이파이·블루투스가 꺼지므로 그 뒤에 다시 켠다. */
private enum class SetupStep(val title: String, val body: String, val action: String) {
    PERMISSIONS("근처 기기·위치 권한 허용", "이어폰·시계·휴대폰의 블루투스 신호와 와이파이 목록을 받는 데만 씁니다. 감독관의 위치를 기록하지 않고, 기록은 이 휴대폰 안에만 저장합니다.", "권한 허용"),
    SILENCE("방해 금지 권한 허용", "측정과 감시 중에 전화·알림·알람이 울리지 않게 자동으로 무음으로 바꾸고, 끝내면 원래대로 돌립니다.", "방해 금지 권한 설정 열기"),
    AIRPLANE("비행기 모드 켜기", "전화나 문자가 오면 감시 화면이 가려지고 감시가 멈춥니다.", "비행기 모드 설정 열기"),
    WIFI("와이파이 다시 켜기", "비행기 모드를 켜면 와이파이가 꺼집니다. 켜야 핫스팟과 와이파이 카메라를 찾을 수 있습니다.", "와이파이 설정 열기"),
    BLUETOOTH("블루투스 다시 켜기", "비행기 모드를 켜면 블루투스가 꺼집니다. 켜야 이어폰·시계·휴대폰을 찾을 수 있습니다.", "블루투스 설정 열기"),
    LOCATION("위치(GPS) 켜기", "Android는 위치가 꺼져 있으면 와이파이 목록을 주지 않습니다.", "위치 설정 열기"),
}

@Composable
private fun SetupFlow(missing: List<SetupStep>, onRecheck: () -> Unit, onRequestPermissions: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    // 알림창에서 켠 블루투스처럼 조금 늦게 반영되는 설정도 놓치지 않게 계속 다시 확인한다.
    LaunchedEffect(Unit) { while (true) { onRecheck(); kotlinx.coroutines.delay(1_000) } }
    val steps = SetupStep.entries
    val open = { action: String -> context.startActivity(android.content.Intent(action)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GuardPanel {
            Text("시작 전 준비", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("위에서부터 하나씩 마쳐야 측정을 시작할 수 있습니다. ${steps.size - missing.size}/${steps.size} 완료", color = RfColors.textDim)
        }
        steps.forEachIndexed { index, step ->
            if (step == missing.first()) GuardPanel {
                Text("${index + 1}. ${step.title}", color = RfColors.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(step.body, color = RfColors.textDim)
                MainButton(step.action) {
                    when (step) {
                        SetupStep.PERMISSIONS -> onRequestPermissions()
                        SetupStep.SILENCE -> openSilenceAccess(context)
                        SetupStep.AIRPLANE -> open(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                        SetupStep.WIFI -> open(android.provider.Settings.ACTION_WIFI_SETTINGS)
                        SetupStep.BLUETOOTH -> open(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                        SetupStep.LOCATION -> open(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    }
                }
                if (step == SetupStep.PERMISSIONS) TextButton(onClick = onOpenSettings) { Text("권한 창이 뜨지 않으면 앱 권한 설정 열기") }
            } else {
                val done = step !in missing
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).semantics { contentDescription = "${index + 1}단계 ${step.title} · ${if (done) "완료" else "다음"}" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (done) RfGlyph(RfSymbol.CHECK, size = 18.dp) else Text("${index + 1}", color = RfColors.textDim, fontWeight = FontWeight.Bold)
                    }
                    Text(step.title, color = RfColors.textDim)
                }
            }
        }
    }
}

@Composable
private fun GuardHowTo(step: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(RfColors.surfaceAlt, CircleShape), contentAlignment = Alignment.Center) { Text(step, color = RfColors.primary, fontWeight = FontWeight.Bold) }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GuardCount(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(color = RfColors.surfaceAlt, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = RfColors.textDim, style = MaterialTheme.typography.labelMedium)
            Text("${count}개", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun GuardSectionTitle(symbol: RfSymbol, title: String, summary: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RfGlyph(symbol, size = 20.dp)
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text(summary, color = RfColors.textDim, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FoldRow(title: String, open: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (open) "펼침" else "접힘" }) {
        Text(title, Modifier.weight(1f), color = RfColors.textDim)
        Text(if (open) "▴" else "▾", color = RfColors.textDim)
    }
}

@Composable
private fun ProximityBars(device: TrackedDevice, barWidth: Dp = 7.dp, maxHeight: Dp = 28.dp) {
    val filled = if (device.presence == SignalPresence.HISTORY) 0 else when (device.distanceClass) {
        DistanceClass.VERY_CLOSE -> 4; DistanceClass.CLOSE -> 3; DistanceClass.NEAR -> 2; DistanceClass.FAR -> 1; DistanceClass.UNKNOWN -> 0
    }
    Row(Modifier.semantics { contentDescription = "가까운 정도 4단계 중 $filled" }, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(barWidth / 2)) {
        repeat(4) { index ->
            Box(Modifier.width(barWidth).height(maxHeight * (index + 1) / 4).background(if (index < filled) classAccent(device.distanceClass) else RfColors.surfaceAlt, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun SignalCard(device: TrackedDevice, groupNote: String? = null, calm: Boolean = false, onClick: () -> Unit) {
    val symbol = plainKind(device).second
    val (title, subtitle) = cardLabels(device)
    val live = device.presence != SignalPresence.HISTORY
    Surface(onClick = onClick, color = RfColors.surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 원래 있던 전파는 주황색으로 강조하지 않는다.
            Box(Modifier.size(44.dp).background(if (live && !calm) warnDim else RfColors.surfaceAlt, CircleShape), contentAlignment = Alignment.Center) {
                RfGlyph(symbol, size = 24.dp, color = if (!live) RfColors.textDim else if (calm) RfColors.primary else RfColors.warning)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(groupNote ?: "$subtitle · ${clock(device.firstSeenMs)}부터", color = RfColors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProximityBars(device)
                Text(proximityLabel(device), color = if (live) classAccent(device.distanceClass) else RfColors.textDim, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
