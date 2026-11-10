package com.beichen.dongguatv

import com.beichen.dongguatv.data.PlayUrlParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 采集站 vod_play_url 的格式不统一（$) 分隔集与地址、# 分隔集、$$$ 分隔线路），
 * 这里用真实形态的样本把解析规则钉死，避免以后改坏。
 */
class PlayUrlParserTest {

    /** 两条线路：线路 1 有 2 集，线路 2 有 1 集 */
    private val sample =
        "第01集\$https://cdn.example.com/a/index.m3u8#第02集\$https://cdn.example.com/b/index.m3u8" +
            "\$\$\$第1集\$https://cdn2.example.com/1.m3u8"

    @Test
    fun parses_lines_and_episodes() {
        val lines = PlayUrlParser.parse(sample, "bfzym3u8,ffm3u8")
        assertEquals(2, lines.size)

        assertEquals("bfzym3u8", lines[0].name)
        assertEquals(2, lines[0].episodes.size)
        assertEquals("第01集", lines[0].episodes[0].name)
        assertEquals("https://cdn.example.com/a/index.m3u8", lines[0].episodes[0].url)
        assertEquals(0, lines[0].episodes[0].index)
        assertEquals(1, lines[0].episodes[1].index)

        assertEquals("ffm3u8", lines[1].name)
        assertEquals(1, lines[1].episodes.size)
    }

    @Test
    fun falls_back_to_generic_line_name() {
        val lines = PlayUrlParser.parse(sample)
        assertEquals("线路1", lines[0].name)
        assertEquals("线路2", lines[1].name)
    }

    @Test
    fun episode_count_takes_longest_line() {
        assertEquals(2, PlayUrlParser.episodeCount(sample))
    }

    @Test
    fun blank_input_is_safe() {
        assertEquals(0, PlayUrlParser.parse("").size)
        assertEquals(0, PlayUrlParser.episodeCount(""))
        assertEquals(0, PlayUrlParser.parse("$$$").size)
    }

    @Test
    fun drops_entries_without_url() {
        val lines = PlayUrlParser.parse("第01集\$#第02集\$https://x.com/2.m3u8")
        assertEquals(1, lines.size)
        assertEquals(1, lines[0].episodes.size)
        assertEquals("https://x.com/2.m3u8", lines[0].episodes[0].url)
    }
}
