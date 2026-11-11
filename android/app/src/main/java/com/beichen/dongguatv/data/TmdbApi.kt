package com.beichen.dongguatv.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TMDb 元数据接口 —— 首页推荐流的数据源。
 *
 * 与原版冬瓜TV Web 完全同源：Web 首页的热门电影/剧集/华语/日韩榜单、
 * Hero 轮播全部来自 TMDb（走 daili.smdhb.com 国内代理），
 * 点击榜单卡片后 Web 会用片名反查采集站聚合源 —— App 端同样这么做。
 * 后端 tv.bcpan.icu 从头到尾只负责「搜资源、出播放地址」。
 */
interface TmdbApi {

    @GET("3/trending/movie/week")
    suspend fun trendingMovie(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "zh-CN"
    ): TmdbResponse

    @GET("3/trending/tv/week")
    suspend fun trendingTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "zh-CN"
    ): TmdbResponse

    @GET("3/trending/all/week")
    suspend fun trendingAll(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "zh-CN"
    ): TmdbResponse

    @GET("3/discover/tv")
    suspend fun discoverTv(
        @Query("with_origin_country") country: String,
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "zh-CN"
    ): TmdbResponse
}

/** TMDb 列表响应（只取用得上的字段，其余忽略） */
data class TmdbResponse(
    val results: List<TmdbItem> = emptyList()
)

/**
 * TMDb 单条影片：电影用 title/release_date，剧集用 name/first_air_date，
 * trending/all 混合列表两者都可能出现，所以全收。
 */
data class TmdbItem(
    val id: Long = 0,
    val title: String = "",
    val name: String = "",
    val overview: String = "",
    val poster_path: String = "",
    val backdrop_path: String = "",
    val vote_average: Double = 0.0,
    val release_date: String = "",
    val first_air_date: String = "",
    val media_type: String = ""
) {
    /** 统一显示名：电影取 title，剧集取 name */
    val displayName: String get() = title.ifBlank { name }

    /** 年份：电影取上映日期，剧集取首播日期 */
    val year: String
        get() = (release_date.ifBlank { first_air_date }).take(4)

    /** 海报：走国内代理，w500 清晰度足够（Web 端列表用的 w300） */
    fun posterUrl(width: Int = 500): String = "${TmdbConst.IMG_BASE}/w$width$poster_path"

    /** Hero 大背景图，Web 端用 original，这里 w780 手机足够且省流量 */
    fun backdropUrl(): String = "${TmdbConst.IMG_BASE}/w780$backdrop_path"
}

/** TMDb 常量：代理与 Key 都来自原版 Web 首页的配置，保持同源 */
object TmdbConst {
    /** 国内直连代理（与 dg_index.html 的 MY_PROXY 一致） */
    const val IMG_BASE = "https://daili.smdhb.com/t/p"

    /** 与原版 Web 相同的公开 Key（原仓库 index.html 明文内置） */
    const val API_KEY = "79cd44bf3778a83f7e2815468c3e091f"
}
