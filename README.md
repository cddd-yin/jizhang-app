# 蓝鲸记账 (jizhang-app)

一款简洁流畅的 Android 记账 App，Kotlin + Jetpack Compose 原生开发。
**核心目标：花完钱自动记入账单，不用手动折腾。**

## 功能

- **实时自动记账**：通过系统「通知使用权」监听支付宝、微信、QQ 的支付/收款通知，
  自动解析金额并分类入账（本地完成，不需要连接电脑，不上传任何数据）。
- **多渠道解析**：覆盖微信（含付款码）、支付宝、QQ 钱包、云闪付、京东/淘宝收银台，
  以及主流银行 App 的消费/动账通知。
- **防误记**：自动过滤公众号营销、优惠券、信贷（花呗/白条）、验证码等「见元就记」的噪声。
- **聚合通知补全**：系统把通知折叠成「[2条]」时，自动从同组子通知补全金额（付款码、银行动账等）。
- **智能去重**：同金额 + 同收支 + 同商户在短时间窗内自动合并，避免微信+银行双通知记两笔；
  同渠道无商户的同额消费不会误并。
- **模糊账单确认**：金额不完整或识别不确定时，弹窗确认（可改金额/收支后再入账），不误记。
- **监听保活**：前台服务 + 定时看门狗 + 开机自启 + 断开自动重连，
  国产机杀后台后会自动恢复，App 内横幅显示「正在自动记录 / 已断开 / 需授权」。
- **手动记账**：右下角「记一笔」，金额 / 收支 / 分类 / 备注一步搞定，点任意记录可编辑、删除。
- **账单导入**：支持导入支付宝 / 微信官方导出的账单 CSV（自动去重，GBK 编码兼容）。
  - 支付宝：我的 → 账单 → 开具交易流水 → 邮箱接收 zip
  - 微信：我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账 → 邮箱接收 zip
- **CSV 导出 / 备份**：一键导出本月或全部账单为 CSV（带 BOM，Excel 直接打开不乱码），
  写入公共「下载/Jizhang」目录，可分享到微信/QQ/邮箱等；导出的文件可再被本 App 导入。
- **账单搜索**：明细页顶部搜索框，按备注 / 分类 / 来源即时过滤，边输边筛。
- **权限引导页**：右上角 ⚙ 进入「自动记账设置」，一站式引导开通通知使用权、
  发送通知（Android 13+ 首次进入自动申请）、自启动、忽略电池优化，保证「花完钱自动记账」不被后台杀掉。
- **高级感首页（账本）**：本月结余天蓝渐变大卡（金额自动缩放）+ 支出/收入双卡 +
  月度预算进度环（可一键设置/编辑预算，超支变红）+ 最近交易预览。
- **高级感界面（v2.4.0 ZCode 风格）**：设计 Token 提取自智谱 ZCode 桌面应用（app.asar 内 Tailwind CSS）——
  品牌天蓝（sky-400/sky-500）+ 中性灰阶表面分层（页面/面板/卡片，细描边）+ 收入绿/支出红语义色、
  ZCode usage-chart 天蓝色板图表；去毛玻璃改实底紧凑顶栏与底部 Tab Bar，选中项天蓝高亮；
  保留 Lato 数字 + Sans 正文字体（OFL）。
- **月度统计**：按月切换，支出/收入/结余总览（结余按收支着色）+ 近 6 个月支出柱状图 +
  收入分类占比饼图 + 本月支出分类条形图。
- **数据本地化**：Room 数据库存于手机本地，无任何网络权限。

## 使用步骤（首次安装后）

1. 打开 App，首次进入自动申请通知权限（Android 13+）；
2. 点击右上角 ⚙ 进入「自动记账设置」，按引导依次开启：**通知使用权（必须）** →
   **发送通知（推荐）** → **自启动（国产机建议）** → **忽略电池优化**；
3. 之后在支付宝/微信/QQ 每完成一笔支付，即自动生成一条记录，无需任何操作。

> 注意：
> - **升级 App 后**系统会撤销通知使用权，打开 App 会提示重新开启（Android 系统限制）。
> - **国产机（小米/华为/OPPO/vivo）建议**：允许 App「自启动」、关闭电池优化、在最近任务中锁定 App，
>   以保证监听不被后台杀掉。App 内前台通知会常驻显示「正在自动记录」。

## 构建

用 Android Studio（Ladybug 或更新版）打开本目录，Gradle 同步后直接 Run；
或命令行：

```bash
./gradlew assembleDebug    # 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17+（JDK 21 亦可），Android SDK 35。
中文路径构建需在 `gradle.properties` 保留 `android.overridePathCheck=true`。

## 技术栈

- Kotlin 2.0 + Jetpack Compose (Material 3)
- Room（本地持久化）+ KSP
- NotificationListenerService（实时监听支付通知）
- 无第三方 SDK、无网络权限，隐私安全

## 目录结构

```
app/src/main/java/com/yang/jizhang/
├── JizhangApp.kt          # Application，数据库单例
├── CategoryRules.kt       # 分类关键词与图标规则
├── data/                  # Room 实体 / DAO / 数据库
├── notify/                # 通知监听 + 金额解析 + 去重
│   ├── PaymentListener.kt     # NotificationListenerService 核心
│   ├── NotificationParser.kt  # 全渠道支付通知解析（金额/收支/商户/防营销）
│   ├── DedupHelper.kt         # 智能去重
│   ├── PendingConfirmStore.kt # 待确认账单暂存
│   └── keepalive/             # 前台保活 / 状态 / 看门狗 / 通知样式
├── importer/              # 支付宝/微信 CSV 导入
├── util/                  # CSV 导出 / MediaStore 备份（抄 SmartLedger）
└── ui/                    # Compose 界面（首页 / 明细 / 统计 / 图表 / 权限引导 / 弹窗 / 主题 / Pg 组件）
```

解析方案借鉴开源项目 [SmartLedger](https://github.com/huanghhcri/SmartLedger)
（MIT License），按本项目数据模型重写，参考源码见 `docs/references/`。
v2.3.0 的高级感界面（双层背景配色、Pg 组件、金额/进度环样式）借鉴
[wallee (Compose-Expense)](https://github.com/wisnukurniawan/Compose-Expense)（Apache-2.0）
与 [Expenso](https://github.com/darkvortex144/Expenso)（进度环），字体 Lato 为 OFL 许可。
v2.4.0 界面全面改为 **ZCode 设计语言**（天蓝品牌 + 中性灰阶 + 细描边 + 紧凑工具风布局），
设计 Token 从 ZCode 桌面应用 `%LOCALAPPDATA%\Programs\ZCode\resources\app.asar`
内的 Tailwind CSS（`--color-*` 变量与 `.dark` 覆盖）实测提取，色值取 oklch → sRGB 转换。
