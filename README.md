# Free My TV — Android 8 中文精简版

基于 [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv) 的 Android 专用定制，保留上游 MIT 许可证。
上游基础提交：`94a55c097c8d199efa6a0fbac298fb02acaa91bf`。

## 功能

- 最低 Android 8.0（API 26），支持 ARMv7 / armeabi-v7a；无原生库，为通用架构 APK。
- 简体中文界面，支持自选本地壁纸、电视内部存储和 U 盘图片。
- 壁纸后台缩小、按 EXIF 旋转并居中裁剪，保存到应用内部；导入后可以移除 U 盘。
- 保留桌面、全部应用、常用应用选择和长按排序、原厂桌面恢复及崩溃看门狗。
- 移除应用屏保、天气与城市、行情、遥控器专用按键映射和直播 / HDMI 快捷入口。
- 影视推荐可选，需自行填写 TMDB 密钥；未填写时显示时钟桌面。

## 当前版本：1.4

设置 → 壁纸遮罩：支持 0%–100% 不透明度，遥控器左右键每次调整 1%，保存后返回桌面生效。浅色白色遮罩与深色黑色遮罩共用该值，默认 15%，可一键恢复。取消或返回不保存修改。只调整现有遮罩透明度，不增加图片解码或绘制层。

自定义壁纸支持浅色、深色和自动主题，文字与遮罩随主题变化；在设置中选择“桌面主题 → 浅色”即可使用浅色壁纸效果。移除设置中的“返回桌面”按钮，使用遥控器返回键退出设置，避免该按钮重新启动桌面。

### 1.2 性能优化记录

设置页启动背景已统一为深色。优化了焦点动画的重复绘制、静止抽屉的帧回调、设置变更后的桌面重建以及壁纸重新解码。

一台海信 Android 8 / ARMv7 / 1080p 电视的短时前后对比：

| 指标 | 1.1.1 | 1.2 |
|---|---:|---:|
| 12 轮预热后焦点操作慢帧率 | 0% | 0% |
| P90 帧耗时 | 8 ms | 7 ms |
| 静止抽屉 CPU（top 采样） | 约 3% | 0.0% |

1.2 在导航测试后合计 PSS 约 38.6 MiB，打开设置返回后约 44.7 MiB。数据会随缓存、设备和操作变化，并非长期性能或无泄漏保证。

## 使用壁纸

右上角设置 → 选择本地壁纸 → 使用系统图片选择器 / 浏览电视存储或 U 盘。
支持 JPG、PNG、WebP；Android 8 首次浏览存储时需授予读取权限。
“恢复默认背景”可移除本地壁纸。

## 构建

需要 JDK 17+、Android SDK build-tools 34.0.0 和 platform android-34。
设置 `JAVA_HOME`、`ANDROID_HOME` 后执行：

```sh
chmod +x build.sh
./build.sh
```

首次会从 `config.example.properties` 创建 `config.properties`，随后生成 `FreeMyTV.apk`。
密钥留在本地配置中，不要提交 `config.properties` 或 `freemytv.keystore`。
该目录不包含个人签名密钥；自己的构建会生成新签名，无法覆盖安装其他签名的 APK。

## 测试

```sh
mkdir -p out/tests
javac -encoding UTF-8 -cp "out/classes:$ANDROID_HOME/platforms/android-34/android.jar" -d out/tests tests/LiteTest.java
java -cp "out/tests:out/classes:$ANDROID_HOME/platforms/android-34/android.jar" io.freemytv.LiteTest
```

## 安装

官方发布包从 1.3 起使用新签名：1.3 / 1.3.1 可直接覆盖升级到 1.4，保留设置和壁纸。1.2 的签名密钥已丢失，升级需先卸载旧版，会清除应用设置、排序和内部壁纸，请提前保存原始壁纸图片。

可通过电视文件管理器安装 APK，或使用 ADB：

```sh
adb connect TV_IP:5555
adb install -r FreeMyTV.apk
```

首次切换桌面可能需要允许电视自身的 ADB 调试。切换会改变默认桌面并停用上游列出的部分应用；可从“原厂桌面”恢复。
本版不提供应用屏保，电视原生屏保由系统设置管理。

## 来源与许可证

本项目是独立 Android 定制版，不代表上游官方发行。第三方字体与图标许可证保留在各自资源目录。
原项目同时支持 webOS/Tizen；此仓库只包含 Android 版本。
