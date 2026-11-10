package com.beichen.dongguatv.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.Room
import org.json.JSONObject

class Repository private constructor(context: Context) {

    private val tvApi = ApiClient.tvApi
    private val danmuApi = ApiClient.danmuApi
    private val db = Room.databaseBuilder(
        context.applicationContext, AppDatabase::class.java, "dongguatv.db"
    ).fallbackToDestructiveMigration().build()

    // ===== 影视接口（调现有 tv.bcpan.icu）=====
    suspend fun search(wd: String) = tvApi.search(wd)
    suspend fun hot() = tvApi.hot()
    suspend fun detail(siteKey: String, id: String) = tvApi.detail(siteKey, id)
    suspend fun checkSite(key: String) = tvApi.check(key).latency

    // ===== 弹幕（直连公网 danmu_api）=====
    /** 匹配剧集拿 episodeId，返回 null 表示没匹配到 */
    suspend fun matchDanmaku(keyword: String, episode: Int): String? {
        val resp = danmuApi.match(MatchRequest(keyword, episode))
        if (!resp.isSuccessful) return null
        val body = resp.body()?.string() ?: return null
        return try {
            val json = JSONObject(body)
            // danmu_api 可能的字段路径：episodeId / data.episodeId / id
            json.optString("episodeId").ifBlank { json.optString("id") }.ifBlank {
                json.optJSONObject("data")?.optString("episodeId")
            }
        } catch (e: Exception) { null }?.takeIf { it.isNotBlank() }
    }

    /** 拿弹幕 XML */
    suspend fun getDanmakuXml(episodeId: String): String? {
        val resp = danmuApi.comment(episodeId)
        if (!resp.isSuccessful) return null
        return resp.body()?.string()
    }

    // ===== 收藏 =====
    fun observeFavorites(): LiveData<List<FavoriteEntity>> = db.favoriteDao().observeAll()
    suspend fun isFavorite(vodId: String, siteKey: String) =
        db.favoriteDao().find(vodId, siteKey) != null
    suspend fun toggleFavorite(item: VodItem) {
        val exist = db.favoriteDao().find(item.vod_id, item.site_key)
        if (exist != null) db.favoriteDao().delete(item.vod_id, item.site_key)
        else db.favoriteDao().insert(FavoriteEntity(
            vodId = item.vod_id, siteKey = item.site_key,
            vodName = item.vod_name, vodPic = item.vod_pic
        ))
    }
    suspend fun deleteFavorite(vodId: String, siteKey: String) = db.favoriteDao().delete(vodId, siteKey)
    suspend fun deleteFollow(id: Long) = db.followDao().delete(id)

    // ===== 历史 =====
    fun observeHistory(): LiveData<List<HistoryEntity>> = db.historyDao().observeAll()
    suspend fun recordHistory(vodId: String, siteKey: String, name: String, pic: String, episode: Int) {
        db.historyDao().upsert(HistoryEntity(
            vodId = vodId, siteKey = siteKey, vodName = name, vodPic = pic, episodeIndex = episode
        ))
    }
    suspend fun deleteHistory(id: Long) = db.historyDao().delete(id)
    suspend fun clearHistory() = db.historyDao().clear()

    // ===== 追剧 =====
    fun observeFollows(): LiveData<List<FollowEntity>> = db.followDao().observeAll()
    suspend fun isFollowed(vodId: String, siteKey: String) =
        db.followDao().find(vodId, siteKey) != null
    suspend fun toggleFollow(item: VodItem) {
        val exist = db.followDao().find(item.vod_id, item.site_key)
        if (exist != null) db.followDao().delete(exist.id)
        else db.followDao().upsert(FollowEntity(
            vodId = item.vod_id, siteKey = item.site_key,
            vodName = item.vod_name, vodPic = item.vod_pic
        ))
    }
    /** 检查追剧更新：对比采集站最新集数，返回有更新的列表 */
    suspend fun checkFollowUpdates(): List<FollowUpdate> {
        val follows = db.followDao().getAll()
        val updates = mutableListOf<FollowUpdate>()
        for (f in follows) {
            try {
                val detail = tvApi.detail(f.siteKey, f.vodId)
                val item = detail.list.firstOrNull() ?: continue
                val latest = PlayUrlParser.episodeCount(item.vod_play_url)
                if (latest > f.lastEpisode) {
                    db.followDao().updateEpisode(f.id, latest)
                    updates.add(FollowUpdate(f, latest, latest - f.lastEpisode))
                }
            } catch (e: Exception) { continue }
        }
        return updates
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}

data class FollowUpdate(
    val follow: FollowEntity,
    val latestEpisode: Int,
    val newCount: Int
)
