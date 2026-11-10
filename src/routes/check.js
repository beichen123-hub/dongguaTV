const express = require('express');
const router = express.Router();
const { findSite } = require('../db/sites');
const { testSiteLatency } = require('../services/siteProxy');

router.get('/check', async (req, res) => {
  const { key } = req.query;
  const site = findSite(key);
  if (!site) return res.json({ latency: 9999 });
  const latency = await testSiteLatency(site);
  res.json({ latency });
});

module.exports = router;
