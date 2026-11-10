function errorHandler(err, req, res, next) {
  console.error('[Error]', err.message || err);
  if (res.headersSent) return next(err);
  res.status(err.status || 500).json({ error: err.message || '服务器内部错误' });
}

function notFound(req, res) {
  res.status(404).json({ error: '接口不存在' });
}

module.exports = { errorHandler, notFound };
