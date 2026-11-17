package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class SignalDetailsTest {
    private fun ad(type: Int, vararg data: Int) = byteArrayOf((data.size + 1).toByte(), type.toByte()) + data.map { it.toByte() }

    @Test fun unnamedBluetoothStillShowsServicesKindValueAndConnectionState() {
        val details = bleDetails(ad(0x01, 0x06) + ad(0x03, 0x0D, 0x18, 0x34, 0x12) + ad(0x16, 0x2C, 0xFE, 0x01) + ad(0x19, 0xC1, 0x00),
            connectable = true, legacy = false, txPower = -4)
        assertEquals("0x1234, 심박 측정(0x180D), Google Fast Pair(0xFE2C)", details["서비스"])
        assertEquals("0x00C1", details["기기 종류 값"])
        assertEquals("연결을 기다리는 중", details["연결"])
        assertEquals("확장(블루투스 5)", details["광고 방식"])
        assertEquals("-4 dBm", details["기기가 알린 송신 출력"])
    }

    @Test fun nothingIsInventedWhenTheAdvertisementSaysNothing() {
        assertTrue(bleDetails(null, null, null, null).isEmpty())
        assertEquals(mapOf("연결" to "연결을 받지 않음", "광고 방식" to "일반"), bleDetails(byteArrayOf(9, 0x19, 1), false, true, 127))
    }

    @Test fun wifiBandChannelSecurityAndStandardAreRead() {
        assertEquals(mapOf("주파수" to "2.4GHz · 채널 6", "보안" to "WPA2", "규격" to "Wi-Fi 4"), wifiDetails(2437, "[WPA2-PSK-CCMP][ESS]", 4))
        assertEquals(mapOf("주파수" to "5GHz · 채널 36", "보안" to "WPA3", "규격" to "Wi-Fi 6"), wifiDetails(5180, "[RSN-SAE-CCMP][ESS]", 6))
        assertEquals(mapOf("주파수" to "6GHz · 채널 1", "보안" to "암호 없음(열린 와이파이)"), wifiDetails(5955, "[ESS]", null))
        assertEquals(mapOf("보안" to "WEP(오래된 방식)"), wifiDetails(0, "[WEP][ESS]", 1))
    }

    @Test fun sixGhzChannelTwoAt5935MhzIsRead() {
        assertEquals(mapOf("주파수" to "6GHz · 채널 2"), wifiDetails(5935, null, null))
    }

    @Test fun routerSelfDescriptionIsReadFromTheWpsBlock() {
        fun attribute(type: Int, text: String) = byteArrayOf((type shr 8).toByte(), type.toByte(), 0, text.length.toByte()) + text.toByteArray()
        val wps = byteArrayOf(0x00, 0x50, 0xF2.toByte(), 0x04) + attribute(0x104A, "\u0010") + attribute(0x1021, "EFM Networks") +
            attribute(0x1023, "ipTIME A3004") + attribute(0x1011, "ipTIME")
        assertEquals(mapOf("공유기가 알린 제조사" to "EFM Networks", "공유기가 알린 모델" to "ipTIME A3004", "공유기가 알린 기기 이름" to "ipTIME"), wpsDetails(wps))
        // 다른 회사의 정보 블록이나 잘린 블록은 읽지 않는다.
        assertTrue(wpsDetails(byteArrayOf(0x00, 0x17, 0xF2.toByte(), 0x04, 0x10, 0x21, 0, 3, 65, 66, 67)).isEmpty())
        assertTrue(wpsDetails(byteArrayOf(0x00, 0x50, 0xF2.toByte(), 0x04, 0x10, 0x21, 0, 9, 65)).isEmpty())
    }

    @Test fun randomAddressesAreToldApartFromRegisteredOnes() {
        assertEquals(true, isLocallyAdministered("DA:A1:19:00:00:01"))
        assertEquals(false, isLocallyAdministered("88:36:6C:00:00:01"))
        assertNull(isLocallyAdministered("sim-airpods"))
        assertEquals("Buds", advertisedName("AA:BB:CC:DD:EE:FF", " Buds "))
        assertNull(advertisedName("AA:BB:CC:DD:EE:FF", "BLE 기기 EE:FF"))
        assertNull(advertisedName("AA:BB:CC:DD:EE:FF", "이름 없는 AP"))
    }
}
