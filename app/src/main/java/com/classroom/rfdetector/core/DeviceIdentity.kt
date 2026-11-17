package com.classroom.rfdetector.core

/** Every identity field retains its source; advertising names and IDs are self-reported. */
data class DeviceIdentity(
    val advertisedCompanies: List<String> = emptyList(),
    val macOrganization: String? = null,
    val audioNameMatch: AudioNameMatch? = null,
    val wearableNameMatch: WearableNameMatch? = null,
) {
    val summary: String get() = advertisedCompanies.firstOrNull()?.let { "$it · BLE 광고" }
        ?: macOrganization?.let { "$it · MAC 등록사" }
        ?: audioNameMatch?.let { "${it.brand} · 이름 기반 후보" }
        ?: wearableNameMatch?.let { "${it.brand} · 이름 기반 후보" } ?: "제조사 미확인"
}

class DeviceIdentityResolver(
    private val macVendors: Map<String, String>,
    private val bluetoothCompanies: Map<Int, String>,
) {
    private val macPattern = Regex("[0-9A-F]{12}")

    private companion object {
        /**
         * The 24-bit prefix of an MA-M/MA-S block is listed under the registry itself; the real owner is in a longer
         * prefix this app does not carry. "Private" is an owner who chose not to publish a name. Neither is a maker.
         */
        val unusableOrganizations = setOf("IEEE Registration Authority", "Private")
    }

    fun resolve(address: String, name: String?, sources: Set<ScanSource>, manufacturerIds: Set<Int>): DeviceIdentity {
        val mac = address.replace(":", "").replace("-", "").uppercase()
        val validMac = mac.matches(macPattern)
        val firstOctet = if (validMac) mac.take(2).toInt(16) else 0
        val local = validMac && firstOctet and 2 != 0
        val unicast = validMac && firstOctet and 1 == 0
        val organization = if (unicast && !local) macVendors[mac.take(6)]?.takeUnless { it in unusableOrganizations } else null
        val orderedIds = manufacturerIds.sortedWith(compareBy<Int> { it !in bluetoothCompanies }.thenBy { it })
        val companies = orderedIds.map { bluetoothCompanies[it] ?: "미등록 회사 ID 0x${it.toString(16).uppercase().padStart(4, '0')}" }
        val audio = if (ScanSource.BLE in sources) AudioNameCatalog.match(name) else null
        val wearable = if (ScanSource.BLE in sources) WearableNameCatalog.match(name) else null
        return DeviceIdentity(companies, organization, audio, wearable)
    }
}
