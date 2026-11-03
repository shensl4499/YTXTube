package com.tensei.ytxtube

import android.util.Log
import org.json.JSONObject

/**
 * Debug-only probe: summarises every InnerTube player response NewPipeExtractor receives, so a
 * "Could not get any stream" failure can be attributed (bot gate, SABR-only response, or every
 * itag being dropped) without a MITM proxy.
 *
 * Release builds fold [BuildConfig.DEBUG] to false, so R8 deletes all of this.
 */
internal object Diag {
    private const val TAG = "YTXDiag"
    private const val MAX_LINES = 8

    private var lines = 0

    fun playerResponse(
        url: String,
        body: String?,
    ) {
        if (!BuildConfig.DEBUG || body == null || lines >= MAX_LINES) return
        if ("/player" !in url && "reel_item_watch" !in url) return
        lines++
        Log.w(TAG, summarise(url, body))
    }

    private fun summarise(
        url: String,
        body: String,
    ): String =
        runCatching {
            val root = runCatching { JSONObject(body) }.getOrNull()
            val player = root?.optJSONObject("playerResponse") ?: root
            if (player == null) {
                return@runCatching "player ${endpoint(url)} non-json bytes=${body.length} head=${body.take(120)}"
            }
            val status = player.optJSONObject("playabilityStatus")
            val streaming = player.optJSONObject("streamingData")
            val formats = streaming?.optJSONArray("formats")
            val adaptive = streaming?.optJSONArray("adaptiveFormats")
            val itags =
                buildList {
                    fun collect(
                        array: org.json.JSONArray?,
                        limit: Int,
                    ) {
                        if (array == null) return
                        for (i in 0 until minOf(array.length(), limit)) {
                            array.optJSONObject(i)?.optInt("itag")?.let { add(it) }
                        }
                    }
                    collect(adaptive, 8)
                    collect(formats, 6)
                }
            val keys =
                streaming
                    ?.keys()
                    ?.asSequence()
                    ?.joinToString(",")
                    .orEmpty()
            "player ${endpoint(url)} status=${status?.optString("status")} " +
                "reason=${status?.optString("reason")?.take(90)} " +
                "sdKeys=[$keys] formats=${formats?.length() ?: -1} " +
                "adaptive=${adaptive?.length() ?: -1} itags=$itags bytes=${body.length}"
        }.getOrElse { "player summary failed: ${it.message} url=$url" }

    private fun endpoint(url: String): String = url.substringAfter("/youtubei/v1/", url).substringBefore('?')
}
