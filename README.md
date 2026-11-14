# 精听

本地英语精听与复习工具，提供单文件网页版和安卓 APK。支持音频/SRT/VTT 导入、逐句跟读、逐词高亮、中英切换、倍速、定时暂停，以及欧路词典查词。

**当前版本：1.18-test（安卓 versionCode 9，测试版）**

- [下载安卓 APK](https://github.com/3363289624-prog/jingting/raw/refs/heads/main/downloads/1.18-test/jingting-1.18-test.apk)
- [本版下载与文件校验](downloads/1.18-test/README.md)
- [打开网页版](https://3363289624-prog.github.io/jingting/)
- [完整更新与验证说明](docs/release-1.18.md)

## 1.18 新增

| 功能 | 使用方式 |
| --- | --- |
| 整份字幕提前/延后 | 材料更多操作或“目录 → 整份字幕校准”，让切句、跳句和跟读范围一起对齐 |
| 收藏连续复习 | 收藏列表或目录中进入，支持顺序、随机及标记“已掌握” |
| 盲听模式 | 播放目录中开启，每句默认隐藏，点击揭晓并重听 |
| 导入配对确认 | 导入前预览并纠正音频/字幕配对，同名覆盖需要明确选择 |
| 刷新率与后台 | 安卓默认跟随系统，可选请求 120Hz；加强锁屏播放、耳机断开和音频打断处理 |

安卓包名仅为 `app.jtpractice.offlineplayer`；应用名称仍为“精听练习·诊断”。先导出备份，再覆盖安装同包名 APK，避免卸载或清除数据。

本版已完成 150 项浏览器集成、28 项逐词核心和 73 项一加真机检查。未做真实耗电对照、实体蓝牙断连和长时间后台耐久测试；具体范围见[验证记录](docs/verification-1.18.json)。

## 网页运行

在仓库根目录运行 `python -m http.server 8080`，打开 `http://localhost:8080/`。请使用 HTTP/HTTPS；直接打开本地 HTML 可能无法持久保存数据。`精听-手机网页包.zip` 同步为本版网页，包含现有样本和安卓启动说明。

材料存放在本机 IndexedDB，不同浏览器、设备、网址和 APK 不会自动同步。翻译仅在用户主动操作时调用自行配置的接口。句级 SRT 的逐词高亮基于时间估算；浏览器后台定时可能因系统挂起而延迟。安卓原生后台能力不等同于浏览器后台能力。

## 安卓源码

`android/` 包含包装器、同版网页和测试代码。构建需要 Python、JDK 17、Android API 35 / build-tools 35；最低 Android 7.0（API 24）。详见[构建说明](android/README-安卓.txt)和[测试说明](android/tests/README.md)。

签名密钥需自行保管和提供，仓库不包含密钥或密码。根目录 `index.html` 与 `android/assets/index.html` 保持完全一致；网页无前端依赖或构建步骤。`samples/` 保留测试字幕，不含音频。
