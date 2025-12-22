
![icon](https://github.com/user-attachments/assets/0ba29abb-6dee-43be-b632-5a7b12e8baae#pic_center)

# Linux.do Explorer

**Browse Linux.do Forum in IntelliJ IDEA**

[![License](https://img.shields.io/badge/license-GPL3.0-green?style=flat-square)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/TooAme/linuxdo-explorer?style=flat-square&logo=github)](https://github.com/TooAme/linuxdo-explorer)
[![JetBrains](https://img.shields.io/badge/JetBrains-2023.3+-blue?style=flat-square&logo=jetbrains)](https://www.jetbrains.com/)

---

## Introduction

**Linux.do Explorer** is an IntelliJ IDEA plugin that allows you to browse [Linux.do](https://linux.do) forum content while writing code. It integrates seamlessly into IDEA's native interface, helping you access information efficiently.

## Features

### Secure Login
- **Browser Login**: Use built-in browser to login, automatically obtain Cookie and User-Agent
- **Manual Configuration**: Support manual Cookie input (must contain _t= field)
- **Secure Storage**: Sensitive information stored using IntelliJ's secure storage, never sent to any server

### Content Browsing
- **Category Browsing**: Browse various forum categories and topics
- **Topic Expansion**: Expand topics directly in sidebar to view all replies
- **Topic Preview**: Double-click topic to view full content in built-in browser
- **Search Topics**: Search bar in toolbar to find topics quickly
- **Notifications**: Display unread notifications for quick updates
- **Auto Refresh**: Support 5/10/20/60 minutes auto refresh
- **Load More**: Pagination support for topics and replies

### Topic Saving
- **Save Post Content**: Save topic as Markdown file (saved to `.linuxdoexp/content/`)
- **AI Summary Save**: Support OpenAI-compatible API for topic summarization (saved to `.linuxdoexp/summary/`)

### Context Menu
- **Topic Node**: Summarize and Save, Save Post Content, Copy Link, Open in Browser
- **Reply Node**: Copy Link, Open in Browser

### Display Options
- **Show Images**: Toggle image display in topic window
- **Show Emoji**: Toggle emoji display in topic window
- **Compact Mode**: Reduce whitespace in detail page
- **Grayscale Mode**: Display images in grayscale
- **Post Info**: Show/hide post number, author, time
- **Font Size**: Small/Medium/Large options
- **Ignore Line Breaks**: Optimize content layout

### Loading Settings
- **Topics per Load**: Configure how many topics to load at once (5-30)
- **Replies per Load**: Configure how many replies to load at once (5-20)
- **Notification Mode**: Off / Unread only / Show all

### Quick Disguise
- **Shift+L Shortcut**: Toggle disguise/restore with one key
- **Hide Mode**: Display "Loading..."
- **English Fill Mode**: Display fake project structure (default)

### AI Summary Configuration (Optional)
- **API URL**: Support OpenAI-compatible API endpoints
- **API Key**: Securely stored API key
- **Model**: Custom model name (default: gpt-3.5-turbo)

## Installation

### Method 1: Install from JetBrains Marketplace (Recommended)
1. Open IDEA, go to `File` -> `Settings` -> `Plugins`
2. Search for `Linux.do Explorer`
3. Click `Install`

### Method 2: Download from Release
1. Download the latest `.zip` file from [Releases](https://github.com/TooAme/linuxdo-explorer/releases)
2. Install in IDEA via `Settings` -> `Plugins` -> ⚙️ -> `Install Plugin from Disk...`

## Usage

1. **Open View**: After installation, you'll see **"Linux.do"** tool window in the right sidebar
2. **Login**: Click settings button, use browser login or manually configure Cookie
3. **Browse Content**: Expand categories to browse topics, double-click to view details
4. **Save Content**: Click "Save Post Content" or "Summarize and Save" in topic detail window
5. **Quick Disguise**: Press `Shift+L` to quickly toggle disguise state

---

**This plugin will be continuously updated. Welcome to Star and follow!**
