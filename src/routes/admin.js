const express = require('express');
const router = express.Router();
const { getDB, saveDB } = require('../db/sites');

const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin';

router.post('/admin/login', (req, res) => {
  if (req.body.password === ADMIN_PASSWORD) {
    res.json({ success: true });
  } else {
    res.status(403).json({ success: false });
  }
});

router.get('/admin/sites', (req, res) => {
  res.json(getDB().sites);
});

router.post('/admin/sites', (req, res) => {
  saveDB({ sites: req.body.sites });
  res.json({ success: true });
});

module.exports = router;
