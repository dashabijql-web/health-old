import fs from 'node:fs';

function fileUrl(relativePath) {
  return new URL(`../${relativePath}`, import.meta.url);
}

function exists(relativePath) {
  return fs.existsSync(fileUrl(relativePath));
}

function countLines(relativePath) {
  return fs.readFileSync(fileUrl(relativePath), 'utf8').split(/\r?\n/).length;
}

function readText(relativePath) {
  return fs.readFileSync(fileUrl(relativePath), 'utf8');
}

const migratedPages = [
  {
    name: 'dashboard',
    dir: 'src/views/health-monitor/dashboard',
    maxIndexLines: 450,
    required: [
      'index.vue',
      'dashboard-page-state.js',
      'dashboard-runtime.js',
      'dashboard-view-model.js',
      'dashboard-view-actions.js',
      'dashboard.scss',
      'components/DashboardDialogs.vue'
    ]
  },
  {
    name: 'real-time',
    dir: 'src/views/health-monitor/real-time',
    maxIndexLines: 220,
    required: [
      'index.vue',
      'use-realtime-page.ts',
      'realtime.scss',
      'components/RealtimeHeader.vue',
      'components/RealtimeUserTable.vue',
      'components/RealtimeDetailDialog.vue'
    ]
  },
  {
    name: 'safety-command',
    dir: 'src/views/safety-command',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'safety-command-runtime.js',
      'safety-command-view-model.js',
      'safety-command.scss',
      'components/SafetyCommandDialogs.vue'
    ]
  },
  {
    name: 'health-portrait',
    dir: 'src/views/personnel-management/health-portrait',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'use-health-portrait-page.js',
      'health-portrait-page-state.js',
      'health-portrait-runtime.js',
      'health-portrait-view-model.js',
      'health-portrait.scss'
    ]
  },
  {
    name: 'employee-profile',
    dir: 'src/views/health-monitor/employee-profile',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'use-employee-profile-page.js',
      'employee-profile-runtime.js',
      'employee-profile-view-model.js',
      'employee-profile.scss'
    ]
  },
  {
    name: 'report-center',
    dir: 'src/views/health-monitor/report-center',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'report-center-runtime.js',
      'report-center-view-model.js',
      'report-center-chart.js',
      'report-center-export.js',
      'report-center.scss'
    ]
  },
  {
    name: 'risk-warning',
    dir: 'src/views/health-monitor/risk-warning',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'risk-warning-page-state.js',
      'risk-warning-runtime.js',
      'risk-warning-view-model.js',
      'risk-warning.scss'
    ]
  },
  {
    name: 'trend-warning',
    dir: 'src/views/health-monitor/trend-warning',
    maxIndexLines: 260,
    required: [
      'index.vue',
      'TrendSparkLine.js',
      'trend-warning.scss'
    ]
  },
  {
    name: 'mine-entry',
    dir: 'src/views/health-monitor/mine-entry',
    maxIndexLines: 380,
    required: [
      'index.vue',
      'mine-entry-review-workflow.js',
      'mine-entry-view-model.js',
      'mine-entry.scss'
    ]
  },
  {
    name: 'sleep',
    dir: 'src/views/health-monitor/sleep',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'sleep-page-state.js',
      'sleep-runtime.js',
      'sleep-view-model.js',
      'sleep.scss'
    ]
  },
  {
    name: 'device-management',
    dir: 'src/views/device-management',
    maxIndexLines: 550,
    required: [
      'index.vue',
      'use-device-management-page.js',
      'device-management-runtime.js',
      'device-management-view-model.js',
      'device-management.scss'
    ]
  },
  {
    name: 'user-list',
    dir: 'src/views/user-list',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'use-user-list-page.js',
      'user-list-runtime.js',
      'user-list-view-model.js',
      'user-list.scss'
    ]
  },
  {
    name: 'role-management',
    dir: 'src/views/role-management',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'use-role-management-page.js',
      'role-management-runtime.js',
      'role-management-view-model.js',
      'role-management.scss'
    ]
  },
  {
    name: 'alert-notifications',
    dir: 'src/views/alert-management/notifications',
    maxIndexLines: 330,
    required: [
      'index.vue',
      'notifications.scss'
    ]
  },
  {
    name: 'alert-records',
    dir: 'src/views/alert-management/records',
    maxIndexLines: 470,
    required: [
      'index.vue',
      'records-runtime.js',
      'records-view-model.js',
      'records.scss'
    ]
  },
  {
    name: 'ai-chat',
    dir: 'src/views/ai-chat',
    maxIndexLines: 360,
    required: [
      'index.vue',
      'ai-chat-chart.js',
      'ai-chat-export.js',
      'ai-chat-query-result.js',
      'ai-chat-session.js',
      'ai-chat-text.js',
      'ai-chat-view-model.js',
      'ai-chat.scss'
    ]
  },
  {
    name: 'workbench',
    dir: 'src/views/health-monitor/workbench',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'workbench-view-model.js',
      'workbench-chart.js',
      'workbench.scss'
    ]
  },
  {
    name: 'heart-rate',
    dir: 'src/views/health-monitor/heart-rate',
    maxIndexLines: 520,
    required: [
      'index.vue',
      'heart-rate-chart.js',
      'heart-rate.scss'
    ]
  },
  {
    name: 'blood-oxygen',
    dir: 'src/views/health-monitor/blood-oxygen',
    maxIndexLines: 540,
    required: [
      'index.vue',
      'blood-oxygen-chart.js',
      'blood-oxygen.scss'
    ]
  },
  {
    name: 'pressure',
    dir: 'src/views/health-monitor/pressure',
    maxIndexLines: 500,
    required: [
      'index.vue',
      'pressure-chart.js',
      'pressure.scss'
    ]
  },
  {
    name: 'blood-pressure',
    dir: 'src/views/health-monitor/blood-pressure',
    maxIndexLines: 510,
    required: [
      'index.vue',
      'blood-pressure-chart.js',
      'blood-pressure.scss'
    ]
  }
];

