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

data class MatchRequest(val keyword: String, val episode: Int = 1)

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
    /** 解析采集站 vod_play_url 为播放线路列表。支持 # 和 $$$ 分隔。 */
    fun parse(playUrl: String): List<PlayLine> {
        if (playUrl.isBlank()) return emptyList()
        return playUrl.split("$$$").mapIndexed { lineIdx, lineRaw ->
            val eps = lineRaw.split("#").filter { it.isNotBlank() }.mapIndexed { epIdx, epRaw ->
                val parts = epRaw.split("$", limit = 2)
                Episode(
                    name = parts.getOrNull(0)?.trim() ?: "第${epIdx + 1}集",
                    url = parts.getOrNull(1)?.trim() ?: "",
                    index = epIdx
                )
            }
            PlayLine(name = "线路${lineIdx + 1}", episodes = eps)
        }
    }

    /** 取总集数（所有线路中最大的集数） */
    fun episodeCount(playUrl: String): Int =
        parse(playUrl).maxOfOrNull { it.episodes.size } ?: 0
}
