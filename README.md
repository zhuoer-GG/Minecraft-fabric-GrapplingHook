# zhuoers-mod-template-1.21.11

## 项目简介
这是一个基于 Fabric 的 Minecraft 1.21.11 模组工程，当前实现了勾爪物品体系，包括：
- 5 种勾爪物品注册（石、铁、金、钻石、下界合金）
- 自定义物品组
- 中文本地化
- 物品贴图、模型与客户端物品映射
- DataGen 自动生成配方与成就

## 2026-03-22 核心算法更新
今天完成了勾爪牵引核心算法的稳定化，重点针对多勾爪场景下的抖动与横跳问题。

### 核心问题
- 多勾爪同时存在时，玩家在质心附近反复横跳，无法稳定停靠。
- 早期方案中对相对向量做平滑，存在参考系陷阱，易导致误导向与异常冲量。

### 最终方案（位置驱动 + 阻尼制动）
- 采用世界坐标质心作为唯一目标点：每帧计算所有活跃勾爪的 pull goal 质心。
- 仅由领头勾爪写入玩家动量，避免同 tick 多实体竞争写速度。
- 自适应死区：多勾爪 stopThreshold=0.35，单勾爪 stopThreshold=0.25。
- 二次方阻尼：在 1.5 格范围内使用 (dist/range)^2 衰减，近距离更快降速。
- 最低阻尼因子设置为 0.1，防止速度过小被摩擦直接吃掉。
- 速度上限钳制：finalSpeed <= 1.2，避免多勾爪切换瞬间冲量过大。

### 结果
- 单勾爪停靠稳定性恢复。
- 多勾爪抖动幅度显著下降，牵引逻辑从拉力模拟转为可控的位置驱动。
- 项目构建通过（compileJava / build -x test 均成功）。

## 2026-03-25 参数升级与自适应阻尼更新

### 勾爪速度参数升级（2x）
- STONE: 0.8
- IRON: 1.2
- GOLD: 1.8
- DIAMOND: 1.6
- NETHERITE: 2.0

### 适配高速勾爪的阻尼策略
- 阻尼由固定参数改为按当前 `this.speed` 自动缩放。
- 速度越高，停止死区越大，提前熄火避免质心附近横跳。
- 速度越高，阻尼范围越大，且幂次阻尼更激进（近距离降速更快）。
- 牵引速度上限改为随速度轻微增长，同时保持稳定上限，防止瞬时冲量。

### 结果预期
- 在全档位速度翻倍后，牵引到目标点时仍可自动稳定收敛。
- 多勾爪场景下的抖动会随档位自适应抑制，不再依赖手动重调固定阻尼参数。

## 关键目录说明
- src/main/java/test1/example
  - 模组主逻辑与注册入口代码。
- src/main/java/test1/example/datagen
  - 数据生成器（Recipes、Advancements）。
- src/main/resources/assets/test1
  - 客户端资源：语言、纹理、模型、客户端物品映射。
- src/main/generated/data
  - 由 runDatagen 生成的数据文件（配方、成就等）。
- src/client/java/test1/example
  - 客户端专用逻辑（如客户端初始化类）。

## 关键 Java 文件说明
- src/main/java/test1/example/ZhuoersMod.java
  - 模组主入口（ModInitializer），负责初始化阶段注册物品与物品组。

- src/main/java/test1/example/ModItems.java
  - 统一注册勾爪物品。
  - 采用 1.21.2+ 的注册方式（通过 ResourceKey + Items.registerItem），避免 Item id not set 崩溃。
  - 所有五个勾爪物品已改用 GrapplingHookItem 工厂，并将对应的 ModGrapplingHookTiers 等级传入。

- src/main/java/test1/example/ModItemGroups.java
  - 注册自定义创造模式物品组。
  - 控制勾爪在物品组中的显示顺序和图标。

- src/main/java/test1/example/ModGrapplingHookTiers.java
  - 勾爪材质枚举，定义速度、距离、钩子数量等核心参数。
  - 通过 getSpeed()、getMaxDistance()、getHookCount() 等方法提供配置数据。

- src/main/java/test1/example/GrapplingHookItem.java
  - 勾爪物品的自定义实现类，继承 Item。
  - 绑定对应的 ModGrapplingHookTiers 等级，具备获取速度、距离等参数的能力。
  - 这是触发勾爪发射的入口。后续会通过重写 use 方法或 Fabric 事件系统来监听右键交互，
    并在服务端创建 GrapplingHookEntity 实体。

- src/main/java/test1/example/ZhuoersModDataGenerator.java
  - DataGen 入口。
  - 注册 ModRecipeProvider 和 ModAdvancementProvider。

- src/main/java/test1/example/datagen/ModRecipeProvider.java
  - 生成勾爪配方：
    - 石/铁/金/钻石：有损合成（史莱姆球 + 对应材质 + 锁链）
    - 下界合金：锻造台升级（钻石勾爪 + 下界合金锭 + 锻造模板）

- src/main/java/test1/example/datagen/ModAdvancementProvider.java
  - 生成成就：
    - root：获得石质勾爪
    - challenge：获得下界合金勾爪
  - 根成就背景使用原版成就页石质背景贴图。

## 关键资源文件说明
- src/main/resources/assets/test1/lang/zh_cn.json
  - 物品名、物品组名、成就标题与描述中文翻译。

- src/main/resources/assets/test1/textures/item/*.png
  - 勾爪物品纹理（16x16）。

- src/main/resources/assets/test1/models/item/*.json
  - 物品模型定义（通常 parent = minecraft:item/generated）。

- src/main/resources/assets/test1/items/*.json
  - 客户端物品映射，指向对应模型。

## 常用命令
- 编译主代码：
  - ./gradlew.bat compileJava

- 运行客户端：
  - ./gradlew.bat runClient

- 运行数据生成：
  - ./gradlew.bat runDatagen

## 开发进度

### 已完成
- ✅ 物品注册（ModItems.java）：5 种勾爪物品
- ✅ GrapplingHookItem 类：勾爪物品基础实现（继承 Item，绑定材质等级）
- ✅ 物品组注册（ModItemGroups.java）
- ✅ 物品贴图与模型
- ✅ 中文本地化
- ✅ DataGen 配方生成
- ✅ DataGen 成就生成
- ✅ GrapplingHookEntity 三态流程（FLYING / HOOKED / RETRACTING）
- ✅ 多勾爪质心牵引核心算法（世界坐标质心 + 领头勾爪写入）
- ✅ 多勾爪自适应死区与二次阻尼策略

### 进行中
- 🔲 多勾爪极端场景下的最终参数收敛（边角、高低差、高速连发）
- 🔲 勾爪实体渲染表现优化（命中反馈、链条视觉细节）

### 计划中
- 🔲 冷却时间机制
- 🔲 配置化参数（阻尼范围、死区阈值、最大速度）