const legacyTrackedPages = [];

const sharedModules = [
  'src/views/health-monitor/metric-page/metric-data-loader.js',
  'src/views/health-monitor/metric-page/metric-export.js',
  'src/views/health-monitor/metric-page/metric-page-mixin.js',
  'src/views/health-monitor/metric-page/metric-scroll.js'
];

const issues = [];
const pageSummaries = [];
const forbiddenIndexPatterns = [
  { label: 'setInterval', pattern: /\bsetInterval\s*\(/ },
  { label: 'setTimeout', pattern: /\bsetTimeout\s*\(/ },
  { label: 'addEventListener', pattern: /\baddEventListener\s*\(/ },
  { label: 'removeEventListener', pattern: /\bremoveEventListener\s*\(/ },
  { label: 'echarts.init', pattern: /\becharts\.init\s*\(/ },
  { label: 'chart dispose', pattern: /\.dispose\s*\(/ }
];

for (const relativePath of sharedModules) {
  if (!exists(relativePath)) {
    issues.push(`missing shared page module ${relativePath}`);
  }
}

for (const page of migratedPages) {
  const missing = page.required
    .map((file) => `${page.dir}/${file}`)
    .filter((relativePath) => !exists(relativePath));

  for (const relativePath of missing) {
    issues.push(`${page.name}: missing required module ${relativePath}`);
  }

  const indexPath = `${page.dir}/index.vue`;
  if (exists(indexPath)) {
    const indexText = readText(indexPath);
    const indexLines = countLines(indexPath);
    pageSummaries.push({ name: page.name, indexLines });

    if (indexLines > page.maxIndexLines) {
      issues.push(`${page.name}: index.vue has ${indexLines} lines, expected <= ${page.maxIndexLines}`);
    }

    for (const forbidden of forbiddenIndexPatterns) {
      if (forbidden.pattern.test(indexText)) {
        issues.push(`${page.name}: index.vue still contains page-level side effect ${forbidden.label}`);
      }
    }
  }
}

const legacySummaries = legacyTrackedPages
  .filter((relativePath) => exists(relativePath))
  .map((relativePath) => ({
    path: relativePath,
    indexLines: countLines(relativePath)
  }));

if (issues.length > 0) {
  console.error('Page structure check failed.');
  for (const issue of issues) {
    console.error(`- ${issue}`);
  }
  process.exit(1);
}

console.log(JSON.stringify({
  migratedPages: pageSummaries.length,
  legacyTrackedPages: legacySummaries.length,
  sharedModules: sharedModules.length,
  pages: pageSummaries,
  legacy: legacySummaries
}, null, 2));
