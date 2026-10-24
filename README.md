
# 时物

> 物有来处，时有所记。

「时物」是一款本地优先的物品管理应用。它替你记住每件物品的价格、购入与退役、保质期与日均成本，也收留那些尚未入手的愿望。数据留在设备本地，重要决定都由你掌控。

<p align="center">
  <img src="Project/Shiwu/design/shiwu-icon_%E6%97%B6%E9%97%B4%E7%9B%92-%E5%9B%BE%E6%A0%87-%E5%9C%86%E8%A7%92.png" width="256" height="256" alt="时物图标" />
</p>

## 特性

- **多清单**：物品与心愿单按清单组织，首页点标题即可切换
- **物品档案**：价格、照片、备注、退役记录，以及随时间沉淀的日均成本
- **保质期守护**：生产日期 / 到期日期 / 保质期三选二自动推算；临期黄框、过期红框
- **心愿清单**：记录优先级与攒钱进度，购买后一键转正为物品
- **统计洞察**：总价值、分类占比、日均成本排名、临期排行
- **数据自主**：CSV / JSON 导入导出，照片随行；局域网 WebDAV 双向同步（照片增量、墓碑合并、冲突副本）
- **清单级保护**：密码与指纹鉴权，敏感清单切换即校验

## 技术栈

Kotlin · Jetpack Compose（Material 3）· Room（KSP2）· AGP 9

## 工程结构

```
Project/Shiwu/app/src/main/java/com/zzzcy/shiwu/
├─ data/     # Room 实体、Repository 与领域模型
├─ ui/       # 页面与通用组件（Compose）
└─ util/     # CSV、照片、导入导出等工具
Project/Shiwu/design/   # 应用图标源文件（SVG 与圆角 PNG）
docs/        # 需求理解与设计文档
```

## 构建

```bash
cd Project/Shiwu
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleRelease   # 或 assembleDebug
```

## 里程碑

| 版本 | 内容 |
| --- | --- |
| V0.0.1-beta | 交互落地：CRUD、退役、回收站、统计实时化、分类与主题、导入导出 |
| V0.0.2-beta | 多清单支持；时间盒风格应用图标 |
| V0.0.3-beta | 移除假数据、昵称引导；照片采样解码与网格懒加载 |
| V0.0.4-beta | 清单级锁定与生物识别解锁 |
| V0.0.5-beta | 数据层迁移至 Room；release 签名发布 |
| V0.0.6-beta | 保质期体系：三选二推算、临期高亮、统计页临期 TOP10 |
| V0.0.7-beta | 首页分类筛选与排序、存放地点、分享二维码 |
| V0.0.8-beta | WebDAV 双向同步（全局 ID / 逻辑时钟 / 墓碑合并 / 照片增量 / 冲突副本） |
| V0.0.9-beta | 同步可靠性加固：清单锁迁移、首次同步默认清单、分类/地点版本传播、照片失败中止重试、远端快照原子写与锁所有权 |

每个里程碑的 release APK 归档于 `releases/<tag>/`，仅本地存档、不入版本库。

## 文档

- [第二阶段 · 交互功能开发计划](docs/Phase2-交互功能开发计划.md)
- [第三阶段 · 多清单开发计划](docs/Phase3-多清单开发计划.md)
- [同步功能需求理解与设计（WebDAV）](docs/Phase4-WebDAV同步需求理解与设计.md)
- [WebDAV 同步开发计划](docs/Phase5-WebDAV同步开发计划.md)
