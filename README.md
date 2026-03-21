# zhuoers-mod-template-1.21.11

## 项目简介
这是一个基于 Fabric 的 Minecraft 1.21.11 模组工程，当前实现了勾爪物品体系，包括：
- 5 种勾爪物品注册（石、铁、金、钻石、下界合金）
- 自定义物品组
- 中文本地化
- 物品贴图、模型与客户端物品映射
- DataGen 自动生成配方与成就

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

### 进行中
- 🔲 GrapplingHookEntity 实体：实现勾爪射出和飞行逻辑
- 🔲 右键交互检测：通过事件系统监听右键点击，发射勾爪
- 🔲 勾爪实体渲染：自定义渲染器显示细长矩形柱体

### 计划中
- 🔲 勾爪击中目标检测
- 🔲 玩家被拉动逻辑
- 🔲 冷却时间机制
