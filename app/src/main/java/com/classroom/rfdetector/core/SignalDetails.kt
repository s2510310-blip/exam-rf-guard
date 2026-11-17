package com.classroom.rfdetector.core

import java.util.Locale

/**
 * 이름이 없는 전파도 판단할 수 있도록, 기기가 내보낸 나머지 정보를 "항목 → 값"으로 읽는다.
 * 모두 기기가 스스로 알린 값이라 바꾸거나 흉내 낼 수 있다.
 */

/** BLE 광고 원본을 [종류, 내용] 목록으로 나눈다. 잘린 구조는 버린다. */
fun adStructures(advertisement: ByteArray?): List<Pair<Int, ByteArray>> {
    if (advertisement == null) return emptyList()
    val structures = mutableListOf<Pair<Int, ByteArray>>()
    var index = 0
    while (index < advertisement.size) {
        val length = advertisement[index].toInt() and 0xFF
        if (length == 0 || index + length >= advertisement.size) break
        structures += (advertisement[index + 1].toInt() and 0xFF) to advertisement.copyOfRange(index + 2, index + 1 + length)
        index += length + 1
    }
    return structures
}

private fun u16(data: ByteArray, at: Int) = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)
private fun hex4(value: Int) = "0x" + value.toString(16).uppercase(Locale.ROOT).padStart(4, '0')

private val serviceNames = mapOf(
    0x180D to "심박 측정", 0x180F to "배터리 잔량", 0x1812 to "입력 장치(키보드·리모컨)", 0x1826 to "운동 기기",
    0xFE2C to "Google Fast Pair", 0xFDF0 to "보청기 연결", 0xFD6F to "접촉 알림", 0xFEED to "Tile 추적 태그", 0xFEAA to "Eddystone 비콘",
)

fun bleDetails(advertisement: ByteArray?, connectable: Boolean?, legacy: Boolean?, txPower: Int?): Map<String, String> {
    val details = LinkedHashMap<String, String>()
    val services = sortedSetOf<Int>()
    adStructures(advertisement).forEach { (type, data) ->
        when (type) {
            0x02, 0x03 -> (0 until data.size - 1 step 2).forEach { services += u16(data, it) }
            0x16 -> if (data.size >= 2) services += u16(data, 0)
            0x19 -> if (data.size >= 2) details["기기 종류 값"] = hex4(u16(data, 0))
        }
    }
    if (services.isNotEmpty()) details["서비스"] = services.joinToString(", ") { id -> serviceNames[id]?.let { "$it(${hex4(id)})" } ?: hex4(id) }
    connectable?.let { details["연결"] = if (it) "연결을 기다리는 중" else "연결을 받지 않음" }
    legacy?.let { details["광고 방식"] = if (it) "일반" else "확장(블루투스 5)" }
    txPower?.takeIf { it in -100..20 }?.let { details["기기가 알린 송신 출력"] = "$it dBm" }
    return details
}

/** WPS 정보 블록(제조사 고유 정보 00:50:F2:04)에서 공유기가 알린 제조사·모델·기기 이름을 읽는다. */
fun wpsDetails(vendorElement: ByteArray): Map<String, String> {
    val details = LinkedHashMap<String, String>()
    if (vendorElement.size < 4 || vendorElement[0] != 0x00.toByte() || vendorElement[1] != 0x50.toByte() ||
        vendorElement[2] != 0xF2.toByte() || vendorElement[3] != 0x04.toByte()) return details
    var index = 4
    while (index + 4 <= vendorElement.size) {
        val type = ((vendorElement[index].toInt() and 0xFF) shl 8) or (vendorElement[index + 1].toInt() and 0xFF)
        val length = ((vendorElement[index + 2].toInt() and 0xFF) shl 8) or (vendorElement[index + 3].toInt() and 0xFF)
        if (index + 4 + length > vendorElement.size) break
        val label = when (type) { 0x1021 -> "공유기가 알린 제조사"; 0x1023 -> "공유기가 알린 모델"; 0x1011 -> "공유기가 알린 기기 이름"; else -> null }
        val text = String(vendorElement, index + 4, length, Charsets.UTF_8).filter { !it.isISOControl() }.trim()
        if (label != null && text.isNotEmpty()) details[label] = text
        index += 4 + length
    }
    return details
}

fun wifiDetails(frequencyMhz: Int, capabilities: String?, standard: Int?): Map<String, String> {
    val details = LinkedHashMap<String, String>()
    val (band, channel) = when (frequencyMhz) {
        2484 -> "2.4GHz" to 14
        in 2412..2472 -> "2.4GHz" to (frequencyMhz - 2407) / 5
        5935 -> "6GHz" to 2 // 6GHz 채널 2(20MHz)만 5955 아래에 있다
        in 5160..5895 -> "5GHz" to (frequencyMhz - 5000) / 5
        in 5955..7115 -> "6GHz" to (frequencyMhz - 5950) / 5
        else -> null to null
    }
    if (band != null) details["주파수"] = "$band · 채널 $channel"
    capabilities?.let { caps ->
        details["보안"] = when {
            "SAE" in caps -> "WPA3"
            "WPA2" in caps || "RSN" in caps -> "WPA2"
            "WPA" in caps -> "WPA"
            "WEP" in caps -> "WEP(오래된 방식)"
            "OWE" in caps -> "암호 없음 · 통신은 암호화"
            else -> "암호 없음(열린 와이파이)"
        }
    }
    when (standard) { 4 -> "Wi-Fi 4"; 5 -> "Wi-Fi 5"; 6 -> "Wi-Fi 6"; 8 -> "Wi-Fi 7"; else -> null }?.let { details["규격"] = it }
    return details
}

/** 주소의 "로컬 관리" 비트. 휴대폰 핫스팟과 블루투스 임의 주소가 여기에 해당하며 제조사를 조회할 수 없다. */
fun isLocallyAdministered(address: String): Boolean? =
    address.take(2).toIntOrNull(16)?.takeIf { address.length == 17 }?.let { it and 2 != 0 }
