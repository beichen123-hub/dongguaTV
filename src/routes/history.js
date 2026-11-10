const express = require('express');
const router = express.Router();
const { jwtAuth } = require('../middleware/jwtAuth');
const db = require('../db/sqlite');

router.use(jwtAuth);

router.post('/history', (req, res) => {
  try {
    const { vod_id, site_key, episode_index, vod_name, vod_pic } = req.body;
    if (!vod_id || !site_key) return res.status(400).json({ error: 'vod_id 和 site_key 必填' });

    const info = db.prepare(`
      INSERT INTO history (user_id, vod_id, site_key, episode_index, vod_name, vod_pic, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
      ON CONFLICT(user_id, vod_id, site_key) DO UPDATE SET
        episode_index = excluded.episode_index,
        vod_name = excluded.vod_name,
        vod_pic = excluded.vod_pic,
        updated_at = datetime('now')
    `).run(req.user.id, vod_id, site_key, episode_index || 0, vod_name || '', vod_pic || '');
    res.json({ success: true, id: info.lastInsertRowid });
  } catch (e) {
    res.status(500).json({ error: '记录历史失败: ' + e.message });
  }
});

router.get('/history', (req, res) => {
  const rows = db.prepare(`
    SELECT id, vod_id, site_key, episode_index, vod_name, vod_pic, updated_at
    FROM history WHERE user_id = ? ORDER BY updated_at DESC
  `).all(req.user.id);
  res.json({ list: rows });
});

router.delete('/history/:id', (req, res) => {
  const info = db.prepare('DELETE FROM history WHERE id = ? AND user_id = ?').run(req.params.id, req.user.id);
  if (info.changes === 0) return res.status(404).json({ error: '历史不存在' });
  res.json({ success: true });
});

module.exports = router;
