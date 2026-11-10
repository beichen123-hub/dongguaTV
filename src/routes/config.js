const express = require('express');
const router = express.Router();

const DANMAKU_API_URL = process.env.DANMAKU_API || '';

router.get('/config', (req, res) => {
  res.json({ danmaku_api: DANMAKU_API_URL });
});

module.exports = router;
