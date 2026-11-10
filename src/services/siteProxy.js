const axios = require('axios');

async function fetchSiteList(site, extraParams = {}, timeout = 6000) {
  const url = new URL(site.api);
  Object.entries({ out: 'json', ...extraParams }).forEach(([k, v]) => url.searchParams.set(k, v));
  const resp = await axios.get(url.toString(), { timeout });
  const data = resp.data;
  const list = data.list || data.data;
  if (Array.isArray(list)) {
    return list.map(item => ({ ...item, site_key: site.key, site_name: site.name, latency: 0 }));
  }
  return [];
}

async function fetchSiteDetail(site, id, timeout = 6000) {
  const url = new URL(site.api);
  url.searchParams.set('ac', 'detail');
  url.searchParams.set('ids', id);
  url.searchParams.set('out', 'json');
  const resp = await axios.get(url.toString(), { timeout });
  return resp.data;
}

async function testSiteLatency(site) {
  const start = Date.now();
  try {
    await axios.get(`${site.api}?ac=list&pg=1`, { timeout: 3000 });
    return Date.now() - start;
  } catch (e) {
    return 9999;
  }
}

module.exports = { fetchSiteList, fetchSiteDetail, testSiteLatency };
