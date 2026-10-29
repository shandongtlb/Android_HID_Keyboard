# Android 17 适配说明

1.7.0 使用 Android 17 SDK（37.0），targetSdk 37，minSdk 26。应用名称为 HID 键盘 / HID Keyboard；包名仍为 `org.shandongtlb.hidkeyboard`。发布包沿用 1.6.0 的签名，不需要卸载旧版。

## 已处理的行为变化

- **系统栏与挖孔**：目标 SDK 35 起的 edge-to-edge 行为、36 起不能退出的要求，通过 `WindowCompat` 和 `WindowInsetsCompat` 处理。内容区与底部状态栏避让系统栏、标题栏、挖孔和强制系统手势区域。
- **大屏与窗口缩放**：目标 SDK 37 后，大屏不能再依靠 `screenOrientation` 保持横屏。键盘/鼠标内容在宽裕窗口内填满可用区域；窄窗口提供双向滚动，避免把完整键盘挤成无法辨认的细条。触摸板和滚轮区域保留自身手势。
- **输入生命周期**：窗口改变大小或收到配置变化时，清除本地按键状态并尝试发送释放报文；Fn 标签与按压反馈同步复位。
- **返回操作**：使用更新后的 AndroidX Activity/AppCompat 与平台返回回调，未依赖已经退出推荐路径的 `onBackPressed()` 覆盖。
- **运行时反射与消息队列**：应用的调度使用公开 `Handler` API，不访问 `MessageQueue` 私有字段，也不修改 `static final` 字段。保留 libsu 6.0.0 的 root 启动机制；其系统内部接口兼容性仍需要对应 ROM 的 root 实测。
- **动态加载、网络及后台行为**：发布包没有自有 native 库，不使用网络、后台音频或后台 Android 服务。没有为 Android 17 新增联网权限、局域网权限、开机接收器或 USB Gadget 配置操作。

## 验证范围

`HidReportStateTest` 检查报文状态、重复触摸、组合键与释放逻辑。`HidScreenTest` 使用 Robolectric 4.17，在 API 26 与 API 37 检查真实布局的测量、窄窗口滚动、竖屏鼠标布局、尺寸变化回调和重试按钮；API 37 额外检查系统栏/挖孔 Insets 的重复分发。

这些是 JVM 上的 Android 框架模拟测试，不是 Android 17 手机、完整模拟器或 USB 主机测试，不能据此宣称所有 ROM、KernelSU/Magisk 或 HID 驱动都已验证。

真机验收应包含：授权 root、连接 HID 主机、键盘与鼠标输入、组合键、切换界面、系统返回、旋转/分屏、拔插 USB 后重试，以及退到后台后按键是否释放。X9U 的 USB/root 链路尚未在本构建环境实测。

## 官方参考

- [Android 17：目标版本行为变化](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Android 17：所有应用行为变化](https://developer.android.com/about/versions/17/behavior-changes-all)
- [Android 16：目标版本行为变化](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Views 的 edge-to-edge 适配](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [Android API 与 AGP 最低版本](https://developer.android.com/build/releases/about-agp)
- [Robolectric 支持的 Android API 范围](https://robolectric.org/compatibility_table/)
- [Robolectric 的 JDK 模块访问配置](https://robolectric.org/getting-started/#running-with-java-17-and-higher)
