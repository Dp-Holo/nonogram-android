# 数织 · Nonogram (Android)

[![Android CI](https://github.com/Dp-Holo/nonogram-android/actions/workflows/android.yml/badge.svg)](https://github.com/Dp-Holo/nonogram-android/actions/workflows/android.yml)

一个可以直接在安卓手机上玩的**数织 / 数回 / Nonogram / Picross**小游戏。
纯 Kotlin + 自绘 View 实现，无第三方依赖，由 GitHub Actions 自动构建 APK。

> 玩法：每一行/列左侧、上方的数字，表示该行/列中连续涂色格子的长度与顺序。
> 例如提示 `3 1` 表示「先连涂 3 格，中间至少空 1 格，再涂 1 格」。
> 把所有该涂的格子涂满、不该涂的保持空白，就画出一幅像素图。

## 预览

第 5 关「心形」涂完之后是这样（`#` = 涂色，`·` = 空白）：

```
· · # # · · # # · ·
· # # # # · # # # #
# # # # # # # # # #
# # # # # # # # # #
# # # # # # # # # #
# # # # # # # # # #
· # # # # # # # # ·
· · # # # # # # · ·
· · · # # # # · · ·
· · · · # # · · · ·
```

## 功能

**玩法**

- 轻点：涂色 / 取消涂色
- 按住拖动：从按下的格子开始**连续涂色**（按在已涂格子上则连续擦除），一次拖动 = 一步撤销
- 长按不动：打叉（标记「确定不涂」）；「标记」模式下点按/拖动都是打叉
- 每 5 格加粗分隔线，行/列提示满足后自动变暗；自动计时、最佳成绩、断点续玩

**关卡**

- 10 张手工设计的内置关卡：5×5、10×10、15×15
- 🎲 **随机出题**：任意尺寸 5×5 ~ 20×20，预设 5×5 / 10×10 / 15×15；生成器会做「行列约束传播」求解校验，**保证逻辑可解（唯一解）**，不用猜
- 🖼 **图片导入**：从相册选一张图，选尺寸、拖阈值、可反色，实时预览后直接开玩
- 生成的关卡自动进入「我的关卡」，可重玩、可删除（长按可选关卡列表右侧 ✕）

> 自动生成 / 自编关卡 / 图片转关卡的后续计划见 [PLAN.md](PLAN.md)。

## 内置关卡

| # | 名称 | 尺寸 |
| - | ---- | ---- |
| 1 | 爱心 | 5×5 |
| 2 | 小树 | 5×5 |
| 3 | 十字 | 5×5 |
| 4 | 沙漏 | 5×5 |
| 5 | 心形 | 10×10 |
| 6 | 星星 | 10×10 |
| 7 | 火箭 | 10×10 |
| 8 | 猫咪 | 10×10 |
| 9 | 蘑菇 | 10×10 |
| 10 | 大爱心 | 15×15 |

## 交互速查

| 操作 | 效果 |
| --- | --- |
| 轻点格子 | 涂色 / 取消涂色 |
| 按住并滑动 | 连续涂色（从起点开始，一次手势可整体撤销） |
| 长按后松手 | 打叉（确定不涂） |
| 底部「标记」 | 切换标记模式：点按/滑动都打叉 |
| 底部「关卡」 | 内置关卡 + 我的关卡 + 随机出题 + 图片导入 |

## 安装

**方式一（推荐）**：打开 [最新 Release](https://github.com/Dp-Holo/nonogram-android/releases/latest)，
下载下方的 `app-release.apk`，点开安装（首次会提示「允许安装未知应用」，按提示允许即可）。

**方式二**：在手机的文件管理里打开 `Download/DSHA/app-release.apk`（构建产物已提前放好一份）。

> 需要 Android 6.0 (API 23) 及以上。

## 签名说明

仓库里的 `keystore/nonogram-release.p12` 是自用的**固定签名**（口令见 `app/build.gradle.kts`），
好处是每次 CI 构建的 APK 签名一致，可以直接覆盖升级；但公开仓库等于公开密钥，仅适合自用/测试分发，
正式上架请换成私钥并由 CI secret 注入。

## 从源码构建

本仓库只依赖标准 Android 工具链，产物由 CI 构建，无需本地环境：

```bash
# 一次性：安装 JDK 17 + Android SDK (platform 34, build-tools 34.0.0)
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

每次推送到 `main` 分支，GitHub Actions 都会自动构建并把 APK 作为 artifact 上传；
打 `v*` tag（例如 `v1.0.0`）时会自动生成 Release 并附上 APK。

## 代码结构

```
app/src/main/java/com/dsha/nonogram/
├── MainActivity.kt   # 界面搭建、计时、存档、弹窗
├── BoardView.kt      # 棋盘自绘 + 点按/长按交互
├── GameState.kt      # 单局状态、撤销栈、过关判定
├── Puzzles.kt        # 10 个关卡的答案数据（# = 涂色）
└── Prefs.kt          # SharedPreferences 进度存储
```

## 许可证

[MIT](LICENSE)
