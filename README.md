# newBlackDex

Android DEX 脱壳工具。基于 [BlackDex](https://github.com/CodingGay/BlackDex) 的复活/移植分支，向上兼容到 Android 14 以上（主要在 Android 16 / HyperOS 上测试），并修复了原项目在高版本上无法运行的一系列问题。

> 原项目作者：CodingGay。本仓库仅做向上兼容和 Bug 修复，不包含任何针对各加固厂商的过检测逻辑。

---

## 软件用途

BlackDex 用于把 Android 应用运行时加载到内存中的 DEX 文件 dump 出来，主要用于：

- 学习、分析加了壳（加固）的应用的 DEX 结构
- 对抗「类抽取」「函数抽取」等壳，尽量还原出可被反编译的 DEX
- 安全研究 / 逆向学习

**请勿用于破解、盗版或任何侵权用途。** 仅限对自有应用或已获授权的应用进行分析。

---

## 支持范围与限制

- **系统版本**：仅向上兼容到 Android 14+，实测机型为 Android 16（HyperOS 3）。更低版本不保证。
- **架构**：arm64-v8a（默认 BlackDex64）、armeabi-v7a（BlackDex32）
- **可用脱壳方式**：
  - ✅ Cookie 模式内存 dump（主路径，已修复）
  - ✅ Hook 脱壳（已修复，Android 16 改 hook `LoadClass`）
  - ✅ 主动调用脱壳（cookie 模式下，用于对抗函数抽取壳）
  - ❌ 深度脱壳（fixCodeItem）**未修复**：Android 13 起 `ArtMethod` 删除了 `dex_code_item_offset_`，原偏移计算逻辑失效，本仓库未修复，用「主动调用」代替。
- **不做**：不包含任何针对加固厂商的过检测、反反调试等行为，能否脱出看壳的实现与运气。
- 可能出现：脱不出东西、进程卡死、目标 app 异常退出等。属正常现象。

---

## 脱壳逻辑

BlackDex 把目标 APK 安装到一个**内部沙箱**（虚拟化环境）里运行，在目标 app 运行时从内存中提取 DEX。整体流程：

```
App (Application)
  └─ AppManager
       └─ BlackDexLoader  (读取设置、ClientConfiguration)
            └─ BlackDexCore  (单例门面)
                 └─ BlackBoxCore  (真实引擎)
                      └─ dumpDex(pkg|file|uri)
                           ├─ installPackage  安装目标 APK 到沙箱
                           └─ launchApk       在代理进程里启动目标
```

### 进程模型

- **主进程** `top.niunaijun.blackdexa64`：UI 与调度，`WelcomeActivity -> MainActivity`。
- **`:black` 进程**：系统服务进程，承载 `DaemonService` 和 `SystemCallProvider`，是 IPC 与包管理的核心。启动时会把 `empty.jar`/`junit.jar`/`vm.jar` 从 assets 拷贝到 `virtual/cache/` 下作为运行时依赖。
- **`:p0`..`:p99` 代理进程**：目标 app 实际运行的进程。`BActivityThread` 是每个代理进程的入口，负责绑定目标 app 并触发脱壳。

### 启动一次脱壳的时序

1. 主进程调用 `BlackDexCore.dumpDex(...)`，把目标 APK 安装进沙箱。
2. `launchApk` 通过 `ProxyActivity$P0` 在 `:p0` 进程拉起目标 app。
3. `:p0` 里 `HCallbackProxy` 拦截 `EXECUTE_TRANSACTION`，在首次启动时调用 `BActivityThread.bindApplication`：
   - `VMCore.init(SDK)` 初始化 native（校准 ArtMethod 偏移、安装 JNI hook）
   - `IOCore.enableRedirect` 安装文件 IO 重定向（hook `UnixFileSystem` 的 native 方法，把目标 app 写死的路径重定向到沙箱目录）
   - `makeApplication` 构造目标 app 的 `Application`（此时目标 dex 已加载到内存）
   - 调度 `handleDumpDex`（独立线程，延迟 500ms 执行 cookie dump）
4. dump 完成后上报结果、卸载目标包、退出 `:p0`。

### 三种脱壳方式

#### 1. Cookie 模式内存 dump（主路径）

`VMCore.cookieDumpDex(ClassLoader, packageName)`：

- 通过 `DexFileCompat.getCookies(classLoader)` 反射拿到目标 ClassLoader 里所有 `DexFile` 的 `mCookie`（`long[]`，每个元素是一个 native `DexFile*` 指针）。
- 对每个 cookie，native 侧 `DexDump::cookieDumpDex` 读取 `DexFile` 对象里的 `begin_`（dex 内存起始地址）和 `size_`（dex 大小），把整段 dex 内存 `memcpy` 出来，修复 magic 后写文件。
- **`beginOffset` 运行时校准**：不同 Android 版本 `DexFile` 布局不同，不能硬编码偏移。`init()` 会加载一个已知大小（1872 字节）的 `empty.apk`，在它的 `DexFile` 对象里搜索这个大小值，定位到 `begin_` 字段的偏移，并校验该校准出的 `begin` 处确实是 `dex\n` magic，避免误匹配到 OatFile 等非 DexFile 的 cookie。
- 写出文件名：`cookie_<size>.dex`。

#### 2. Hook 脱壳

`VMCore.hookDumpDex(dir)`，在 `AppInstrumentation.newApplication` 里（开启「Hook Dump」时）调用：

- hook libart.so 里的类加载函数，每当加载一个类/方法时拿到 `DexFile*`，把对应 dex dump 出来。能抓到 cookie 里可能没有的、运行时动态加载的 dex。
- **Android 14/15**：hook `ClassLinker::LoadMethod`（按版本匹配多个 mangled 符号）。
- **Android 16+**：`LoadMethod` 不再从 libart.so 导出，改为 hook 仍导出的 `ClassLinker::LoadClass`（签名 `LoadClass(this, Thread*, const DexFile&, const dex::ClassDef&, Handle<mirror::Class>)`，DexFile 是第 3 个参数）。
- hook 回调里复用 `handleDumpByDexFile`，按 size 去重后写出 `hook_<size>.dex`。
- 同样使用运行时校准的 `beginOffset`，并做 size 合理性 + 整段可读性校验，避免布局偏移不对时 `memcpy` 越界崩溃。

#### 3. 主动调用脱壳（cookie 模式下，对抗函数抽取壳）

`VMCore.autoCallAllMethod`，在 cookie dump 前执行：

- 拿到目标 ClassLoader 的所有类名列表，逐个 `loadClass`，触发类初始化和方法加载。
- 对「函数抽取壳」（运行时才把被抽取的 code item 还原回内存），主动调用能让这些 code item 被还原，提升 dump 出的 dex 的完整度。
- 过滤掉 `com.luoye.dpt`（dpt 壳检测类）和 `top.niunaijun`（自身类），避免被检测或循环。

### native 注册与 VMCore 类

- `VMCore` 是 native 方法承载类。`libblackdex.so` 在 `JNI_OnLoad` 里通过 `RegisterNatives` 把 native 方法注册到 `VMCore`。
- 历史上 `vm.jar`（沙箱 classloader 用的 dex）里也放了一份 `VMCore`，会与宿主的 `VMCore` 形成两个不同的 `Class`，而 `RegisterNatives` 只会注册到先加载 lib 的那个，导致另一个调 native 时 `UnsatisfiedLinkError`。本仓库已把 `vm.jar` 改为占位 dex（不含 `VMCore`），全局只保留宿主一个 `VMCore` 类，cookie 和 hook 两种模式都走它。
- `hookDumpDex` 由 `private` 改为 `public`，`AppInstrumentation` 直接用宿主 `VMCore` 调用，不再反射目标 classloader 里的 `VMCore`。

### Android 16 关键兼容修复

- `UnixFileSystem.canonicalize0` 在 Android 16 改为非 native 方法，`RegisterNatives` 对非 native 方法会失败并抛 `NoSuchMethodError`。`JniHook::HookJniFun` 在 `RegisterNatives` 失败、`FindClass`、`GetArtMethod` 等所有路径上清除了挂起异常，避免下一次 `FindClass` 触发 `AssertNoPendingException` -> `SIGABRT`。
- `:p0` 进程在 `ProxyActivity` 装饰窗创建时，框架会调 `Settings.Global` 读取桌面模式标志，目标 app 包名与宿主 uid 不匹配会抛 `SecurityException` 杀进程（在 dump 线程跑之前）。新增 `sDumping` 标志，dump 期间 `HCallbackProxy` 手动派发事务并吞掉框架异常，保证 dump 线程跑完；dump 完成后主动 `Process.killProcess` 退出 `:p0`。
- `ClassLinker::LoadMethod` 不再导出 -> 改 hook `LoadClass`。
- `vm.jar` 缓存不更新 -> `initJarEnv` 拷贝前先 `setWritable(true)`。

---

## 设置项说明

| 设置 | 作用 |
|---|---|
| Use default storage path / Customize | dump 输出目录。默认 `Download/dexDump`（Android R+），否则 `<externalCacheDir>/../dump` |
| Hook Dump | 开启 Hook 脱壳（hook `LoadClass`/`LoadMethod`），输出 `hook_*.dex`，提高成功率 |
| Deep Unpacking（深度脱壳） | 修复被抽取的 DexCode（**Android 13+ 已失效，未修复**），开启会明显变慢且可能失败 |
| Call Method（主动调用） | 运行时主动调用目标所有类，对抗函数抽取壳（仅 cookie 模式生效） |
| Verify Dex Before Dump | dump 前校验 dex magic。部分加固会把内存中 dex magic 清零对抗脱壳，此时可关闭此项以 dump 出 magic 被破坏的 dex（写出时仍会回填 `dex\n035` magic） |

---

## 输出位置

- Android R（11）+：`/storage/emulated/0/Download/dexDump/<目标包名>/`
- 更低版本：`<externalCacheDir>/../dump/<目标包名>/`
- 可在设置里自定义。
- 文件命名：`cookie_<size>.dex`（cookie 模式）、`hook_<size>.dex`（hook 模式）。同一 size 只写一次（去重）。

---

## 构建与安装

### 工具链（已锁定，勿随意升级）

- Gradle 6.7.1、AGP 4.2.0、Kotlin 1.5.0、**JDK 8~14**（JDK 17+ 不支持，Gradle 6.7.1 会报 `Unsupported class file major version 61`，实测 JDK 11 可用）
- NDK 21、CMake 3.10
- Maven 仓库使用**阿里云镜像 + jitpack**（见根 `build.gradle`），不要换成 `google()`/`mavenCentral()` 默认源，否则依赖解析会失败
- 首次原生构建较慢：Dobby 从 `Bcore/src/main/cpp/Dobby` 源码编译
- `local.properties`（SDK 路径）是机器本地的，不要提交
- Windows 主机用 `gradlew.bat`

### 构建命令

```bash
# arm64-v8a（默认）
gradlew.bat assembleBlackDex64Debug
gradlew.bat assembleBlackDex64Release

# armeabi-v7a
gradlew.bat assembleBlackDex32Debug
gradlew.bat assembleBlackDex32Release

# 安装到设备
gradlew.bat installBlackDex64Debug
```

JDK 17+ 环境下需先切到 JDK 11：

```powershell
$env:JAVA_HOME="D:\JDK\jdk11"; .\gradlew.bat assembleBlackDex64Release
```

### 模块结构（`settings.gradle`）

- `:app` — Android 应用，Kotlin，包名 `top.niunaijun.blackdex`。UI 与调度。入口 `WelcomeActivity -> MainActivity`。
- `:Bcore` — 核心引擎，Java + C++/NDK，包名 `top.niunaijun.blackbox`。脱壳逻辑、native 库 `blackdex`（CMake 构建，内含 Dobby）。
  - `:Bcore:black-hook` — JNI hook 原语，包名 `top.niunaijun.jnihook`
  - `:Bcore:black-fake` — fake framework，包名 `top.niunaijun.black_fake`
- 依赖链：`app -> Bcore -> {black-hook, black-fake}`，`black-fake -> black-hook`

### 签名

如需自签名 release 包，在 `app/` 下放 `keystore.properties`（已 gitignore）：

```properties
storeFile=../your.jks
storePassword=xxxx
keyAlias=xxxx
keyPassword=xxxx
```

`app/build.gradle` 已配置 `signingConfigs.release` 从该文件读取。

---

## 使用方法

1. 安装 BlackDex。
2. （Android 11+）授予「所有文件访问权限」。
3. 在主页列表里选中要脱壳的应用，或在右下角 FAB 选择本地 APK 文件。
4. 等待脱壳完成（弹窗提示成功/失败，及 dex 保存路径）。
5. 到输出目录查看 `cookie_*.dex` / `hook_*.dex`，用 jadx 等工具反编译。

> 遇到脱不出的壳，可尝试组合：开启「Hook Dump」+「主动调用」；若怀疑加固把 dex magic 清零，再关闭「Verify Dex Before Dump」。

---

## 已知问题 / 不做修复

- 深度脱壳（fixCodeItem）在 Android 13+ 失效，未修复，用「主动调用」代替。
- 不做任何过检测、反反调试。
- 对加固强度较高 / 有环境检测的 app，可能直接失败或目标 app 崩溃。
- 没有真实测试套件（仅占位 `ExampleUnitTest`），验证方式 = 能构建、能安装、能在真机上 dump 出 dex。

---

## 致谢

- 原项目 [BlackDex](https://github.com/CodingGay/BlackDex) 及其作者 CodingGay
- [Dobby](https://github.com/jmpews/Dobby)（内嵌于 `Bcore/src/main/cpp/Dobby`，从源码编译）
- [free_reflection](https://github.com/tiann/FreeReflection)、xhook 等

## 问题反馈

使用问题或 Bug 欢迎提 Issue。请附上 logcat（过滤 `VmCore` 标签）和设备信息（机型、Android 版本、目标 app 及加固类型）。
