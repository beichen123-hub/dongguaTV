const express = require('express');
const router = express.Router();
const { getActiveSites } = require('../db/sites');
const { fetchSiteList } = require('../services/siteProxy');

router.get('/search', async (req, res) => {
  const { wd } = req.query;
  console.log(`[Search] ${wd}`);
  if (!wd) return res.json({ list: [] });

  const sites = getActiveSites();
  const promises = sites.map(async (site) => {
    try {
      return await fetchSiteList(site, { ac: 'list', wd }, 6000);
    } catch (e) {
      return [];
    }
  });
  const results = await Promise.all(promises);
  res.json({ list: results.flat() });
});

module.exports = router;
