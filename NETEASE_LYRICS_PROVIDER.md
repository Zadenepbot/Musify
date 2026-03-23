# 网易云音乐歌词提供商 (Netease Cloud Music Lyrics Provider)

本实现为 Metrolist 添加了网易云音乐歌词获取功能，**直接使用官方 API**，无需任何第三方或自建服务。

## 功能特性

- 根据歌曲标题、艺术家、时长搜索匹配的歌曲
- 获取歌词及翻译（如可用）
- 开箱即用，无需配置
- 时长容差匹配（8秒）
- 使用 eapi 加密与官方客户端相同

## 配置 Metrolist

1. 打开 Metrolist 应用
2. 进入 **设置** → **内容设置** → **歌词**
3. 开启 **网易云音乐** 开关
4. 在 **歌词提供商优先级** 中调整网易云音乐的位置（建议靠前以获得更好的匹配结果）

**无需填写任何 API 地址**，应用会自动使用官方接口。

## 实现细节

### 文件结构

```
Metrolist/
├── app/src/main/kotlin/com/metrolist/
│   └── netease/
│       └── NeteaseCloudMusicLyricsProvider.kt  # 主要实现
```

### API 接口

- **搜索歌曲**: `POST /eapi/api/cloudsearch/pc`（eapi 加密）
- **获取歌词**: `POST /eapi/api/song/lyric/v1`（eapi 加密）

响应包含：
- `body.lyric`：标准 LRC 格式歌词
- `body.tlyric`：翻译歌词（LRC 格式，如存在）

### 匹配策略

1. 构建搜索查询：`"{title} {artist}"`
2. 使用 eapi 加密请求搜索 API
3. 根据时长容差（8秒）筛选最匹配的歌曲
4. 获取该歌曲的歌词及翻译

### 翻译歌词处理

歌词存储在本地数据库中时，主歌词和翻译歌词会以如下格式组合：

```
[主歌词 LRC 内容]

[translate]
[翻译歌词 LRC 内容]
```

这种格式便于后续展示时拆分显示。

## 故障排除

### 歌词无法加载

- 确保设备能访问互联网
- 检查是否已启用"网易云音乐"提供商
- 查看日志以获取具体错误信息

### 网络问题

- 某些地区可能需要对官方 API 进行代理
- 如遇到连接问题，可尝试使用 VPN 或网络代理

## 技术说明

本实现使用 Java/Kotlin 原生 eapi 加密算法（AES-ECB + MD5），与网易云音乐客户端相同。无需运行额外的 Node.js 服务，减少了部署复杂性和资源消耗。

### eapi 加密流程

1. 构建请求参数 JSON
2. 构造消息：`nobody${path}${EAPI_MAGIC}${jsonData}${EAPI_MAGIC}`
3. 计算 MD5 摘要
4. 拼接：`${path}-${EAPI_MAGIC}-${jsonData}-${EAPI_MAGIC}-${digest}`
5. 使用 AES-ECB 加密（密钥 `e82ckenh8dichen8`）
6. 发送加密后的十六进制字符串作为请求体

## 开源许可

- Metrolist: GPL-3.0
