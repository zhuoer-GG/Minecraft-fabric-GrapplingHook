# Grappling Hook 开发工作日志

**项目**: Minecraft 1.21.11 Fabric 勾爪系统完整开发  
**最后更新**: 2026年3月22日  
**状态**: 核心算法完成，进入参数精调阶段

---

## 2026-03-22 当日核心更新（多勾爪稳定化）

### 关键问题复盘
1. 多勾爪抖动与横跳
- 玩家在质心附近持续来回移动，难以停稳。
- 原因是多目标切换下速度更新频繁，且停止条件不够强。

2. 参考系陷阱
- 对玩家相对向量做平滑会引入错误参考系。
- 当玩家自身移动时，相对向量不是固定目标，可能产生错误追逐和瞬时冲量。

3. 近距离控制不足
- 线性阻尼在质心附近减速不够快。
- 死区过小导致进入质心附近后仍被持续微调推动。

### 最终落地方案
1. 逻辑范式切换
- 从拉力模拟转向位置驱动的阻尼制动。
- 直接以世界坐标质心作为唯一目标点。

2. 多勾爪权威写入
- 同一玩家的活跃勾爪中，仅领头勾爪负责写入玩家动量。
- 彻底避免同 tick 多实体写速度竞争。

3. 动态死区（Adaptive Deadzone）
- 单勾爪停止阈值：0.25
- 多勾爪停止阈值：0.35
- 碰撞辅助停止：isBlocked 且 distance < 0.7

4. 二次方强阻尼
- 在 1.5 格内采用二次方衰减：(distance / 1.5)^2
- 阻尼底限 0.1，避免速度过低被摩擦吃掉
- 速度上限钳制 1.2，防止瞬时冲量

5. 死区内微动处理
- 死区内先清零动量并保持无重力
- 对极小位移进行服务器端位置微调，降低物理引擎残余扰动

### 代码变更摘要
- 文件：src/main/java/test1/example/entity/GrapplingHookEntity.java
- 方法：tickHooked(Entity owner)
- 变更点：
   - 替换为世界坐标质心驱动版本
   - 移除旧的相对向量平滑相关逻辑
   - 引入多勾爪动态死区 + 二次阻尼 + 速度上限

### 验证结果
- compileJava: BUILD SUCCESSFUL
- build -x test: BUILD SUCCESSFUL
- 无新增编译错误（仅保留既有 deprecated 提示）

---

## 第一部分：开发过程中遇到的问题及解决方案

### 问题 1：五种材质全显圆石 + 红色光泽
**遇到阶段**: 第一轮游戏测试（消息 13-15）

**症状**:
- 所有勾爪无论等级（石/铁/钻石/金/翡翠）都渲染为圆石贴图
- 勾爪周身带有鲜明的红色光泽，影响视觉效果

**根本原因**:
- `tier` 字段为普通类成员，不通过 SynchedEntityData 同步，客户端始终使用默认值 `STONE`
- 渲染器使用硬编码的 overlay 参数值 `0`，导致启用了伤害状态覆盖层（红色）

**解决方案**:
1. 新增 `DATA_TIER_INDEX` SynchedEntityData 字段，在服务端发射勾爪时同步 tier 索引
2. 在 `getTier()` 方法中，客户端优先读取同步的 tier 索引，而非默认值
3. 在 `GrapplingHookEntityRenderer` 中，将 overlay 参数改为 `OverlayTexture.NO_OVERLAY`

**验证结果**: ✅ 建筑各色勾爪按预期渲染对应材质贴图，无红色光泽

---

### 问题 2：发射距离过短
**遇到阶段**: 第二轮游戏测试（消息 16-20）

**症状**:
- 勾爪仅能射出玩家手边 0.5-1 格距离的矩形柱体，无法看到完整的链条
- 有时甚至看不到任何可视化对象

**根本原因**:
- `range` 和 `speed` 字段未同步到客户端，客户端使用默认值（`range=0`，`speed=0`）
- 客户端在 `tick()` 方法中独立判定"是否超距离"，因为 `range==0`，首帧即判定超距离
- 状态立即切换为 `RETRACTING`，柱体被判定为回收状态，渲染时长极短甚至不显示
- 服务端日志显示 `range=32`（正确），但客户端日志显示 `range=0`（错误）

**调试证据**:
```
Server thread: range=32 ✓（服务端配置正确）
Render thread: range=0 ✗（客户端默认值导致误判）
```

**解决方案**:
1. **新增同步字段**:
   - `DATA_RANGE`: 同步最大飞行距离（单位：格）
   - `DATA_SPEED`: 同步飞行速度（以整数 × 100 形式存储，如 0.4D → 40）
   - `DATA_STATE`: 同步状态机状态（FLYING=0, HOOKED=1, RETRACTING=2）

2. **修改 `onSyncedDataUpdated()` 方法**:
   - 改为单参数版本：`onSyncedDataUpdated(EntityDataAccessor<?> pKey)`
   - 当接收到 `DATA_RANGE` 时，更新本地 `range` 字段
   - 当接收到 `DATA_SPEED` 时，将整数值转换回 double：`speedInt / 100.0`
   - 当接收到 `DATA_STATE` 时，更新本地 `state` 字段

