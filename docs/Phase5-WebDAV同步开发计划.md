# 时物 App · WebDAV 同步开发计划（feature/webdav-sync）

> 状态：Phase A-C 已完成（单元测试通过）· 2026-08-15 · 分支 `feature/webdav-sync`
>
> 设计依据：`docs/Phase4-WebDAV同步需求理解与设计.md`（已确认：全量快照 + 互斥锁 + 记录级 LWW + 冲突副本）

## 一、目标

局域网 NAS（WebDAV）双向同步：手机与平板各自离线增删改，回家手动同步后数据一致、不丢修改、结果确定。v1 手动触发，不做自动同步与载荷加密。

## 二、总体方案（与 Phase4 对齐）

| 主题 | 方案 |
| --- | --- |
| 同步模型 | 全量快照 `shiwu/data.json` + `sync.lock` 互斥锁（租约 5 分钟）+ 记录级 LWW |
| 全局 ID | `id = (deviceId << 40) | seq`，存量数据启动时一次性迁移并重写引用 |
| 版本 | 每条记录 `rev` + `updatedBy`（逻辑时钟），LWW：rev 大者胜，同 rev 比 deviceId |
| 删除 | items / wishes / lists 全部软删（墓碑参与合并，防止复活） |
| 冲突 | 自动 LWW；输方存为「（冲突副本）」新记录，同步报告可见 |
| 分类 | v1 简化：按名称并集合并（离线重命名不追源，名称允许重复） |
| 照片 | 快照只存文件名；按 `photos/` 目录增量上传 / 下载，本地映射回绝对路径 |
| 凭据 | Android Keystore AES 加密存储账号密码，UI 不回显 |
| 不同步 | 清单锁、主题、昵称、next 计数器等设备本地设置 |

## 三、任务拆分

### Phase A：数据层（Room v3 → v4）

- [x] `deviceId`（23 位随机）生成并持久化；`globalSyncId(deviceId, seq)` 分配器
- [x] 存量数据一次性迁移：items / wishes / lists 重分配全局 id，事务内重写 `listId` / `purchasedItemId` / `current_list_id`
- [x] items / wishes / lists 增加 `rev`、`updatedBy`；wishes / lists 增加软删字段
- [x] 删除行为改为软删（deleteWish / deleteList / 相关 UI 过滤墓碑）
- [x] JSON 序列化同步扩展（导出不含墓碑的展示逻辑不变）

### Phase B：同步核心

- [x] `WebDavClient`（HttpURLConnection）：GET / PUT / DELETE / MKCOL / PROPFIND + `If-None-Match` 锁
- [x] `SyncModels`：快照序列化 / 反序列化（照片名 ↔ 本地路径映射）
- [x] `MergeEngine`（纯 Kotlin，可单测）：LWW + 冲突副本 + 孤儿物品归位 + 同步报告
- [x] `WebDavSyncManager`：锁 → 下载 → 合并 → 照片增量 → 上传 → 写回本地库 → 报告
- [x] 首次引导三情形：本地有远端空 / 本地空远端有 / 两端都有（直接合并）

### Phase C：界面

- [x] 「我的」页新增「WebDAV 同步」入口
- [x] 同步配置页：地址 / 账号 / 密码（Keystore 加密）/ 测试连接 / 立即同步
- [x] 同步中禁止重复触发；结果报告（新增 / 更新 / 删除 / 冲突）
- [x] Manifest：`INTERNET` 权限 + 允许局域网明文 HTTP

### Phase D：逻辑验证（手机不在电脑旁）

- [x] JVM 单元测试：全局 ID 跨设备不碰撞、快照往返、合并引擎全部场景（双新增 / 双编辑 LWW / 编辑 vs 删除 / 双删 / 清单删除后孤儿物品 / 分类并集 / 冲突副本）—— 12 项全部通过
- [x] `./gradlew test` + debug 构建通过
- [ ] 真机演练（后续用户提供环境）：Phase4 文档第九节的 7 个场景

## 六、落地偏差记录

- 冲突副本通过名称追加「（冲突副本）」标记，未新增 `conflictOf` 字段（v1 简化）；
- 分类按名称并集合并，离线重命名不追源（v1 简化）；
- `purgeItem`（回收站彻底删除）仍为硬删除，不产生墓碑（边缘场景，v1 接受）。

## 四、合并规则（MergeEngine）

| 场景 | 处理 |
| --- | --- |
| 仅一端有记录 | 采纳（加入合并结果） |
| 两端同 rev 同 updatedBy | 视为同一记录，无冲突 |
| 两端都改（rev 不同） | rev 大者胜；输方（非墓碑）存为冲突副本 |
| 一端改、一端删 | 删除胜 → 修改存为冲突副本；修改胜 → 删除失效 |
| 两端都删 | 保留一个墓碑 |
| 清单删除胜但物品仍活跃 | 物品归入默认清单（「我的物品」），计入报告 |
| 分类 | 名称并集，两端都保留 |

## 五、验证口径

- 单元测试覆盖合并引擎与 ID 逻辑（不依赖设备）；
- 编译通过（debug / release）；
- 真机验证延迟到用户提供手机 / NAS 环境后进行。
