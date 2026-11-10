package com.beichen.dongguatv

import com.beichen.dongguatv.data.NameUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 剧名归一化：采集站同一部剧带不同营销后缀，聚合和换源都靠它判断「是不是同一部」。
 */
class NameUtilTest {

    @Test
    fun version_suffixes_merge_into_one_key() {
        val base = NameUtil.key("繁花")
        assertEquals(base, NameUtil.key("繁花 国语版"))
        assertEquals(base, NameUtil.key("繁花 沪语版"))
        assertEquals(base, NameUtil.key("繁花国语版"))
    }

    @Test
    fun brackets_are_stripped() {
        assertEquals(NameUtil.key("狂飙"), NameUtil.key("狂飙[全39集]"))
        assertEquals(NameUtil.key("繁花"), NameUtil.key("繁花(剧版)"))
        assertEquals(NameUtil.key("三体"), NameUtil.key("三体【全30集】"))
    }

    @Test
    fun different_shows_stay_apart() {
        assertNotEquals(NameUtil.key("繁花"), NameUtil.key("繁花之妖孽人生"))
        assertNotEquals(NameUtil.key("狂飙"), NameUtil.key("狂飙之风云再起"))
    }

    @Test
    fun display_drops_noise() {
        assertEquals("繁花", NameUtil.display("繁花 国语版"))
        assertEquals("狂飙", NameUtil.display("狂飙[全39集]"))
    }

    @Test
    fun blank_is_safe() {
        assertEquals("", NameUtil.key(""))
        assertEquals("", NameUtil.key("   "))
    }
}
