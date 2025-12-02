![icon](https://github.com/user-attachments/assets/0ba29abb-6dee-43be-b632-5a7b12e8baae#pic_center)

  # Linux.do Explorer

  **在 VSCode 中无缝浏览 Linux.do 论坛**
  
  [![Author](https://img.shields.io/badge/Linux.do-TooAme-blue?style=flat-square)](https://linux.do/u/tooame/summary)
  [![License](https://img.shields.io/badge/license-GPL3.0-green?style=flat-square)](LICENSE)
  [![GitHub Stars](https://img.shields.io/github/stars/TooAme/linuxdo-explorer?style=flat-square&logo=github)](https://github.com/TooAme/linuxdo-explorer)
  [![VSCode](https://img.shields.io/badge/VSCode-%5E1.85.0-aqua?style=flat-square&logo=visual-studio-code)](https://code.visualstudio.com/)

---

**Linux.do Explorer** 是一个强大的 VSCode 插件，专为开发者设计，让你在编写代码的同时，能够方便快捷地浏览 [Linux.do](https://linux.do) 论坛的内容。它伪装成文件浏览器的样式，完美融入 VSCode 的原生界面，助你摸鱼于无形（划掉）高效获取信息。

**本插件将持续更新，不妨点个star关注一下~**

如果你想参与本插件的开发，可以着手以下几个部分，这也是后续更新的方向：

- 更简单地登录

- 自定义设置：包括cookie，语言，自定义加载数量等

## 功能特性

*   **沉浸式体验**：伪装成 VSCode 原生文件浏览器界面，无缝集成，体验丝滑。
*   **分类浏览**：支持浏览论坛的各种分类和话题，轻松找到感兴趣的内容。
*   **话题展开**：直接在侧边栏展开话题，查看所有回复，无需跳转浏览器。
*   **一键刷新**：实时获取最新内容，不错过任何热门话题。
*   **快速访问**：支持一键在浏览器中打开话题，进行更复杂的操作。

## 使用方法

1.  **安装插件**：在 VSCode 插件市场搜索并安装 `Linux.do Explorer`。
2.  **打开视图**：安装完成后，在侧边栏的资源管理器中会看到 **"Linux.do"** 视图。
3.  **登录账号**：首次使用需要点击登录按钮，按照提示输入 Cookie。
4.  **开始浏览**：登录成功后，即可开始摸鱼！

## 如何获取 Cookie

为了正常使用插件，你需要获取 Linux.do 的 Cookie。请按照以下步骤操作：

1.  使用浏览器访问 [https://linux.do](https://linux.do) 并登录你的账号。
2.  按 `F12` 或右键点击页面选择“检查”打开开发者工具。
3.  切换到 **"网络 (Network)"** 标签页。
4.  刷新页面 (`F5`)。
5.  点击任意一个请求（推荐点击 `linux.do` 主域名的请求），在右侧面板找到 **"请求头 (Request Headers)"**。
6.  找到 **`Cookie:`** 字段，复制其后的完整内容。
7.  回到 VSCode，在插件登录界面粘贴刚才复制的 Cookie。