3. **限制距离判定仅服务端执行**:
   - 在 `tick()` 中的距离检查添加条件：`if (!this.level().isClientSide())`
   - 确保客户端不进行独立的状态判定，状态完全由服务端同步决定

4. **所有状态切换改用 `setStateSync()` 方法**:
   - 实现私有方法 `setStateSync(HookState newState)`，内部调用 `this.entityData.set(DATA_STATE, newState.ordinal())`
   - 将 `tickHooked()`、`onHitBlock()`、`onHitEntity()` 中的所有状态赋值改为 `setStateSync()` 调用
   - 确保状态变更立即同步到所有监听客户端

5. **防止视锥体剔除**:
   - 重写 `shouldRender(double pX, double pY, double pZ)` 方法返回 `true`
   - 原因：渲染器绘制的是从玩家手部到实体的连接线，即使实体在视锥体外，线段仍可能在视野内

6. **构造函数中显式同步初始值**:
   - 在 `GrapplingHookEntity(...)` 构造函数中，添加：
     ```java
     this.entityData.set(DATA_RANGE, this.range);
     this.entityData.set(DATA_SPEED, (int) Math.round(this.speed * 100.0));
     ```
   - 确保实体创建时客户端立即获得准确的 range 和 speed

**验证结果**: ✅ 勾爪完整链条可见，距离判定精准，状态同步一致

---

### 问题 3：编译错误（网络同步修复时引入）
**遇到阶段**: 第三轮修复（消息 21-最新）

**症状**:
- 编译失败，5 个编译错误

**具体错误**:
1. `EntityDataSerializers.DOUBLE` 找不到符号
   - 原因：Minecraft 1.21.11 的 EntityDataSerializers 中不存在 DOUBLE 常量
   - 解决：改用 `EntityDataSerializers.INT`，速度值乘以 100 后作为整数存储

2. `onSyncedDataUpdated(List<EntityDataAccessor<?>>)` 方法签名不匹配
   - 原因：父类 Entity 的实际方法签名为 `onSyncedDataUpdated(EntityDataAccessor<?> pKey)`（单参数）
   - 解决：改为单参数版本，用 `if (pKey == DATA_*)` 代替 `if (pList.contains(...))`

3. `isClientSide` 属性访问权限为 private
   - 原因：Level 类中 `isClientSide` 是私有字段，不能直接访问
   - 解决：改用 `this.level().isClientSide()` 方法调用

**修复流程**:
- 更新 `DATA_SPEED` 从 `EntityDataSerializers.DOUBLE` 改为 `EntityDataSerializers.INT`
- 更新 `defineSynchedData()` 中的默认值从 `0.4D` 改为 `40`（表示 0.4 × 100）
- 重写 `onSyncedDataUpdated()` 方法，改为单参数版本
- 在构造函数中同步初始值时，速度参数改为：`(int) Math.round(this.speed * 100.0)`
- 在 `onSyncedDataUpdated()` 中，接收 speed 时转换回 double：`speedInt / 100.0`
- 修改所有 `this.level().isClientSide` 为 `this.level().isClientSide()`

**验证结果**: ✅ BUILD SUCCESSFUL，3 个任务执行，2 个编译任务通过

---

## 第二部分：当前需要改进的内容

### 改进需求 1：勾爪打击和牵引系统
**优先级**: 🔴 高

**当前问题**:
- 勾爪触碰到方块后进入 `HOOKED` 状态并停止移动
- 缺少"牵引玩家"的逻辑，勾爪仅固定在命中点
- 由于没有回收机制，即使是石质等低速勾爪也可能发射出多个被阻塞且未收回的矩形柱体
- 长时间游戏可能导致性能问题（客户端需要渲染多个未清理的勾爪对象）

**待实现功能**:
1. **牵引力学**: 当勾爪 is `HOOKED` 时，对玩家施加指向勾爪锚点的拉力
   - 需要在玩家端和勾爪端之间建立双向通信
   - 参考 Minecraft 挂钩枪、魔改 mod 的实现方案

2. **自动收回机制**: 
   - 实现玩家可主动收回勾爪（例如长按右键）
   - 或设置自动收回条件（例如玩家靠近勾爪、或经过 N 秒后自动回收）

3. **多勾爪管理**:
   - 追踪玩家发射的勾爪列表
   - 在玩家重新发射前自动清理前一个勾爪

---

### 改进需求 2：参数平衡与配置优化
**优先级**: 🟡 中

**当前问题**:
- 各材质等级的 `maxDistance`（最大距离）和 `speed`（飞行速度）参数需要平衡调整
- 当前设置可能不能充分体现各材质间的性能差异
- 不同场景下（地底矿洞、架桥、爬山）的易用性可能不均衡

**参数需要重新评估**:
```
石质勾爪 (STONE):
  - 当前: maxDistance=12, speed=0.4
  - 评估: 是否太弱？新手玩家体验如何？

铁质勾爪 (IRON):
  - 当前: maxDistance=16, speed=0.5
  - 评估: 与石质的质变是否足够明显？

钻石勾爪 (DIAMOND):
  - 当前: maxDistance=24, speed=0.7
  - 评估: 高级玩家的期望距离？

```

