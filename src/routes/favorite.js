const express = require('express');
const router = express.Router();
const { jwtAuth } = require('../middleware/jwtAuth');
const db = require('../db/sqlite');

router.use(jwtAuth);

router.post('/favorite', (req, res) => {
  try {
    const { vod_id, site_key, vod_name, vod_pic } = req.body;
    if (!vod_id || !site_key) return res.status(400).json({ error: 'vod_id 和 site_key 必填' });

    const info = db.prepare(`
      INSERT INTO favorites (user_id, vod_id, site_key, vod_name, vod_pic)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(user_id, vod_id, site_key) DO UPDATE SET
        vod_name = excluded.vod_name, vod_pic = excluded.vod_pic
    `).run(req.user.id, vod_id, site_key, vod_name || '', vod_pic || '');
    res.json({ success: true, id: info.lastInsertRowid });
  } catch (e) {
    res.status(500).json({ error: '收藏失败: ' + e.message });
  }
});

router.get('/favorites', (req, res) => {
  const rows = db.prepare(`
    SELECT id, vod_id, site_key, vod_name, vod_pic, created_at
    FROM favorites WHERE user_id = ? ORDER BY created_at DESC
  `).all(req.user.id);
  res.json({ list: rows });
});

router.delete('/favorite/:id', (req, res) => {
  const info = db.prepare('DELETE FROM favorites WHERE id = ? AND user_id = ?').run(req.params.id, req.user.id);
  if (info.changes === 0) return res.status(404).json({ error: '收藏不存在' });
  res.json({ success: true });
});

module.exports = router;
