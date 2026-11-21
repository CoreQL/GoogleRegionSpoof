# 谷歌区域伪装（GoogleRegionSpoof）

一个 LSPosed 模块，把**谷歌应用读到的客户端区域信号**伪装成选定的目标地区：
SIM/网络国家码、运营商（MCC+MNC 与名称）、语言（Locale）、时区、Resources 配置的 locale、
`gsm.*` 系统属性，以及可选的 GPS 坐标。

> **边界先说清**：本模块只改应用在本地读到的区域信号，**不改变 IP 出口**。
> 很多谷歌服务是「IP + 客户端信号」一起看的，请配合代理（IP 出口与所选区域一致）使用，
> 否则信号互相矛盾，效果会打折。

## v1.1 新界面（仿 GlobalKit 的卡片式交互）

- **主页**（整页可滚动，卡片式）：
  - 顶部状态卡：模块名 + 当前状态；
  - **当前地区卡**：国旗 + 国家名，点「更改」打开**可搜索**的国家选择列表（14 个国家）；
  - **运营商卡片（二级联动）**：横向卡片列出所选国家的 2–3 家运营商（名称 + MCCMNC），
    点选即生效；**切换国家时运营商自动重置为该国第一家**；
  - **GPS 城市行 + GPS 伪装开关**（默认关）：城市默认首都，可换同国其他主要城市；
  - **当前伪装值**可展开面板：SIM/网络国家码、运营商、语言/地区、时区、GPS 位置一览；
  - 底部说明文字与「设置」入口。
- **设置页**：外观三档（跟随系统 / 浅色 / 深色）+ 主题颜色（橙/蓝/绿/紫/红，默认橙），
  选完立即作用于模块自己的界面，重启保持。

## 国家与运营商（14 国，每国 2–3 家真实运营商）

| 国家 | 运营商（MCC+MNC） | 语言 | 时区 |
|---|---|---|---|
| 美国 us | T-Mobile 310260 / AT&T 310410 / Verizon 311480 | en-US | America/New_York |
| 日本 jp | NTT docomo 44010 / SoftBank 44020 / au 44007 | ja-JP | Asia/Tokyo |
| 新加坡 sg | Singtel 52501 / StarHub 52505 / M1 52503 | en-SG | Asia/Singapore |
| 香港 hk | CMHK 45400 / CSL 45419 / SmarTone 45406 | zh-HK | Asia/Hong_Kong |
| 台湾 tw | 中华电信 46692 / 台湾大哥大 46697 / 远传电信 46601 | zh-TW | Asia/Taipei |
| 英国 gb | EE 23430 / O2 23410 / Vodafone 23415 | en-GB | Europe/London |
| 德国 de | Telekom.de 26201 / O2 26207 / Vodafone 26202 | de-DE | Europe/Berlin |
| 韩国 kr | SK Telecom 45005 / KT 45008 / LG U+ 45006 | ko-KR | Asia/Seoul |
| 泰国 th | AIS 52001 / TrueMove H 52004 / dtac 52005 | th-TH | Asia/Bangkok |
| 澳大利亚 au | Telstra 50501 / Optus 50502 / Vodafone 50503 | en-AU | Australia/Sydney |
| 马来西亚 my | Maxis 50212 / Celcom 50219 / Digi 50216 | ms-MY | Asia/Kuala_Lumpur |
| 菲律宾 ph | Globe 51502 / Smart 51503 | en-PH | Asia/Manila |
| 越南 vn | Viettel 45204 / Vinaphone 45202 / Mobifone 45201 | vi-VN | Asia/Ho_Chi_Minh |
| 巴西 br | Vivo 72410 / Claro 72405 / TIM 72402 | pt-BR | America/Sao_Paulo |

默认：美国 / T-Mobile。配置分开存（国家、运营商、GPS 城市、GPS 开关），
v1.0 的旧配置（preset_id）首次打开会自动迁移。

## 实际 hook 的方法

每个 hook 独立 try/catch，失败静默放行原结果，不会让目标应用崩溃：

- `TelephonyManager.getSimCountryIso / getNetworkCountryIso`（含带 int 参数的隐藏重载）
- `TelephonyManager.getSimOperator / getNetworkOperator`
- `TelephonyManager.getSimOperatorName / getNetworkOperatorName`
- `Locale.getDefault()`（含 `Category` 重载）
- `TimeZone.getDefault()`
- `Resources.getConfiguration()`（返回改过 locale 的配置副本，不动应用原对象）
- `SystemProperties.get(...)`：仅拦截 `gsm.sim.operator.iso-country`、`gsm.operator.iso-country`、
  `gsm.sim.operator.numeric`、`gsm.operator.numeric`、`gsm.sim.operator.alpha`、`gsm.operator.alpha`
