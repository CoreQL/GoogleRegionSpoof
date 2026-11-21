# 谷歌区域伪装（GoogleRegionSpoof）

一个 LSPosed 模块，把**谷歌应用读到的客户端区域信号**伪装成选定的目标地区：
SIM/网络国家码、运营商（MCC+MNC 与名称）、语言（Locale）、时区、Resources 配置的 locale、
以及 `gsm.*` 系统属性。

> **边界先说清**：本模块只改应用在本地读到的区域信号，**不改变 IP 出口**。
> 很多谷歌服务是「IP + 客户端信号」一起看的，请配合代理（IP 出口与所选区域一致）使用，
> 否则信号互相矛盾，效果会打折。

## 功能

- 设置界面点选区域预设即保存，内置 7 个预设（默认美国）：

| 预设 | 国家码 | 运营商（MCC+MNC） | 语言 | 时区 |
|---|---|---|---|---|
| 美国 | us | T-Mobile（310260） | en-US | America/New_York |
| 日本 | jp | NTT docomo（44010） | ja-JP | Asia/Tokyo |
| 新加坡 | sg | Singtel（52501） | en-SG | Asia/Singapore |
| 香港 | hk | CMHK（45400） | zh-HK | Asia/Hong_Kong |
| 台湾 | tw | Chunghwa Telecom（46692） | zh-TW | Asia/Taipei |
| 英国 | gb | EE（23430） | en-GB | Europe/London |
| 德国 | de | Telekom.de（26201） | de-DE | Europe/Berlin |

- 实际 hook 的方法（每个 hook 独立 try/catch，失败静默放行原结果，不会让目标应用崩溃）：
  - `TelephonyManager.getSimCountryIso / getNetworkCountryIso`（含带 int 参数的隐藏重载）
  - `TelephonyManager.getSimOperator / getNetworkOperator`
  - `TelephonyManager.getSimOperatorName / getNetworkOperatorName`
  - `Locale.getDefault()`（含 `Category` 重载）
  - `TimeZone.getDefault()`
  - `Resources.getConfiguration()`（返回改过 locale 的配置副本，不动应用原对象）
  - `SystemProperties.get(...)`：仅拦截 `gsm.sim.operator.iso-country`、`gsm.operator.iso-country`、
    `gsm.sim.operator.numeric`、`gsm.operator.numeric`、`gsm.sim.operator.alpha`、`gsm.operator.alpha`
- 配置读取失败（文件不可读等）时自动回退默认预设（美国），不会不伪装、更不会崩溃。

## 默认作用域（LSPosed 中默认勾选）

- `com.google.android.gms`（Google Play 服务）
- `com.android.vending`（Play 商店）
- `com.google.android.googlequicksearchbox`（Google 应用）
- `com.google.android.apps.bard`（Gemini）
- `com.google.android.youtube`（YouTube）

## 使用方法

1. 安装 APK，在 LSPosed 中启用本模块，确认作用域勾选了上面的谷歌应用；
2. 打开「谷歌区域伪装」，点选目标区域（点选即保存）；
3. **强停**对应的谷歌应用后重新打开才生效；**Play 商店还需到系统设置里清一次它的缓存**，
   否则商店首页地区可能不变；
4. IP 出口请用代理切到同一地区，与客户端信号保持一致。

## 已知限制（实事求是）

- **IP 不归本模块管**：只伪装客户端信号，IP 仍是真实出口，务必配合代理。
- **Play 商店换区不只看客户端信号**：还受谷歌账号的国家/地区设置与支付资料（付款方式所属国家）
  影响；账号地区一年只能改一次这类平台规则，模块改不了。
- 部分谷歌服务会结合账号历史、设备型号等多维信号判断地区，本模块覆盖的是最常见的
  SIM/运营商/语言/时区这一组，不能保证所有区域功能都解锁。
- 切换区域后需强停目标应用（配置在目标进程启动时读取），做不到热切换。
- 配置文件依赖 LSPosed 的跨进程读取（XSharedPreferences）；个别 ROM 上若读不到配置，
  会回退成默认美国，而不是报错。

## 构建

```sh
source ~/workspace/android-tools/env.sh   # JDK 17 + Android SDK 35（或等价环境）
./build.sh                                 # 手动工具链：aapt2 → javac → d8 → zipalign → apksigner
```

- 框架：经典 Xposed API 82（`libs/api-82.jar` 为官方 API 存根，仅编译期使用，不打包进 APK）；
- 最低 Android 8.0（API 26）；签名见各 Release 说明（测试钥签名，覆盖安装需保持同一签名）。

## 借鉴与致谢

本项目的实现思路学习自以下两个开源项目（仅学习思路，本仓库代码为独立编写）：

- [Ackites/Nrfr](https://github.com/Ackites/Nrfr)：免 Root 的 SIM 国家码修改工具，
  用 Shizuku 调用系统 `ICarrierConfigLoader` 覆写运营商配置。本项目借鉴了它的
  「国家 → 运营商」预设组织方式。详见 [docs/学习笔记.md](docs/学习笔记.md)。
- [GlobalKit（com.tiktok.unlocker）](https://github.com/Xposed-Modules-Repo/com.tiktok.unlocker)：
  面向 TikTok 的 LSPosed 区域伪装模块（基于 libxposed Modern API 102）。
  本项目仿照它的信号面（SIM/网络国家、运营商、语言、时区）与「设置界面选地区」的交互，
  目标应用换成谷歌全家桶，框架层采用等价的经典 Xposed API 82 + XSharedPreferences。

## 免责声明

仅供学习、研究与个人测试使用。因使用本模块导致的应用异常、账号限制或其他损失，由使用者自行承担；
使用时请遵守所在地法律法规及相关服务条款。
