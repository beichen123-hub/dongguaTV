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
    /**
     * 匹配剧集拿 episodeId，返回 null 表示真没匹配到。
     *
     * 实测坑点（都踩过）：
     * 1. /api/v2/match 只认 fileName 字段，传 keyword/episode 直接 400；
     * 2. 采集站剧名带营销后缀，如「繁花 国语版」「狂飙[全39集]」，
     *    原样丢给 match 会匹配失败，得逐个降级清洗；
     * 3. match 偶发返回 isMatched=false（服务端搜索限流），
     *    所以再加一条兜底：用 /api/v2/search/episodes 按剧名列出所有集自己挑。
     */
    suspend fun matchDanmaku(keyword: String, episode: Int): String? {
        val ep = episode.coerceAtLeast(1)
        val candidates = nameCandidates(keyword)
        for (name in candidates) {
            tryMatchByFileName(name, ep)?.let { return it }
        }
        for (name in candidates) {
            trySearchEpisodes(name, ep)?.let { return it }
        }
        return null
    }

    /** 剧名降级候选：原名 → 去括号 → 去版本后缀 → 取主标题 */
    private fun nameCandidates(raw: String): List<String> {
        val base = raw.trim()
        if (base.isEmpty()) return emptyList()
        val noBracket = base.replace(Regex("[\\[【(（].*?[\\]】)）]"), " ")
        val noVersion = noBracket.replace(
            Regex("(国语|粤语|沪语|英语|日语|韩语|国语版|粤语版|沪语版|英语版|" +
                    "TV版|tv版|未删减版|无删减|完整版|高清版|超清|蓝光|中字版|HD|4K)"),
            " "
        )
        val main = noVersion.replace(Regex("\\s+"), " ").trim()
        val head = main.split(" ", "-", "：", ":").firstOrNull()?.trim().orEmpty()
        return listOf(base, noBracket.trim(), main, head)
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && it.length >= 2 }
            .distinct()
            .take(4)
    }

    private suspend fun tryMatchByFileName(name: String, ep: Int): String? = try {
        val padded = ep.toString().padStart(2, '0')
        // 剧集用 SxxExx；第 1 集额外用纯文件名兜一次（单片电影走这条）
        val names = if (ep <= 1) listOf("$name S01E$padded.mp4", "$name.mp4")
        else listOf("$name S01E$padded.mp4")
        var found: String? = null
        for (fn in names) {
            val resp = danmuApi.match(MatchRequest(fn))
            if (!resp.isSuccessful) continue
            val body = resp.body()?.string() ?: continue
            val json = JSONObject(body)
            if (!json.optBoolean("success", false) || !json.optBoolean("isMatched", false)) continue
            val matches = json.optJSONArray("matches") ?: continue
            if (matches.length() == 0) continue
            val id = matches.optJSONObject(0)?.optString("episodeId").orEmpty()
            if (id.isNotBlank()) { found = id; break }
        }
        found
    } catch (e: Exception) {
        null
    }

    /** 兜底：按剧名搜到番剧，再从集列表里挑第 ep 集 */
    private suspend fun trySearchEpisodes(name: String, ep: Int): String? {
        return try {
            val resp = danmuApi.searchEpisodes(name)
            if (!resp.isSuccessful) return null
            val body = resp.body()?.string() ?: return null
            val animes = JSONObject(body).optJSONArray("animes") ?: return null
            var picked: String? = null
            for (i in 0 until animes.length()) {
                val anime = animes.optJSONObject(i) ?: continue
                val title = anime.optString("animeTitle")
                // 标题里得含剧名，且尽量避开动漫/综艺（同名真人剧优先）
                if (!title.replace(" ", "").contains(name.replace(" ", ""))) continue
                val isCartoon = title.contains("动漫") || title.contains("综艺")
                val episodes = anime.optJSONArray("episodes") ?: continue
                if (episodes.length() == 0) continue
                var epId = ""
                for (j in 0 until episodes.length()) {
                    val e = episodes.optJSONObject(j) ?: continue
                    if (e.optString("episodeTitle").contains("第${ep}集")) {
                        epId = e.optString("episodeId"); break
                    }
                }
                if (epId.isEmpty()) {
                    val idx = (ep - 1).coerceIn(0, episodes.length() - 1)
                    epId = episodes.optJSONObject(idx)?.optString("episodeId").orEmpty()
                }
                if (epId.isNotBlank()) {
                    picked = epId
                    if (!isCartoon) break   // 真人剧优先，命中就不找了
                }
            }
            picked
        } catch (e: Exception) {
            null
        }
    }

    /** 拿弹幕 XML（B 站标准格式，单集常见 3-4MB / 4 万条） */
    suspend fun getDanmakuXml(episodeId: String): String? {
        val resp = danmuApi.comment(episodeId)
        if (!resp.isSuccessful) return null
        return resp.body()?.string()?.takeIf { it.isNotBlank() }
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
                    db.followDao().updateEpisode(f.id, latest, System.currentTimeMillis())
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
