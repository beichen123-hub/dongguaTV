const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../../data.db');
const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

function init() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS favorites (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      vod_id TEXT NOT NULL,
      site_key TEXT NOT NULL,
      vod_name TEXT,
      vod_pic TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(user_id, vod_id, site_key)
    );
    CREATE TABLE IF NOT EXISTS history (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      vod_id TEXT NOT NULL,
      site_key TEXT NOT NULL,
      episode_index INTEGER DEFAULT 0,
      vod_name TEXT,
      vod_pic TEXT,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(user_id, vod_id, site_key)
    );
    CREATE TABLE IF NOT EXISTS follows (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      vod_id TEXT NOT NULL,
      site_key TEXT NOT NULL,
      last_episode INTEGER DEFAULT 0,
      vod_name TEXT,
      vod_pic TEXT,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(user_id, vod_id, site_key)
    );
  `);
}

init();

module.exports = db;
