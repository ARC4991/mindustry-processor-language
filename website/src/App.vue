<script setup lang="ts">
import { computed, ref } from "vue";
import SiteHeader from "./components/SiteHeader.vue";
import TutorialView from "./components/TutorialView.vue";
import { pages } from "./content/pages";
import type { PageKey } from "./types/site";

const page = ref<PageKey>("home");
const active = computed(() => pages[page.value]);
const sourceUrl = "https://github.com/ARC4991/mindustry-processor-language";

function select(name: PageKey) {
  page.value = name;
  window.scrollTo({ top: 0, behavior: "smooth" });
}
</script>

<template>
  <SiteHeader :current="page" :pages="pages" @select="select" />

  <main class="shell">
    <section class="hero">
      <p class="eyebrow">{{ active.eyebrow }}</p>
      <h1>{{ active.title }}</h1>
      <p class="lead">{{ active.lead }}</p>
    </section>

    <section v-if="page === 'home'" class="page-content">
      <div class="feature-grid">
        <article class="card"><span class="index">01</span><h2>语言与类型</h2><p>Int、Float、Bool、String、元组、数组、List、Set、MutableList、类、继承与重载统一进入类型系统；局部变量和函数返回值可按上下文推导。</p></article>
        <article class="card"><span class="index">02</span><h2>对象化游戏接口</h2><p><code>Unit.getAllDagger()</code> 与 <code>Building.getAll...</code> 返回通用 Set。过滤、遍历、计数、索引和控制以普通集合 API 组合，底层 flag、重连和游标由 Runtime 管理。</p></article>
        <article class="card"><span class="index">03</span><h2>资源感知 Runtime</h2><p>编译器计算物理 Memory、对象池、字符串表、MutableList 槽位和多处理器邮箱；产物中包含处理器、Memory、报告和连接说明。</p></article>
        <article class="card"><span class="index">04</span><h2>可复现的包</h2><p>workspace、Git 和 .mplpkg 使用同一内容摘要模型。锁文件固定包版本、硬件接口与传递依赖，构建阶段不发生隐式网络更新。</p></article>
      </div>
      <section class="split">
        <div><h2>从源代码到可部署蓝图</h2><p>语义分析建立类型和模块关系，MIL 保留结构化程序并展开游戏能力，目标 profile 负责指令 lowering，Runtime 规划器随后分配处理器与共享 Memory。</p><div class="chips"><span>v146 基线</span><span>v159.7 优化</span><span>MIL 中间语言</span><span>资源报告</span></div></div>
        <pre class="code"><code><span class="cm">// 源级结构保留到 MIL</span>
<span class="kw">val</span> units = <span class="ty">Unit</span>.<span class="fn">getAllDagger</span>();
<span class="kw">for</span> (<span class="kw">var</span> unit : units) {
  unit.<span class="fn">move</span>(unit.x + 1, unit.y);
}</code></pre>
      </section>
    </section>

    <TutorialView v-else-if="page === 'tutorial'" />

    <section v-else-if="page === 'language'" class="page-content">
      <div class="two-column"><article><h2>集合与值</h2><p>集合工厂统一为 <code>List.of</code> 与 <code>Set.of</code>。数组字面量、元组、受证明动态下标与容量化 MutableList 均映射为编译器管理的布局。</p><pre class="code"><code><span class="kw">val</span> point: (<span class="ty">Int</span>, <span class="ty">Int</span>) = (3, 4);
