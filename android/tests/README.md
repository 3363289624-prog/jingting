# 本地检查

生产网页只有 `../assets/index.html`。测试脚本、测试页面和测试音频均不进入 APK。

## 浏览器回归

需要本机 Python、Node.js 和支持 IndexedDB/音频的浏览器，无 npm 依赖：

```powershell
node tests/word-core-tests.cjs assets/index.html work/word-core-results.json
python tests/run_browser_checks.py --output-dir work/checks
python tests/serve_checks.py --root work/checks --port 8778
```

打开 `http://127.0.0.1:8778/integration.html` 并点击开始。页面将检查五项新功能及原有逐词、跟读、界面和数据生命周期，并将 JSON 结果保存到 `work/checks/browser-report.json`。

**必须使用独立、可丢弃的 localhost 端口。** 检查会建立和清除该测试来源的材料库，不可在正式 App 来源、已有用户材料的网页来源或生产 `assets` 目录中运行。

## 安卓真机验证

`android/` 提供本轮使用的 Instrumentation 类、焦点抢占 Activity 与耗电检查主体。测试 APK 必须单独构建，与目标 App 同签名；测试组件和测试资产不进入正式 APK。Instrumentation 在内存材料库中运行固定样本，并在结束时比较原材料元数据与音频大小，确认没有写入用户材料库。

真实耳机断开接收器检查由测试代码调用原生接收器路径，不等同于所有蓝牙设备断连实测。另一个测试 Activity 实际申请系统音频焦点并播放静音样本，以验证其他应用抢占后的暂停和待播取消。

耗电脚本要求手机解锁、精听位于前台且 USB 已拔出。使用 60 秒预热及 ABBA 四段、每段 300 秒的对照，App 窗口亮度临时固定为 35%，记录电量计数器、电流、温度与实际刷新率。插入充电线、切后台、锁屏或音频中断时会停止并标记无效，不能把无效样本当作省电结论。

合成 WAV 在运行时生成；源码和 APK 不包含音频样本、测试 APK、密钥或密码。构建签名仍需自行提供原密钥。
