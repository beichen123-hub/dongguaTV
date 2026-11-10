const express = require('express');
const router = express.Router();
const { getSites } = require('../db/sites');
const { fetchSiteList } = require('../services/siteProxy');

router.get('/hot', async (req, res) => {
  const sites = getSites().filter(s => ['ffzy', 'bfzy', 'lzi', 'dbzy'].includes(s.key));
  for (const site of sites) {
    try {
      const list = await fetchSiteList(site, { ac: 'list', pg: 1, h: 24 }, 3000);
      if (list.length > 0) return res.json({ list: list.slice(0, 12) });
    } catch (e) {
      continue;
    }
  }
  res.json({ list: [] });
});

module.exports = router;
