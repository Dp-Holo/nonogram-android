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

- 10 张手工设计的关卡：5×5、10×10、15×15 三种尺寸
- 轻点涂色，长按打叉（标记「确定不涂」），另有「标记」模式适合触控不便时使用
- 每 5 格加粗分隔线，行/列提示满足后自动变暗，方便推进
- 撤销 / 重来 / 关卡列表
- 自动计时、最佳成绩、断点续玩（退出后进度自动保存）
- 过关弹窗，可直接进入下一关
- 深色主题，适配状态栏与导航栏

## 关卡

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

## 安装

**方式一（推荐）**：打开 [最新 Release](https://github.com/Dp-Holo/nonogram-android/releases/latest)，
下载下方的 `app-debug.apk`，点开安装（首次会提示「允许安装未知应用」，按提示允许即可）。

**方式二**：在手机的文件管理里打开 `Download/DSHA/app-debug.apk`（构建产物已提前放好一份）。

> 需要 Android 6.0 (API 23) 及以上；APK 使用 debug 签名，可正常安装，但升级覆盖时需保持同一签名。

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
