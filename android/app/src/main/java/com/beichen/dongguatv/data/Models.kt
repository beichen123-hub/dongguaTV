package com.beichen.dongguatv.data

data class SearchResponse(val list: List<VodItem> = emptyList())

data class VodItem(
    val vod_id: String = "",
    val vod_name: String = "",
    val vod_pic: String = "",
    val vod_remarks: String = "",
    val site_key: String = "",
    val site_name: String = "",
    val type_id: String = "",
    val latency: Int = 0
)

data class DetailResponse(
    val code: Int = 0,
    val msg: String = "",
    val list: List<VodDetail> = emptyList()
)

data class VodDetail(
    val vod_id: String = "",
    val vod_name: String = "",
    val vod_pic: String = "",
    val vod_content: String = "",
    val vod_year: String = "",
    val vod_area: String = "",
    val vod_actor: String = "",
    val vod_director: String = "",
    val vod_remarks: String = "",
    val vod_play_from: String = "",
    val vod_play_url: String = ""
)

data class CheckResponse(val latency: Int = 9999)

/**
 * danmu_api /api/v2/match 请求体。
 * 实测该接口只认 fileName（传 keyword/episode 会 400 Missing fileName parameter），
 * 文件名需带上季集信息才能精确匹配到单集。
 */
data class MatchRequest(val fileName: String)

/** 单集播放信息：名称 + 播放地址 */
data class Episode(
    val name: String,
    val url: String,
    val index: Int
)

/** 一条播放线路：名称 + 该线路下的所有集数 */
data class PlayLine(
    val name: String,
    val episodes: List<Episode>
)

object PlayUrlParser {
    /**
     * 解析采集站 vod_play_url 为播放线路列表。
     * 多线路用 $$$ 分隔，集与地址用 $ 分隔，集之间用 # 分隔。
     * @param playFrom 采集站的 vod_play_from（如 "bfzym3u8,feifan"），用于给线路起真实名字
     */
    fun parse(playUrl: String, playFrom: String = ""): List<PlayLine> {
        if (playUrl.isBlank()) return emptyList()
        val lineNames = playFrom.split(",").map { it.trim() }
        return playUrl.split("$$$").mapIndexed { lineIdx, lineRaw ->
            val eps = lineRaw.split("#").filter { it.isNotBlank() }.mapIndexed { epIdx, epRaw ->
                val parts = epRaw.split("$", limit = 2)
                Episode(
                    name = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
                        ?: "第${epIdx + 1}集",
                    url = parts.getOrNull(1)?.trim() ?: "",
                    index = epIdx
                )
            }.filter { it.url.isNotBlank() }
            val rawName = lineNames.getOrNull(lineIdx)?.takeIf { it.isNotBlank() }
            PlayLine(
                name = rawName ?: "线路${lineIdx + 1}",
                episodes = eps
            )
        }.filter { it.episodes.isNotEmpty() }
    }

    /** 取总集数（所有线路中最大的集数） */
    fun episodeCount(playUrl: String): Int =
        parse(playUrl).maxOfOrNull { it.episodes.size } ?: 0
}
