import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const ROOT = process.cwd();
const SRC = path.join(ROOT, 'src');
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-');
const ARTIFACT_DIR = path.resolve(ROOT, 'tests', 'product', 'artifacts', RUN_ID);
const REPORT_JSON = path.join(ARTIFACT_DIR, 'ux-competitor-benchmark.json');
const REPORT_MD = path.join(ARTIFACT_DIR, 'ux-competitor-benchmark.md');
const MIN_SCORE = Number.parseInt(process.env.PRODUCT_MIN_SCORE || '72', 10);
const STRICT = process.env.PRODUCT_STRICT === '1';

const BENCHMARKS = [
  {
    id: 'visual-system',
    title: '视觉系统一致性（对标 Apple Health / Fitbit / Garmin 的清晰健康数据呈现）',
    weight: 18,
    checks: [
      check('uses-dashboard-root', ['dm-root', 'dashboard'], 4),
      check('uses-cards', ['card', 'kpi', 'stat'], 4),
      check('uses-chart-canvas-svg', ['canvas', 'echarts', 'svg'], 4),
      check('has-loading-empty-states', ['loading', 'empty', '暂无', '无数据'], 3),
      check('responsive-css', ['@media', 'mobile', 'grid-template', 'flex'], 3)
    ]
  },
  {
    id: 'layout-information-architecture',
    title: '布局与信息层级（对标一流监控台的首屏概览、下钻、筛选）',
    weight: 18,
    checks: [
      check('has-nav-groups', ['指挥中心', '监测中心', '预警中心', '人员中心'], 4),
      check('has-filter-search', ['搜索', '筛选', 'filter', 'query', 'date'], 4),
      check('has-drilldown-detail', ['详情', 'drawer', 'dialog', 'profile'], 4),
      check('has-table-list', ['el-table', 'list', 'records'], 3),
      check('has-route-coverage', ['risk-warning', 'report-center', 'trend-warning', 'notifications'], 3)
    ]
  },
  {
    id: 'feature-completeness',
    title: '功能完善度（健康监测、预警、人员、报告、AI、设备闭环）',
    weight: 24,
    checks: [
      check('health-metrics', ['heart-rate', 'blood-oxygen', 'pressure', 'blood-pressure', 'sleep'], 5),
      check('warning-workflow', ['risk-warning', 'alert', 'handled', '处理'], 5),
      check('people-device', ['employee', 'device', 'department'], 4),
      check('report-export-ai', ['report', 'export', 'AI', 'ai-chat'], 4),
      check('dual-source-awareness', ['Health-Data-Source', 'data-source', 'new', 'old'], 3),
      check('mobile-entry', ['mobile', 'bottom-nav', 'viewport'], 3)
    ]
  },
  {
    id: 'operability',
    title: '运营可用性（异常、空态、错误、加载、可追踪）',
    weight: 18,
    checks: [
      check('error-handling', ['catch', 'error', '失败', '重试'], 4),
      check('loading-states', ['loading', '加载'], 4),
      check('empty-states', ['empty', '暂无', '无数据'], 4),
      check('time-range', ['date', 'range', 'month', 'today'], 3),
      check('status-semantics', ['danger', 'warning', 'success', 'info', '高危', '中危', '低危'], 3)
    ]
  },
  {
    id: 'technical-quality',
    title: '前端实现质量（组件化、复用、健壮性、测试可覆盖）',
    weight: 22,
    checks: [
      check('shared-modules', ['common', 'utils', 'mixin', 'components'], 4),
      check('tests-present', ['tests', 'audit', 'warning-lifecycle'], 4),
      check('api-abstraction', ['api/', 'request', 'service'], 4),
      check('sanitized-html', ['DOMPurify', 'sanitize', 'marked'], 3),
      check('no-obvious-debug', ['console.log'], -4),
      check('no-dangerous-eval', ['eval(', 'new Function'], -8)
    ]
  }
];

function check(id, needles, points) {
  return { id, needles, points };
}

async function walk(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true }).catch(() => []);
  const files = [];
  for (const entry of entries) {
    if (['node_modules', 'dist', '.git'].includes(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await walk(full));
    else if (/\.(vue|js|mjs|ts|scss|css)$/.test(entry.name)) files.push(full);
  }
  return files;
}

const files = await walk(SRC);
const corpus = (await Promise.all(files.map((file) => fs.readFile(file, 'utf8').catch(() => '')))).join('\n');
const lowerCorpus = corpus.toLowerCase();

function hit(needle) {
  return lowerCorpus.includes(String(needle).toLowerCase());
}

function evalCheck(item) {
  const matched = item.needles.filter(hit);
  const passed = item.points < 0 ? matched.length === 0 : matched.length > 0;
  const score = passed ? Math.abs(item.points) : 0;
  const max = Math.abs(item.points);
  return { id: item.id, passed, score, max, matched, expected: item.needles, mode: item.points < 0 ? 'absence' : 'presence' };
}

const categories = BENCHMARKS.map((category) => {
  const checks = category.checks.map(evalCheck);
  const rawMax = checks.reduce((sum, item) => sum + item.max, 0);
  const rawScore = checks.reduce((sum, item) => sum + item.score, 0);
  const score = rawMax ? Math.round((rawScore / rawMax) * category.weight) : 0;
  return { ...category, checks, rawScore, rawMax, score };
});

const totalScore = categories.reduce((sum, item) => sum + item.score, 0);
const failedChecks = categories.flatMap((category) => category.checks.filter((item) => !item.passed).map((item) => ({ category: category.id, ...item })));
const summary = {
  runId: RUN_ID,
  startedAt: new Date().toISOString(),
  finishedAt: new Date().toISOString(),
  minScore: MIN_SCORE,
  strict: STRICT,
  score: totalScore,
  status: totalScore >= MIN_SCORE && (!STRICT || failedChecks.length === 0) ? 'passed' : 'failed',
  note: '本脚本是竞品启发式质量雷达：对标一流健康/监控产品的视觉、布局、功能、运营、技术维度；它不能替代真实用户访谈和人工视觉评审。',
  categories,
  failedChecks
};

function buildMarkdown() {
  return [
    '# HealthShow UX / competitor benchmark report',
    '',
    `- run_id: ${summary.runId}`,
    `- score: ${summary.score}/100`,
    `- min_score: ${summary.minScore}`,
    `- status: ${summary.status}`,
    `- note: ${summary.note}`,
    '',
    '## Categories',
    '',
    '| category | score | weight |',
    '| --- | ---: | ---: |',
    ...summary.categories.map((item) => `| ${item.title} | ${item.score} | ${item.weight} |`),
    '',
    '## Failed / missing checks',
    '',
    ...(summary.failedChecks.length ? summary.failedChecks.map((item) => `- ${item.category}/${item.id}: expected ${item.mode} of ${item.expected.join(', ')}`) : ['- none'])
  ].join('\n');
}

await fs.mkdir(ARTIFACT_DIR, { recursive: true });
await fs.writeFile(REPORT_JSON, JSON.stringify(summary, null, 2), 'utf8');
await fs.writeFile(REPORT_MD, buildMarkdown(), 'utf8');
console.log(JSON.stringify({ artifactDir: ARTIFACT_DIR, reportFile: REPORT_MD, score: summary.score, status: summary.status, failedChecks: summary.failedChecks.length }, null, 2));
if (summary.status === 'failed') process.exitCode = 1;
