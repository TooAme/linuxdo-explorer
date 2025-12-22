
![icon](https://github.com/user-attachments/assets/0ba29abb-6dee-43be-b632-5a7b12e8baae#pic_center)

# Linux.do Explorer

**在 IntelliJ IDEA 中浏览 Linux.do 论坛**

[![License](https://img.shields.io/badge/license-GPL3.0-green?style=flat-square)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/TooAme/linuxdo-explorer?style=flat-square&logo=github)](https://github.com/TooAme/linuxdo-explorer)
[![JetBrains](https://img.shields.io/badge/JetBrains-2023.3+-blue?style=flat-square&logo=jetbrains)](https://www.jetbrains.com/)

---

## 简介

**Linux.do Explorer** 是一个 IntelliJ IDEA 插件，让你在编写代码的同时，能够方便快捷地浏览 [Linux.do](https://linux.do) 论坛的内容。它完美融入 IDEA 的原生界面，助你高效获取信息。

## 功能特性

### 安全登录
- **浏览器登录**：使用内置浏览器登录，自动获取 Cookie 和 User-Agent
- **手动配置**：支持手动填写 Cookie（需包含 _t= 字段）
- **安全存储**：敏感信息使用 IntelliJ 安全存储，不会发送到任何服务器

### 内容浏览
- **分类浏览**：支持浏览论坛的各种分类和话题
- **话题展开**：直接在侧边栏展开话题，查看所有回复
- **话题预览**：双击话题在内置浏览器中查看完整内容
- **话题搜索**：工具栏搜索框，快速搜索话题
- **通知提醒**：显示未读通知，快速了解动态
- **自动刷新**：支持 5/10/20/60 分钟自动刷新
- **分页加载**：支持话题和回复的分页加载

### 话题保存
- **保存帖子内容**：将话题保存为 Markdown 文件（保存至 `.linuxdoexp/content/`）
- **AI 总结保存**：支持调用 OpenAI 兼容 API 进行话题总结（保存至 `.linuxdoexp/summary/`）

### 右键菜单
- **话题节点**：总结并保存、保存帖子内容、复制链接、在浏览器中打开
- **回复节点**：复制链接、在浏览器中打开

### 显示选项
- **图片显示**：开关话题窗口图片显示
- **表情符号**：开关话题窗口表情显示
- **紧凑模式**：减少详情页空白
- **图片滤镜**：正常 / 黑白 / 点阵 三种模式
- **帖子信息**：显示/隐藏楼数、发帖人、时间
- **字号设置**：小/中/大 三档字号
- **忽略回车符**：优化内容排版
- **预览模式**：底部面板预览 或 悬浮提示预览

### 加载设置
- **单次加载话题数**：配置每次加载的话题数量（5-30）
- **单次加载回复数**：配置每次加载的回复数量（5-20）
- **通知显示模式**：关闭显示 / 仅显示未读 / 显示全部

### 快捷伪装
- **Shift+L 快捷键**：一键切换伪装/恢复
- **隐藏模式**：显示 "Loading..."
- **英文填充模式**：显示伪装的项目结构（默认）

### AI 总结配置（可选）
- **API URL**：支持 OpenAI 兼容的 API 地址
- **API Key**：安全存储 API 密钥
- **Model**：自定义模型名称（默认 gpt-3.5-turbo）

## 安装方法

### 方式一：从 JetBrains Marketplace 安装（推荐）
1. 打开 IDEA，进入 `File` -> `Settings` -> `Plugins`
2. 搜索 `Linux.do Explorer`
3. 点击 `Install` 安装

### 方式二：从 Release 下载
1. 从 [Releases](https://github.com/TooAme/linuxdo-explorer/releases) 下载最新的 `.zip` 文件
2. 在 IDEA 中通过 `Settings` -> `Plugins` -> ⚙️ -> `Install Plugin from Disk...` 安装

## 使用方法

1. **打开视图**：安装完成后，在右侧边栏会看到 **"Linux.do"** 工具窗口
2. **登录账号**：点击设置按钮，使用浏览器登录或手动配置 Cookie
3. **浏览内容**：展开分类浏览话题，双击话题查看详细内容
4. **保存内容**：在话题详情窗口点击 "保存帖子内容" 或 "总结并保存"
5. **快捷伪装**：按 `Shift+L` 快速切换伪装状态

---

**本插件将持续更新，欢迎 Star 关注！**
