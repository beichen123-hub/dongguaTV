const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const db = require('../db/sqlite');
const { signToken } = require('../middleware/jwtAuth');

router.post('/register', async (req, res) => {
  try {
    const { username, password } = req.body;
    if (!username || !password) return res.status(400).json({ error: '用户名和密码必填' });
    if (username.length < 3 || username.length > 20) return res.status(400).json({ error: '用户名 3-20 个字符' });
    if (password.length < 6) return res.status(400).json({ error: '密码至少 6 位' });

    const existing = db.prepare('SELECT id FROM users WHERE username = ?').get(username);
    if (existing) return res.status(409).json({ error: '用户名已存在' });

    const hash = bcrypt.hashSync(password, 10);
    const info = db.prepare('INSERT INTO users (username, password_hash) VALUES (?, ?)').run(username, hash);
    const token = signToken({ id: info.lastInsertRowid, username });
    res.json({ token, user: { id: info.lastInsertRowid, username } });
  } catch (e) {
    res.status(500).json({ error: '注册失败: ' + e.message });
  }
});

router.post('/login', async (req, res) => {
  try {
    const { username, password } = req.body;
    if (!username || !password) return res.status(400).json({ error: '用户名和密码必填' });

    const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (!user) return res.status(401).json({ error: '用户名或密码错误' });

    if (!bcrypt.compareSync(password, user.password_hash)) {
      return res.status(401).json({ error: '用户名或密码错误' });
    }
    const token = signToken({ id: user.id, username: user.username });
    res.json({ token, user: { id: user.id, username: user.username } });
  } catch (e) {
    res.status(500).json({ error: '登录失败: ' + e.message });
  }
});

module.exports = router;
