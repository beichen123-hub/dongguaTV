const express = require('express');
const router = express.Router();
const { jwtAuth } = require('../middleware/jwtAuth');
const db = require('../db/sqlite');
const { findSite } = require('../db/sites');
const { fetchSiteDetail } = require('../services/siteProxy');

router.use(jwtAuth);

function parseEpisodeCount(playUrl) {
  if (!playUrl) return 0;
  const groups = playUrl.split('$$$');
  let max = 0;
  for (const g of groups) {
    const eps = g.split('#').filter(s => s.trim());
    if (eps.length > max) max = eps.length;
  }
  return max;
}

router.post('/follow', (req, res) => {
  try {
    const { vod_id, site_key, vod_name, vod_pic } = req.body;
    if (!vod_id || !site_key) return res.status(400).json({ error: 'vod_id 和 site_key 必填' });

    const info = db.prepare(`
      INSERT INTO follows (user_id, vod_id, site_key, last_episode, vod_name, vod_pic)
      VALUES (?, ?, ?, 0, ?, ?)
      ON CONFLICT(user_id, vod_id, site_key) DO UPDATE SET
        vod_name = excluded.vod_name, vod_pic = excluded.vod_pic
    `).run(req.user.id, vod_id, site_key, vod_name || '', vod_pic || '');
    res.json({ success: true, id: info.lastInsertRowid });
  } catch (e) {
    res.status(500).json({ error: '追剧失败: ' + e.message });
  }
});

router.get('/follows', (req, res) => {
  const rows = db.prepare(`
    SELECT id, vod_id, site_key, last_episode, vod_name, vod_pic, updated_at
    FROM follows WHERE user_id = ? ORDER BY updated_at DESC
  `).all(req.user.id);
  res.json({ list: rows });
});

router.delete('/follow/:id', (req, res) => {
  const info = db.prepare('DELETE FROM follows WHERE id = ? AND user_id = ?').run(req.params.id, req.user.id);
  if (info.changes === 0) return res.status(404).json({ error: '追剧不存在' });
  res.json({ success: true });
});

router.get('/follow/check', async (req, res) => {
  try {
    const follows = db.prepare('SELECT * FROM follows WHERE user_id = ?').all(req.user.id);
    const updates = [];
    for (const f of follows) {
      try {
        const site = findSite(f.site_key);
        if (!site) continue;
        const detail = await fetchSiteDetail(site, f.vod_id, 6000);
        const item = (detail.list || detail.data || [])[0];
        if (!item) continue;
        const latest = parseEpisodeCount(item.vod_play_url);
        if (latest > f.last_episode) {
          db.prepare('UPDATE follows SET last_episode = ?, updated_at = datetime(\'now\') WHERE id = ?')
            .run(latest, f.id);
          updates.push({
            id: f.id, vod_id: f.vod_id, site_key: f.site_key, vod_name: f.vod_name,
            vod_pic: f.vod_pic, last_episode: f.last_episode, latest_episode: latest,
            new_episodes: latest - f.last_episode
          });
        }
      } catch (e) {
        continue;
      }
    }
    res.json({ updates });
  } catch (e) {
    res.status(500).json({ error: '检查更新失败: ' + e.message });
  }
});

module.exports = router;
