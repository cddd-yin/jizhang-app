# 轻记账 (jizhang-app)

一款简洁流畅的 Android 记账 App，Kotlin + Jetpack Compose 原生开发。

## 功能

- **实时自动记账**：通过系统「通知使用权」监听支付宝、微信、QQ 的支付/收款通知，
  自动解析金额并分类入账（本地完成，不需要连接电脑，不上传任何数据）。
- **手动记账**：右下角「记一笔」，金额 / 收支 / 分类 / 备注一步搞定，点任意记录可编辑、删除。
- **账单导入**：支持导入支付宝 / 微信官方导出的账单 CSV（自动去重，GBK 编码兼容）。
  - 支付宝：我的 → 账单 → 开具交易流水 → 邮箱接收 zip
  - 微信：我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账 → 邮箱接收 zip
- **月度统计**：按月切换，支出/收入/结余总览 + 分类占比条形图。
- **数据本地化**：Room 数据库存于手机本地，无任何网络权限。

## 使用步骤（首次安装后）

1. 打开 App，点击顶部横幅「去开启」；
2. 在系统「通知使用权」设置中允许 **轻记账** 读取通知；
3. 之后在支付宝/微信/QQ 每完成一笔支付，即自动生成一条记录（同分钟同金额自动去重）。

## 构建

用 Android Studio（Ladybug 或更新版）打开本目录，Gradle 同步后直接 Run；
或命令行：

```bash
./gradlew assembleDebug    # 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17+，Android SDK 35。

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
├── notify/                # 通知监听服务 + 金额解析
├── importer/              # 支付宝/微信 CSV 导入
└── ui/                    # Compose 界面（明细 / 统计 / 弹窗 / 主题）
```
