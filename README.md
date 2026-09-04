# Simple Cheat

Minecraft 1.20.1 的纯客户端 Fabric 模组，用来降低单人游戏难度。服务端不需要安装。

## 功能

### 杀戮光环（`R`）

自动攻击范围内的敌对生物。

### 远程防护（`B`）

检测到箭、火球这类投射物飞来时自动举盾格挡。

### 自动闪避（`V`）

预测投射物的飞行轨迹，提前走位躲开。

### 自动搭路（`G`）

在脚下自动放方块，走到哪铺到哪。

## 设置

按 `右 Shift` 打开设置界面，所有选项都在里面，鼠标悬停会显示说明。

左键点模块名开关，右键点模块名展开设置。改动在关闭界面时自动存到 `.minecraft/config/simple-cheat.json`。

按键可以在游戏内「选项 - 控制 - 按键绑定」的 Simple Cheat 分类里改。

## 环境要求

- Minecraft 1.20.1
- Fabric Loader 0.15.0 或更高
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.92.x（1.20.1 版本）
- Java 17 或更高

## 安装

1. 用 [Fabric 安装器](https://fabricmc.net/use/installer/)为 1.20.1 安装 Fabric Loader。
2. 把 Fabric API 的 jar 放进 `.minecraft/mods/`。
3. 把 `build/libs/simple-cheat-1.0.0.jar` 也放进 `.minecraft/mods/`（不要放 `-sources.jar`）。

## 构建

```bash
./gradlew build      # 产物在 build/libs/
./gradlew runClient  # 直接启动带模组的游戏
```

## 添加新模块

在 `module/` 下新建一个类继承 `Module`，构造函数里用 `addSettings(...)` 注册设置项，重写 `onTick()` 写逻辑，然后在 `ModuleManager` 的构造函数里加进 `modules` 列表。按键绑定、配置存取、设置界面、HUD 都会自动接上。

## 注意

这是个作弊模组，只建议在单人存档或自己的服务器上用。多数联机服务器禁止这类模组，可能会被封禁。

## 许可

MIT
