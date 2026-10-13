# 阅读量管理（Halo-plugin-visit-editor）

> 源码仓库：https://github.com/MyrStar/Halo-plugin-visit-editor
> 作者：[Eternal](https://iloved.top)

一款 [Halo 2.x](https://www.halo.run) 插件，提供**文章阅读量的可视化管理**能力：在 Console 后台直接查看每篇文章的访问计数，并可以自由修改。

## 功能特性

- 📊 文章列表一览：标题、发布状态、发布时间、当前阅读量
- ✏️ 行内编辑：直接修改任意文章的阅读量，支持单条保存或批量保存
- 🔍 标题搜索 + 分页浏览
- 🔁 即时生效：修改后前台刷新即可看到新阅读量，无需重启
- 🔐 权限控制：接口聚合到管理员角色，普通用户无权访问
- 🧩 兼容真实统计：真实访客访问仍会在修改值基础上正常累加，不会互相覆盖

## 实现原理

Halo 2.x 将文章访问量存储在 `Counter` 扩展（`metrics.halo.run/v1alpha1`，名称为 `posts.content.halo.run/{文章名}`）中。本插件通过 Extension API 读写该扩展：

- 修改后由 Halo 核心的 `PostCounterReconciler` 自动同步到文章统计注解，前台主题即时可见
- Halo 的访问统计采用**增量累加**机制（内存暂存 + 每分钟批量落库），手动修改的值不会被后续真实访问覆盖

## 安装

1. 在 [Releases](../../releases) 下载最新的 `plugin-visit-editor-x.y.z.jar`
2. Halo 后台 → 插件 → 安装 → 上传 jar 并启用
3. 左侧菜单「内容」分组下点击「阅读量管理」即可使用

要求 Halo >= 2.22。

## 使用

- 在列表中找到目标文章（可按标题搜索）
- 在「阅读量」列输入新的数值，点击「保存」；也可以一次性修改多行后点击右上角「保存全部修改」
- 前台文章页刷新即可看到新阅读量

> 注意：Halo 的真实访问统计每分钟批量写入一次，刚发生的访问最长约 1 分钟后才会体现在数值中。

## 开发

环境要求：JDK 21、Node.js 18+、pnpm

```bash
# 安装前端依赖（首次）
cd ui && pnpm install

# 构建插件（自动先构建前端，再打包 jar，产物在 build/libs）
./gradlew build

# 本地调试（需要 Docker，自动启动 Halo 并加载插件）
./gradlew haloServer
```

技术栈：Java 21 + Spring WebFlux（后端自定义 API）、Vue 3 + @halo-dev/ui-plugin-bundler-kit（Console 管理界面）。

## 许可证

[GPL-3.0](LICENSE)
