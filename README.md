# 小米晕车缓解触摸修复 (Xiaomi CarSickFix)

> LSPosed 模块 · 让小米澎湃 OS 的「晕车缓解」不再让某些应用点不动

![icon](icon/preview.png)

## 问题

小米澎湃 OS 的「晕车缓解」会创建一个全屏悬浮窗
（`SecurityCenterCarSickness`，由 `com.miui.securitycenter` 创建，
type=`SYSTEM_OVERLAY`，alpha=0.8）。

这个窗口没有 `PRIVATE_FLAG_TRUSTED_OVERLAY`，输入系统会把经过它的触摸事件
标记为 `FLAG_WINDOW_IS_OBSCURED`（“窗口被遮挡”）。实现了防点击劫持过滤的
应用（例如 GitHub 官方客户端）会直接丢弃这类事件，表现为**整个界面点不动、
滑不动**，而其它应用看起来一切正常。

实测证据（`dumpsys input` 的 `TouchStatesByDisplay`）：

| 场景 | 触摸事件的 targetFlags |
|---|---|
| 关闭晕车缓解 | `FOREGROUND \| SPLIT` |
| 开启晕车缓解 | `FOREGROUND \| WINDOW_IS_OBSCURED \| SPLIT` |
| 开启 Google「防晕车辅助」（SystemUI 窗口，带 trusted overlay） | `FOREGROUND \| SPLIT` |

> 注意：**与透明度无关**。把窗口 alpha 从 0.8 一路降到 0.1，事件依然会被标记为
> 被遮挡。所以唯一的解决办法是让这个窗口成为可信悬浮窗。

## 原理

LSPosed 模块，作用域 `com.miui.securitycenter` 等安全服务进程。

Hook `android.view.WindowManagerImpl.addView` / `WindowManagerGlobal.addView`，
在窗口加入 WindowManager 之前，给标题包含 `carsick` 的 `LayoutParams` 补上
`PRIVATE_FLAG_TRUSTED_OVERLAY`（常量通过反射读取，不硬编码）。

只影响标题匹配的窗口，圆点外观不变（没有改 alpha）。

## 效果

| | 修复前 | 修复后 |
|---|---|---|
| 窗口输入属性 | `NOT_FOCUSABLE \| NOT_TOUCHABLE` | `NOT_FOCUSABLE \| NOT_TOUCHABLE \| TRUSTED_OVERLAY` |
| 下层应用触摸 | `WINDOW_IS_OBSCURED` | 正常 |
| 晕车缓解开启时在 GitHub 里操作 | 无响应 | 正常点击、滚动 |

已在 Xiaomi M098FE（OS4.0.7 / Android 17）验证。

## 安装

1. 下载 [Releases](https://github.com/YunYanShaoKaoJiang/carsickfix/releases) 里的 APK 并安装
2. 在 LSPosed 管理器中启用「晕车缓解触摸修复」
3. 作用域勾选：`com.miui.securitycenter`、`com.miui.securitycore`、`com.miui.securityadd`
4. 让目标进程重新启动以载入模块：

```sh
am force-stop com.miui.securitycenter
```

不需要重启系统。

## 验证

打开晕车缓解后，按住屏幕并执行：

```sh
dumpsys input | sed -n '/TouchStatesByDisplay/,+4p'
```

看到 `targetFlags=FOREGROUND | SPLIT`（不含 `WINDOW_IS_OBSCURED`）即为生效。

## 构建

```sh
bash build.sh
# 产物：build/xiaomicarsickfix.apk
```

脚本直接使用 `aapt2` + `javac` + `d8` + `apksigner`，不依赖 Gradle。
`src/de/robv/...` 是编译期用的 Xposed API 桩，不会打进 APK。

需要修改 `build.sh` 顶部的 SDK 路径（默认 `/opt/android-sdk`）。

## 已知限制

- 如果小米后续更换该窗口的标题，或改为由 `system_server` 创建窗口，本模块需要相应调整
- 本模块只解决“触摸被判定为被遮挡”，不会改变晕车缓解本身的动画行为

## 图标说明

图标中的 “mi” 图形为小米公司商标，此处仅作个人使用/标识用途，与小米公司无关。
本项目与小米、Google、GitHub 均无隶属关系。

## License

MIT
