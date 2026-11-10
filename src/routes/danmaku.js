const express = require('express');
const router = express.Router();
const axios = require('axios');
const { jwtAuth } = require('../middleware/jwtAuth');

const DANMU_BASE = process.env.DANMU_INTERNAL_URL || 'http://danmu-danmu-api-1:9321/87654321';

router.use(jwtAuth);

router.post('/danmaku/match', async (req, res) => {
  try {
    const { keyword, episode } = req.body;
    if (!keyword) return res.status(400).json({ error: 'keyword 必填' });

    const resp = await axios.post(`${DANMU_BASE}/api/v2/match`,
      { keyword, episode: episode || 1 },
      { timeout: 8000 }
    );
    res.json(resp.data);
  } catch (e) {
    res.status(502).json({ error: '弹幕服务请求失败: ' + (e.message || 'unknown') });
  }
});

router.get('/danmaku/comment/:id', async (req, res) => {
  try {
    const { id } = req.params;
    const resp = await axios.get(`${DANMU_BASE}/api/v2/comment/${id}`,
      { params: { format: 'xml' }, timeout: 8000, responseType: 'text' }
    );
    res.set('Content-Type', 'application/xml; charset=utf-8');
    res.send(resp.data);
  } catch (e) {
    res.status(502).json({ error: '弹幕获取失败: ' + (e.message || 'unknown') });
  }
});

module.exports = router;
