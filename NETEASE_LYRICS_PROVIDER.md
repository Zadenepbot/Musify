# 网易云音乐歌词提供商 (Netease Cloud Music Lyrics Provider)

本实现为 Metrolist 添加了网易云音乐歌词获取功能，需要自建 `NeteaseCloudMusicApiEnhanced` API 服务。

## 功能特性

- 根据歌曲标题、艺术家、时长搜索匹配的歌曲
- 获取歌词（支持逐字歌词如果可用）
- 可配置的 API 服务地址
- 时长容差匹配（8秒）
- 完整的设置界面支持

## 部署网易云音乐 API

### 方式一：Docker（推荐）

```bash
docker pull moefurina/ncm-api:latest
docker run -d -p 3000:3000 --name ncm-api moefurina/ncm-api:latest
```

### 方式二：Node.js 直接运行

```bash
git clone https://github.com/neteasecloudmusicapienhanced/api-enhanced.git
cd api-enhanced
pnpm i
node app.js
```

默认端口为 3000。

## 配置 Metrolist

1. 打开 Metrolist 应用
2. 进入 **设置** → **内容设置** → **歌词**
3. 开启 **网易云音乐** 开关
4. 点击 **网易云音乐 API 地址** 配置项
5. 输入你的 API 服务地址（例如 `http://localhost:3000` 或 `http://your-server:3000`）
6. 保存

## 提供商优先级

在 **歌词提供商优先级** 设置中，可以调整网易云音乐在歌词搜索顺序中的位置。建议将网易云音乐放在靠前的位置以获得更好的歌词匹配结果。

## 实现细节

### 文件结构

```
Metrolist/
├── app/src/main/kotlin/com/metrolist/
│   ├── music/
│   │   ├── constants/
│   │   │   └── PreferenceKeys.kt        # 添加配置键
│   │   ├── lyrics/
│   │   │   └── LyricsProviderRegistry.kt # 注册新提供商
│   │   ├── ui/screens/settings/
│   │   │   └── ContentSettings.kt       # 设置界面
│   │   └── App.kt                        # 应用初始化
│   └── netease/
│       └── NeteaseCloudMusicLyricsProvider.kt  # 主要实现
```

### API 接口

- **搜索歌曲**: `GET /api/cloudsearch/pc?keywords={title}+{artist}&type=1&limit=30`
- **获取歌词**: `GET /api/song/lyric/v1?id={songId}`

### 匹配策略

1. 构建搜索查询：`"{title} {artist}"`
2. 搜索匹配的歌曲
3. 根据时长容差（8秒）筛选最匹配的歌曲
4. 获取该歌曲的歌词

## 故障排除

### 歌词无法加载

1. 确认 API 服务正在运行：`curl http://localhost:3000`
2. 检查 Metrolist 中的 API 地址配置是否正确
3. 查看日志输出以获取错误信息

### API 服务无法访问

- 确保设备能够访问 API 服务地址
- 如果 API 在远程服务器上，检查防火墙和网络设置
- 考虑使用反向代理（如 Nginx）添加 HTTPS 支持

## 注意事项

- 本功能需要自建 API 服务，不包含在 Metrolist 应用中
- API 服务应部署在本地网络或可访问的公网服务器
- 建议定期更新 `NeteaseCloudMusicApiEnhanced` 以获取最新功能和修复

## 开源许可

- Metrolist: GPL-3.0
- NeteaseCloudMusicApiEnhanced: MIT