<span class="kw">val</span> targets = <span class="ty">List</span>.<span class="fn">of</span>(1, 2, 3);
<span class="kw">val</span> allowed = <span class="ty">Set</span>.<span class="fn">of</span>(1, 3, 5);</code></pre></article>
      <article><h2>对象、继承与重载</h2><p>类支持字段、构造、方法、访问控制、单继承、虚方法和 <code>super</code>。同名方法按参数类型形成签名重载，调用时选择最具体的候选。</p><pre class="code"><code><span class="kw">class</span> <span class="ty">Scout</span> <span class="kw">extends</span> <span class="ty">UnitRole</span> {
  <span class="kw">fun</span> <span class="fn">score</span>(range: <span class="ty">Float</span>) = range * 2.0;
}</code></pre></article></div>
      <h2>Unit 与 Building</h2><pre class="code"><code><span class="kw">val</span> draggers = <span class="ty">Unit</span>.<span class="fn">getAllDagger</span>()
  .<span class="fn">where</span>(unit =&gt; unit.alive)
  .<span class="fn">where</span>(unit =&gt; unit.x &gt; 10);
<span class="kw">for</span> (<span class="kw">var</span> unit : draggers) { unit.<span class="fn">move</span>(unit.x + 1, unit.y); }</code></pre>
      <p class="note">完整规则见仓库 <a :href="sourceUrl + '/tree/master/docs/语法设计/稳定'" target="_blank">稳定语法文档</a>。</p>
    </section>

    <section v-else-if="page === 'design'" class="page-content timeline">
      <article><span>01</span><h2>硬件作为契约</h2><p><code>.mplh</code> 声明 Message、Display、Building 与组合屏幕，编译器在链接阶段验证名称、类型、尺寸和包注入需求。</p></article>
      <article><span>02</span><h2>内存自动规划</h2><p>代码层面不直接分配物理槽；对象、数组、字符串和 MutableList 需求汇总为真实 Cell/Bank、处理器变量与蓝图连接。</p></article>
      <article><span>03</span><h2>运行时协作</h2><p>高成本纯数值函数可以被分配到 Worker 处理器。Main 与 Worker 通过公共 Memory 邮箱同步，所有分片受到真实指令与标签统计约束。</p></article>
      <article><span>04</span><h2>确定性部署</h2><p>蓝图包含处理器和内存；mlog、MIL、报告、部署清单与连接说明作为同一次构建的可审查产物一并输出。</p></article>
    </section>

    <section v-else-if="page === 'runtime'" class="page-content">
      <div class="two-column"><article><h2>存储层</h2><p>处理器变量承载临时标量；物理 Memory 承载动态数组、MutableList、对象池、字符串序列和跨处理器邮箱。编译器按所需槽位和目标能力选择 Cell/Bank 并写入蓝图。</p><div class="chips"><span>对象池</span><span>字符串表</span><span>动态聚合</span><span>共享邮箱</span></div></article><article><h2>跨 Tick 语义</h2><p>顶层程序按传统顺序执行并默认结束为 <code>stop</code>。持续逻辑由用户显式编写 <code>while</code>；函数通过 <code>@counter</code> 形成可恢复的控制流 ABI，资源预算不改变源级顺序语义。</p></article></div>
      <h2>多处理器协作</h2><div class="feature-grid"><article class="card"><h2>Main</h2><p>持有 I/O、Unit/Building 控制和顺序控制流，发起经过效果分析确认的纯数值任务。</p></article><article class="card"><h2>Worker-N</h2><p>从共享 Memory 邮箱读取参数，执行同一 target lowering 得到的函数代码，写回结果并等待下一次请求。</p></article><article class="card"><h2>规划器</h2><p>根据真实指令、标签、处理器类型、内存类型和 runtime.goal 均衡可分片的函数组件。</p></article><article class="card"><h2>部署产物</h2><p>每片都有 MIL 与 mlog；报告列出分片资源、Memory 段与任务邮箱，蓝图只放处理器和 Memory。</p></article></div>
    </section>

    <section v-else-if="page === 'packages'" class="page-content">
      <div class="two-column"><article><h2>当前目录命令</h2><p><code>mpl init</code>、<code>install</code>、<code>check</code> 与 <code>build</code> 均把当前目录作为项目根。输出目录是 build 的唯一可选位置参数。</p><pre class="code"><code>mpl <span class="kw">init</span>