- **GPS（仅开关打开时才装钩）**：`Location.getLatitude / getLongitude` 返回所选城市坐标
  （按「包名+城市」哈希加一个稳定小偏移，约 ±130 米，避免坐标正落在市中心点上）；
  `LocationManager.getLastKnownLocation` 返回按假坐标构造的 Location。

配置读取失败（文件不可读等）时自动回退默认（美国 / T-Mobile），不会不伪装、更不会崩溃。

## 默认作用域（LSPosed 中默认勾选）

- `com.google.android.gms`（Google Play 服务）
- `com.android.vending`（Play 商店）
- `com.google.android.googlequicksearchbox`（Google 应用）
- `com.google.android.apps.bard`（Gemini）
- `com.google.android.youtube`（YouTube）

## 使用方法

1. 安装 APK，在 LSPosed 中启用本模块，确认作用域勾选了上面的谷歌应用；
2. 打开「谷歌区域伪装」，先选国家、再选运营商（点选即保存）；
3. **强停**对应的谷歌应用后重新打开才生效；**Play 商店还需到系统设置里清一次它的缓存**，
   否则商店首页地区可能不变；
4. IP 出口请用代理切到同一地区，与客户端信号保持一致；
5. GPS 伪装默认关。**风险提示**：在 GMS 这类系统级应用里伪装 GPS，可能让依赖定位的
   谷歌服务（时间线、查找设备、基于位置的提醒等）行为异常；不需要时请保持关闭。

## 已知限制（实事求是）

- **IP 不归本模块管**：只伪装客户端信号，IP 仍是真实出口，务必配合代理。
- **Play 商店换区不只看客户端信号**：还受谷歌账号的国家/地区设置与支付资料（付款方式所属国家）
  影响；账号地区一年只能改一次这类平台规则，模块改不了。
- 部分谷歌服务会结合账号历史、设备型号等多维信号判断地区，本模块覆盖的是最常见的
  SIM/运营商/语言/时区这一组，不能保证所有区域功能都解锁。
- 切换配置后需强停目标应用（配置在目标进程启动时读取），做不到热切换。
- GPS 伪装只覆盖 `Location` 读数与 `getLastKnownLocation`；走网络定位/Fused 内部通道的
  读取不一定经过这两个入口，效果因应用而异。
- 配置文件依赖 LSPosed 的跨进程读取（XSharedPreferences）；个别 ROM 上若读不到配置，
  会回退成默认美国，而不是报错。

## 构建

```sh
source ~/workspace/android-tools/env.sh   # JDK 17 + Android SDK 35（或等价环境）
./build.sh                                 # 手动工具链：aapt2 → javac → d8 → zipalign → apksigner
```

- 框架：经典 Xposed API 82（`libs/api-82.jar` 为官方 API 存根，仅编译期使用，不打包进 APK）；
- 最低 Android 8.0（API 26）；当前版本 v1.1.1（versionCode 3）。

## 借鉴与致谢

本项目的实现思路学习自以下两个开源项目（仅学习思路，本仓库代码为独立编写）：

- [Ackites/Nrfr](https://github.com/Ackites/Nrfr)：免 Root 的 SIM 国家码修改工具，
  用 Shizuku 调用系统 `ICarrierConfigLoader` 覆写运营商配置。本项目借鉴了它的
  「国家 → 运营商」预设组织方式。详见 [docs/学习笔记.md](docs/学习笔记.md)。
- [GlobalKit（com.tiktok.unlocker）](https://github.com/Xposed-Modules-Repo/com.tiktok.unlocker)：
  面向 TikTok 的 LSPosed 区域伪装模块（基于 libxposed Modern API 102）。
  本项目仿照它的信号面（SIM/网络国家、运营商、语言、时区、GPS）与卡片式设置界面
  （国家/运营商二级联动、可搜索国家列表、外观与主题色设置），目标应用换成谷歌全家桶，
  框架层采用等价的经典 Xposed API 82 + XSharedPreferences。

## 免责声明

仅供学习、研究与个人测试使用。因使用本模块导致的应用异常、账号限制或其他损失，由使用者自行承担；
使用时请遵守所在地法律法规及相关服务条款。
