const express = require('express');
const router = express.Router();
const { findSite } = require('../db/sites');
const { fetchSiteDetail } = require('../services/siteProxy');

router.get('/detail', async (req, res) => {
  const { site_key, id } = req.query;
  const targetSite = findSite(site_key);
  if (!targetSite) return res.status(404).json({ error: 'Site not found' });
  try {
    const data = await fetchSiteDetail(targetSite, id, 6000);
    res.json(data);
  } catch (e) {
    res.status(500).json({ error: 'Source Error' });
  }
});

module.exports = router;