**优化方向**:
- 进行大量游戏内测试，收集用户反馈
- 对比其他 mod（如 Grapple mod）的参数设计
- 考虑加入难度系数（简单模式放松限制、困难模式严格要求）

---

### 改进需求 3：运动学与场景适配
**优先级**: 🟡 中

**当前问题**:
- 勾爪飞行时的碰撞检测是静态的，未考虑玩家和周围物体的实时移动
- 当玩家在勾爪飞行过程中移动，可能导致：
  - 玩家与勾爪链条交叉穿模
  - 牵引方向计算误差（回收时朝向旧位置）
  - 勾爪命中判定不准确（移动目标的判定延迟）

**需要处理的场景**:
1. **玩家移动期间发射勾爪**
   - 需要在勾爪 flying 阶段，实时更新与玩家的相对位置计算
   - 链条渲染应该连接到玩家当前位置，而非发射时的位置

2. **勾爪飞行中玩家移动**
   - 飞行方向应该是相对于玩家初始视线，还是动态追踪？
   - 距离判定：是相对初始位置，还是相对当前位置？

3. **牵引过程中障碍物干扰**
   - 如果牵引路径被挡住，应该如何处理（停止、绕过、立即失效）？

4. **多人游戏同步**
   - 网络延迟下，其他玩家看到的勾爪位置和自己看到的可能不一致
   - 需要考虑预测算法或容错机制

**待研究的优化方向**:
- 采用"视线锁定"vs"自由飞行"模式
- 实现碰撞预测（raycast 提前检测障碍）
- 加入"物理锚点"概念（勾爪自动吸附到最近的有效表面）
- 评估是否需要"客户端预测"来掩盖网络延迟

---

## 第三部分：项目架构总结

### 核心类与职责
```
GrapplingHookEntity（src/main/java）
  ├─ 三态状态机：FLYING → HOOKED → RETRACTING
  ├─ SynchedEntityData 字段同步（tier, range, speed, state）
  ├─ 服务端距离判定与碰撞处理
  └─ 与玩家所有者进行双向通信

GrapplingHookEntityRenderer（src/client/java）
  ├─ 矩形柱体几何生成
  ├─ 手部锚点动态计算
  ├─ 材质贴图按 tier 选择
  └─ Overlay 移除（OverlayTexture.NO_OVERLAY）

ModGrapplingHookTiers（枚举）
  ├─ 五种等级配置（STONE, IRON, DIAMOND, GOLD, EMERALD）
  └─ 每个等级的 maxDistance、speed、对应方块贴图

GrapplingHookItem（右键物品）
  └─ 玩家右键时在服务端创建 GrapplingHookEntity 实例

ModKeyBindings / ZhuoersModClient
  └─ 按键绑定注册与客户端初始化
```

### 编译与运行
- **编译**: `.\gradlew.bat compileJava compileClientJava`
- **运行**: `.\gradlew.bat runClient`
- **当前状态**: ✅ BUILD SUCCESSFUL，游戏运行正常

---

## 第四部分：后续工作计划

### 短期（本周内）
- [ ] 实现勾爪牵引玩家逻辑（拉力向量、阻尼、约束）
- [ ] 添加勾爪主动收回机制（长按右键释放）
- [ ] 多勾爪自动清理（避免堆积）

### 中期（本月）
- [ ] 游戏内大规模参数平衡测试
- [ ] 实现高级运动学（碰撞预测、玩家移动同步）
- [ ] 添加视觉反馈（勾爪发射、命中、回收的粒子效果 / 声音）

### 长期（功能完善）
- [ ] 多人游戏网络同步验证
- [ ] 性能优化（批量渲染、LOD 等级）
- [ ] 与其他 mod 的兼容性测试
- [ ] 完整的配置文件系统（JSON / TOML）

---

## 附录：关键代码片段参考

### SynchedEntityData 同步字段定义
```java
// 在 GrapplingHookEntity 中
private static final EntityDataAccessor<Integer> DATA_TIER_INDEX = ...INT
private static final EntityDataAccessor<Integer> DATA_RANGE = ...INT
private static final EntityDataAccessor<Integer> DATA_SPEED = ...INT（100倍）
private static final EntityDataAccessor<Integer> DATA_STATE = ...INT（0-2）
```

### onSyncedDataUpdated 单参数版本
```java
@Override
public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
    super.onSyncedDataUpdated(pKey);
    
    if (pKey == DATA_SPEED) {
        int speedInt = this.entityData.get(DATA_SPEED);
        this.speed = (double) speedInt / 100.0;
    }
    // ... 其他字段处理
}
```

### 距离判定限制范围
```java
if (!this.level().isClientSide() && this.state == HookState.FLYING && owner != null) {
    // 仅服务端执行距离检查
}
```

---

**文档完成日期**: 2026年3月21日  
**下一次更新**: 完成牵引系统实现后
