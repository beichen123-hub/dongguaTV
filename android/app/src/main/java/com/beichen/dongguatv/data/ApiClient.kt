package com.beichen.dongguatv.data

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TV_BASE = "https://tv.bcpan.icu/"
    private const val DANMU_BASE = "http://113.137.46.16:19321/87654321/"

    private fun okClient(): OkHttpClient {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(log)
            .build()
    }

    private fun buildRetrofit(base: String): Retrofit {
        val gson = GsonBuilder().setLenient().create()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(okClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val tvApi: TvApi by lazy { buildRetrofit(TV_BASE).create(TvApi::class.java) }
    val danmuApi: DanmuApi by lazy { buildRetrofit(DANMU_BASE).create(DanmuApi::class.java) }
    val tmdbApi: TmdbApi by lazy { buildRetrofit(TMDB_BASE).create(TmdbApi::class.java) }
}

interface TvApi {
    @GET("api/search")
    suspend fun search(@Query("wd") wd: String): SearchResponse

    // 注意：后端没有 /api/hot（web 首页的热门是 HTML 硬编码），别再照着博客抄这个接口
    @GET("api/detail")
    suspend fun detail(@Query("site_key") siteKey: String, @Query("id") id: String): DetailResponse

    @GET("api/check")
    suspend fun check(@Query("key") key: String): CheckResponse
}

interface DanmuApi {
    @retrofit2.http.POST("api/v2/match")
    suspend fun match(@Body body: MatchRequest): Response<okhttp3.ResponseBody>

    /** 兜底：按剧名搜索，返回该番剧下所有集的 episodeId */
    @GET("api/v2/search/episodes")
    suspend fun searchEpisodes(@Query("anime") anime: String): Response<okhttp3.ResponseBody>

    @GET("api/v2/comment/{id}")
    suspend fun comment(
        @Path("id") id: String,
        @Query("format") format: String = "xml"
    ): Response<okhttp3.ResponseBody>
}
