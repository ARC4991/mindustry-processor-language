# CLI 与包管理

`mpl` 按 Git/Node 风格工作：当前工作目录就是唯一项目根目录。命令不接受项目目录参数，避免一次操作误修改另一个项目。

```bash
mpl init [--target=v146]
mpl install
mpl install <包名>
mpl install <包名>=<git-url|mplpkg路径|registry-url>
mpl search [关键词]
mpl check --target=v146
mpl build [--debug] --target=v146 [输出目录]
```

`init` 要求当前目录为空或尚未初始化。`build` 的可选位置参数仅是构建产物目录，默认 `build`；它不改变项目根目录。

## 依赖来源

`mpl.json` 的 `dependencies` 使用显式来源前缀：

```json
{
  "dependencies": {
    "local-ui": "workspace:../local-ui",
    "orbit": "git:https://github.com/example/orbit.git",
    "screen-kit": "registry:https://packages.example/screen-kit.mplpkg"
  }
}
```

- `workspace:` 只接受相对路径，适用于同一工作区开发。
- `git:` 使用浅克隆取得源码，然后按受纳入文件的 SHA-256 缓存。
- `registry:` 下载 `.mplpkg` ZIP，拒绝目录穿越，并按同一 SHA-256 缓存。

安装时会生成格式化的 `mpl.lock`，其中记录包名、版本、精确来源、包内容摘要、`.mplh` 摘要和传递依赖版本。构建阶段只读取锁定缓存，不会重新下载或改写锁文件。缓存缺失、清单来源不一致、包内容变化、硬件接口变化和能力不兼容都会导致构建失败，并提示重新执行 `mpl install`。

## 网络索引

`mpl search` 和裸包名安装从 MPL IO 站点的 `registry/index.json` 读取清单。地址可用环境变量 `MPL_PACKAGE_INDEX_URL` 覆盖；网络代理可通过 `MPL_HTTP_PROXY` 或 `HTTPS_PROXY` 配置。索引格式为：

```json
{
  "schemaVersion": 1,
  "packages": {
    "@mpl/example": {
      "version": "1.0.0",
      "source": "registry:https://example.invalid/example.mplpkg",
      "description": "示例包"
    }
  }
}
```

GitHub CD 会把仓库 `registry/index.json` 和官方站点一起部署到 `ARC4991/mindustry-processor-language-io` 的 `gh-pages` 分支。部署需要仓库 Secret `IO_DEPLOY_TOKEN`，它必须有目标仓库的写权限。

## 跨平台发行

`./gradlew releaseArchive` 生成 `releases/mpl-<版本>-universal-jdk17.zip`。包内同时包含 `mpl`（Linux/macOS）与 `mpl.bat`（Windows）、运行库和 SHA-256 清单；运行环境需要 JDK 17 或更新版本。GitHub Actions 在 Ubuntu、macOS、Windows 上执行测试并验证相应启动器，推送 `v*` 标签时创建 GitHub Release。
