# HID Keyboard Plus / HID 键盘优化版

基于 [Sucareto/Android_HID_Keyboard](https://github.com/Sucareto/Android_HID_Keyboard) v1.5.2 的稳定性改进版。用已 root 的 Android 手机，通过 USB 连接电脑等设备，发送键盘与鼠标输入。

## 1.6.0 的变化

- 键盘与鼠标分别使用 8 字节、4 字节报文，修复鼠标初始化写错数组。
- 按每个按钮的实际按下记录管理状态：重复按下/取消不会改变其他修饰键；按下后切换 Fn 标签仍能正确松开原按键；Ctrl+Alt+Delete 松开后保留另外按住的 Ctrl。
- 用 libsu 6.0.0 的普通绑定 root service 直接写 HID 节点。删除旧的 `libsu:io` 管道转发，保留每条 HID 报文的写入边界。
- 所有设备打开、写入和关闭均在后台执行。设备采用 `O_NONBLOCK`，每次写入最长等待 200 ms；发送队列有上限，异常时停止会话并提示重试。
- 键盘、鼠标共享前台连接；失去焦点、切后台、退出时清空待发输入并尝试发送释放报文。关闭资源不以释放报文成功为前提。
- 鼠标移动按 16 ms 合并，按钮边沿与点击保持有序；保留滚轮未满一格的位移。
- 增加简体中文连接状态、重试与释放按键按钮。
- 独立包名 `org.shandongtlb.hidkeyboard`，可与原版共存；debug 构建使用 `.debug` 后缀。

## 使用

1. 手机需要 root，并且内核已启用 USB HID Gadget 支持。
2. 使用 [USB Gadget Tool](https://github.com/tejado/android-usb-gadget) 或自己的配置方式，先启用键盘和鼠标 Gadget。仍约定 `/dev/hidg0` 为键盘、`/dev/hidg1` 为鼠标；本应用不修改 Gadget 配置。
3. 连接受控设备，打开本应用并授权 root。看到“键盘、鼠标已连接”后操作。仅有键盘节点时仍可使用键盘。
4. `Fn + Space` 切换鼠标界面，`Fn + Enter` 发送 Ctrl+Alt+Delete。鼠标返回按钮返回键盘。
5. 断开 USB 后若显示错误，重新连接并点击“重试”。“释放按键”用于清除本应用记录的按键和按钮状态。

首次验证兼容性时，保持 USB Gadget Tool 的开机自动创建、启用关闭。其上游明确说明部分设备开机启用可能导致循环重启。此版本没有在 X9U 上完成 USB 实测，编译与单元测试通过不等同于设备兼容性验证。

## root 操作范围

`HidRootService` 的 Binder 接口只接受设备编号和固定长度 HID 报文，不接受文件路径或 shell 命令。它检查调用 UID，只打开上述两个固定字符设备；使用 `O_NOFOLLOW` 并检查设备类型，且没有创建、截断普通文件的打开标志。

root helper 为普通绑定服务，不使用 daemon 模式、不注册开机启动、不改启动分区或 SELinux、不联网。libsu 会在应用私有缓存中准备自己的启动 JAR，并通过 `su` / `app_process` 启动该 helper。应用关闭连接或进程退出时 helper 随绑定生命周期结束。

超时及释放处理以驱动正常遵守非阻塞 I/O 约定为前提；无法保证修复内核或厂商 USB 驱动本身的死锁、崩溃，也无法保证拔线后主机一定收到了最后一次释放报文。

## 构建与验证

需要 JDK 17、Android SDK Platform 34 和 Build Tools 34.0.0：

```sh
bash ./gradlew testDebugUnitTest lintRelease assembleDebug assembleRelease
```

- `app/build/outputs/apk/debug/`：可安装的 debug APK，使用独立 `.debug` 包名。
- `app/build/outputs/apk/release/`：未提供签名配置时输出 unsigned APK。
- 单元测试覆盖重复触摸、重复释放、组合键共享修饰键、Fn 映射变化、六键报告、鼠标状态及 root 接口报文长度检查。
- GitHub Actions 对 push/PR 自动运行测试、Lint 和构建，在运行页面提供 APK、SHA-256 和检查报告。
- Gradle Wrapper 使用官方 8.2 版本，并固定 Gradle 分发包的 SHA-256 校验值。

### 自己的 release 签名

原作者的签名密钥不在本仓库，本版不能覆盖原作者签名的 APK。后续更新同一包名需要继续使用相同的自有密钥。

本地构建通过环境变量读取签名信息，不要把密钥或密码提交到仓库：

- `HID_KEYSTORE`：keystore 的绝对路径。
- `HID_STORE_PASSWORD`：keystore 密码。
- `HID_KEY_ALIAS`：别名，默认 `hid-release`。
- `HID_KEY_PASSWORD`：私钥密码，未提供时使用 keystore 密码。

GitHub Actions 支持同名 repository secrets，另用 `HID_KEYSTORE_BASE64` 存放 keystore 的 Base64 内容。未设置 secrets 时自动构建 debug 和 unsigned release；PR 构建不读取签名 secrets。普通 CI debug 签名不能作为稳定的 release 更新签名。

## 来源

原版键盘布局与功能由 [Sucareto](https://github.com/Sucareto/Android_HID_Keyboard) 提供；USB 功能创建参考 [USB Gadget Tool](https://github.com/tejado/android-usb-gadget)；root 进程管理使用 [topjohnwu/libsu](https://github.com/topjohnwu/libsu)。保留原仓库历史和原始截图。
