# CarSickFix — 晕车缓解触摸修复（LSPosed）

## 问题

小米澎湃 OS「晕车缓解」会创建一个全屏悬浮窗（`SecurityCenterCarSickness`，
`com.miui.securitycenter`，type=`SYSTEM_OVERLAY`，alpha=0.8）。

这个窗口没有 `PRIVATE_FLAG_TRUSTED_OVERLAY`，输入系统会把经过它的触摸事件
标记为 `FLAG_WINDOW_IS_OBSCURED`（“窗口被遮挡”）。实现了防点击劫持过滤的
应用（例如 GitHub）会直接丢弃这类事件，表现为界面点不动、滑不动。

对照实验：
- 关掉晕车缓解：`targetFlags=FOREGROUND | SPLIT`
- 打开晕车缓解：`targetFlags=FOREGROUND | WINDOW_IS_OBSCURED | SPLIT`
- 换成 Google 的「防晕车辅助」（SystemUI 窗口，带 `TRUSTED_OVERLAY`）：没有该标志

另外实测表明小米这边**与透明度无关**：把窗口 alpha 降到 0.1 仍会被标记，
所以只能走 trusted overlay 这条路。

## 方案

LSPosed 模块，作用域 `com.miui.securitycenter`（及其它安全服务进程）。

Hook `android.view.WindowManagerImpl.addView` / `WindowManagerGlobal.addView`，
在窗口加入 WindowManager 之前，给标题包含 `carsick` 的 `LayoutParams` 补上
`PRIVATE_FLAG_TRUSTED_OVERLAY`（通过反射读取该常量，避免版本相关硬编码）。

效果（实测）：
- 窗口输入属性变为 `TRUSTED_OVERLAY`
- GitHub 的触摸 `targetFlags` 恢复为 `FOREGROUND | SPLIT`
- 晕车缓解开启时 GitHub 可以正常滚动、点击
- 圆点外观不变（窗口 alpha 未改）

## 安装

```sh
pm install -r carsickfix.apk
```

然后在 LSPosed 管理器里启用「晕车缓解触摸修复」，作用域勾选：
`com.miui.securitycenter`、`com.miui.securitycore`、`com.miui.securityadd`。

启用后不需要重启系统，只要重启目标应用即可生效：

```sh
am force-stop com.miui.securitycenter
```

## 验证

```sh
# 打开晕车缓解后，看触摸事件的 targetFlags
dumpsys input | grep -A3 TouchStatesByDisplay
```

看到 `targetFlags=FOREGROUND | SPLIT`（不含 `WINDOW_IS_OBSCURED`）即为正常。

## 构建

```sh
bash build.sh
# 产物：build/carsickfix.apk
```

脚本用 aapt2 + javac + d8 + apksigner 直接打包，不依赖 Gradle。
`src/de/robv/...` 是编译期的 Xposed API 桩，不会打进 APK。
