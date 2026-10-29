package io.github.cocokoniq.fancyir.core.irdb.remote

import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteIrdbTest {

    private class FakeHttp(private val responses: Map<String, String>) : HttpFetcher {
        val requested = mutableListOf<String>()
        override suspend fun get(url: String): String {
            requested += url
            return responses[url] ?: throw RemoteIrdbException("404 $url", 404)
        }
    }

    private fun ir(command: String) =
        "Filetype: IR signals file\nVersion: 1\nname: Power\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\ncommand: $command 00 00 00\n"

    @Test
    fun `flipper remote lists brands, downloads raw files and ranks families`() = runTest {
        val api = "https://api.github.com/repos/Lucaslhm/Flipper-IRDB/contents"
        val raw = "https://raw.githubusercontent.com/Lucaslhm/Flipper-IRDB/main"
        val http = FakeHttp(
            mapOf(
                "$api/TVs?ref=main" to """[{"name":"LG","path":"TVs/LG","type":"dir"},{"name":"Sony","path":"TVs/Sony","type":"dir"}]""",
                "$api/TVs/LG?ref=main" to """[{"name":"LG A.ir","path":"TVs/LG/LG A.ir","type":"file"},{"name":"LG_B.ir","path":"TVs/LG/LG_B.ir","type":"file"},{"name":"notes.md","path":"TVs/LG/notes.md","type":"file"}]""",
                "$raw/TVs/LG/LG%20A.ir" to ir("08"),
                "$raw/TVs/LG/LG_B.ir" to ir("08"),
            ),
        )

        val sets = FlipperIrdbRemote(http).codesets(DeviceCategory.TV, "lg")

        assertEquals(1, sets.size, "both files share one family")
        assertEquals(1, sets.single().rank)
        assertTrue(http.requested.none { it.endsWith("notes.md") })
    }

    @Test
    fun `smartir remote filters the index by brand and controller`() = runTest {
        val raw = "https://raw.githubusercontent.com/smartHomeHub/SmartIR/master"
        val code = "JgAMAAABKZEXNBgQFxEXEA=="
        val http = FakeHttp(
            mapOf(
                "$raw/docs/CLIMATE.md" to """
                    #### Daikin
                    | [1100](../codes/climate/1100.json) | FTXS | Broadlink |
                    | [1101](../codes/climate/1101.json) | FTXM | Xiaomi |
                    #### Gree
                    | [1180](../codes/climate/1180.json) | YAA | Broadlink |
                """.trimIndent(),
                "$raw/codes/climate/1100.json" to """{"manufacturer":"Daikin","supportedController":"Broadlink","commandsEncoding":"Base64",
                    "operationModes":["cool"],"fanModes":["auto"],"commands":{"off":"$code","cool":{"auto":{"24":"$code"}}}}""",
            ),
        )

        val sets = SmartIrRemote(http).codesets(DeviceCategory.AIR_CONDITIONER, "DAIKIN")

        assertEquals(listOf("smartir:climate:1100"), sets.map { it.id })
        assertTrue(http.requested.none { it.endsWith("1101.json") }, "Xiaomi-controller files are skipped")
    }

    @Test
    fun `rate limiting is recognisable`() {
        assertTrue(RemoteIrdbException("limited", 403).isRateLimited)
    }
}
