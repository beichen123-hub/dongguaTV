const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, '../public')));

app.use('/api', require('./routes/search'));
app.use('/api', require('./routes/detail'));
app.use('/api', require('./routes/hot'));
app.use('/api', require('./routes/check'));
app.use('/api', require('./routes/config'));
app.use('/api', require('./routes/admin'));
app.use('/api/auth', require('./routes/auth'));
app.use('/api', require('./routes/favorite'));
app.use('/api', require('./routes/history'));
app.use('/api', require('./routes/follow'));
app.use('/api', require('./routes/danmaku'));

const { errorHandler, notFound } = require('./middleware/errorHandler');
app.use(notFound);
app.use(errorHandler);

app.listen(PORT, () => {
  console.log(`冬瓜TV服务已启动: http://localhost:${PORT}`);
  console.log(`  弹幕内网代理: ${process.env.DANMU_INTERNAL_URL || 'http://danmu-danmu-api-1:9321/87654321'}`);
});
