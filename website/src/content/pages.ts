import type { PageContent, PageKey } from "../types/site";

export const pages: Record<PageKey, PageContent> = {
  home: { label: "概览", eyebrow: "Mindustry Logic Toolchain", title: "把处理器逻辑写成可维护的程序", lead: "MPL 为 Mindustry Logic 提供完整的编译链：从带类型、对象、集合和模块的源代码，到可审查的 MIL、目标版本 mlog、资源报告与可直接导入的运行时蓝图。" },
  tutorial: { label: "语法教程", eyebrow: "Tutorial", title: "从 MPL 程序到 MIL 宏调用", lead: "以一个可部署的处理器程序串联变量、函数、对象、查询、硬件接口、构建产物与 MIL 的目标层表达。" },
  language: { label: "语言规范", eyebrow: "Language", title: "严格类型与熟悉的程序结构", lead: "MPL 将 Kotlin 风格类型推导、Java/Kotlin 风格重载和 TypeScript 风格单继承组合为面向处理器逻辑的实用语言表面。" },
  design: { label: "设计思路", eyebrow: "Design", title: "把游戏约束前置到编译期", lead: "硬件、内存、指令预算、包接口和目标 profile 都作为编译器可验证的输入，生成可部署的运行时拓扑。" },
  runtime: { label: "Runtime", eyebrow: "Runtime", title: "为处理器、Memory 与跨 Tick 执行规划运行时", lead: "MPL 将对象、字符串、集合和纯数值 Worker 的存储与协作需求收集为稳定、可审查的运行时布局。" },
  packages: { label: "包与 CLI", eyebrow: "Package Management", title: "可复现的依赖与当前目录工作流", lead: "MPL CLI 使用当前目录作为唯一项目根，通过内容摘要、严格锁文件和硬件接口校验管理 workspace、Git 和 .mplpkg 包。" },
  compiler: { label: "编译细节", eyebrow: "Compiler", title: "MPL -> MIL -> mlog -> 蓝图", lead: "每个阶段都有明确职责：保留源级结构、展开目标能力、规划 Runtime，并输出可检查的构建产物。" },
  examples: { label: "参考代码", eyebrow: "Examples", title: "从最小程序到多处理器 Runtime", lead: "仓库示例与测试一起维护，覆盖硬件声明、对象、集合、单位管理、字符串、模块和蓝图部署。" }
};
