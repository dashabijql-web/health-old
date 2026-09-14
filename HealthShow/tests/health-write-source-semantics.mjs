import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import assert from 'node:assert/strict';

const sourcePath = path.resolve('tests', 'api', 'health-write-regression.mjs');

test('write regression treats new empty data source as partial coverage instead of failure', () => {
  const source = fs.readFileSync(sourcePath, 'utf8');

  assert.match(source, /function skipCheck\(/, 'write regression must have an explicit skipCheck helper');
  assert.match(source, /record\.status\s*=\s*error\?\.skip\s*\?\s*['"]skipped['"]\s*:\s*['"]failed['"]/, 'write regression must record skipped checks');
  assert.match(source, /isNewDataSource/, 'write regression must branch on SQL_DB/API_DATA_SOURCE for new-source semantics');
  assert.match(source, /new data source has no bound write probe target/, 'missing new-source bound device should be a skip note');
  assert.match(source, /new data source has no writable AI report candidate/, 'missing new-source AI employee should be a skip note');
  assert.match(source, /failedCount = summary\.checks\.filter\(\(item\) => item\.status === 'failed'\)\.length/, 'skipped checks must not fail the process');
  assert.match(source, /skippedCount = summary\.checks\.filter\(\(item\) => item\.status === 'skipped'\)\.length/, 'skipped checks must be counted explicitly');
});
