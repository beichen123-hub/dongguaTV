const fs = require('fs');
const path = require('path');

const DATA_FILE = path.join(__dirname, '../../db.json');
const FORCE_UPDATE = true;

const DEFAULT_SITES = [
  { key: 'ffzy', name: '非凡影视', api: 'https://替换接口一行一条', active: true }
];

function getDB() {
  try {
    const data = JSON.parse(fs.readFileSync(DATA_FILE));
    if (FORCE_UPDATE) {
      const dbSites = data.sites || [];
      DEFAULT_SITES.forEach(defSite => {
        if (!dbSites.find(s => s.key === defSite.key)) dbSites.push(defSite);
      });
      return { sites: dbSites };
    }
    return data;
  } catch (e) {
    return { sites: DEFAULT_SITES };
  }
}

function saveDB(data) {
  fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2));
}

function getSites() {
  return getDB().sites || [];
}

function getActiveSites() {
  return getSites().filter(s => s.active);
}

function findSite(key) {
  return getSites().find(s => s.key === key);
}

module.exports = { getDB, saveDB, getSites, getActiveSites, findSite };