mpl <span class="kw">install</span> @mpl/example
mpl <span class="kw">search</span> display
mpl <span class="kw">build</span> --target=v146 build</code></pre></article><article><h2>来源与锁定</h2><p>workspace 使用相对路径；Git 使用浅克隆；registry 下载 .mplpkg ZIP。每种来源均由同一个内容 SHA-256 缓存、包清单和 .mplh 硬件摘要共同约束。</p><pre class="code"><code><span class="string">"dependencies"</span>: {
  <span class="string">"orbit"</span>: <span class="string">"git:https://.../orbit.git"</span>,
  <span class="string">"screen-kit"</span>: <span class="string">"registry:https://.../screen.mplpkg"</span>
}</code></pre></article></div>
      <div class="feature-grid"><article class="card"><h2>网络索引</h2><p>IO 站点提供可搜索的 <code>index.json</code>；索引条目声明包名、版本、下载来源和说明。</p></article><article class="card"><h2>锁文件</h2><p><code>mpl.lock</code> 固定传递依赖版本、精确来源、内容摘要和硬件摘要，构建阶段不隐式更新。</p></article><article class="card"><h2>硬件注入</h2><p>包在 .mplh 中声明 require，应用在 import 时以 with 显式注入并检查 Display 组合尺寸。</p></article><article class="card"><h2>跨平台 CLI</h2><p>发行 ZIP 同时携带 Linux/macOS 的 mpl、Windows 的 mpl.bat 与 Java 17 依赖库；标签发布自动进入 GitHub Release。</p></article></div>
    </section>

    <section v-else-if="page === 'compiler'" class="page-content">
      <div class="pipeline"><div>MPL 源码</div><b>→</b><div>AST / HIR</div><b>→</b><div>MIL</div><b>→</b><div>mlog</div><b>→</b><div>蓝图</div></div>
      <div class="feature-grid"><article class="card"><h2>前端</h2><p>拆分的 ANTLR 词法/语法文件解析 MPL 与 MIL；后置声明、模块导入、类继承、泛型集合和重载进入统一语义分析。</p></article><article class="card"><h2>优化</h2><p>常量折叠、死分支移除、循环优化、最短标签和目标 profile 专用 lowering 在代码生成前后协同工作。</p></article><article class="card"><h2>产物</h2><p><code>runtime.msch</code>、每片 <code>.mlog</code>/<code>.mil</code>、格式化 JSON 报告与部署清单共同描述最终部署。</p></article></div>
      <pre class="code"><code><span class="cm"># 调试构建保留可读标签；常规构建使用 _0、_1 …</span>
mpl <span class="kw">build</span> --debug --target=v146 build</code></pre>
    </section>

    <section v-else class="page-content">
      <div class="feature-grid examples"><article class="card"><h2>基础输出</h2><p>最小项目、Message 硬件声明和自动 print flush。</p><a :href="sourceUrl + '/tree/master/examples/基础输出'" target="_blank">打开示例 ↗</a></article><article class="card"><h2>单位绕圈</h2><p>三台 Dagger 的查询、where 链和持续控制循环。</p><a :href="sourceUrl + '/tree/master/examples/单位绕圈'" target="_blank">打开示例 ↗</a></article><article class="card"><h2>继承与推导</h2><p>单继承、super、虚方法和最具体重载返回类型推导。</p><a :href="sourceUrl + '/tree/master/examples/继承与类型推导'" target="_blank">打开示例 ↗</a></article><article class="card"><h2>聚合函数推导</h2><p>元组返回推导、数组形状传播与逐元素 ABI。</p><a :href="sourceUrl + '/tree/master/examples/元组函数推导'" target="_blank">打开示例 ↗</a></article><article class="card"><h2>混合模块</h2><p>MPL/MIL 模块、导入导出和硬件 alias。</p><a :href="sourceUrl + '/tree/master/examples/混合模块'" target="_blank">打开示例 ↗</a></article></div>
    </section>
  </main>

  <footer><div class="shell">MPL / GPL-3.0 / Java 17 / Vue 3 documentation site</div></footer>
</template>
