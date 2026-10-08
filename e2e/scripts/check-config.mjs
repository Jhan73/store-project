// Static guard over playwright.config.ts (no TypeScript runner is available, so it reads the text).
// Limits: it only sees literal settings; a value computed at runtime or set from another file is not detected.
import { readFileSync } from 'node:fs';

const path = process.argv[2] ?? new URL('../playwright.config.ts', import.meta.url);
const source = readFileSync(path, 'utf8').replace(/\/\/.*$/gm, '');
const failures = [];

const reporter = /reporter\s*:\s*([\s\S]*?),\s*\n\s*(?:\w+\s*:|\})/.exec(source);
if (!reporter) {
  failures.push('reporter setting not found');
} else {
  for (const banned of ['html', 'json', 'blob', 'junit']) {
    if (new RegExp(`['"\`]${banned}['"\`]`).test(reporter[1])) {
      failures.push(`reporter "${banned}" persists typed values as step titles`);
    }
  }
}

for (const key of ['trace', 'video', 'screenshot']) {
  if (!new RegExp(`\\b${key}\\s*:\\s*['"]off['"]`).test(source)) {
    failures.push(`${key} must be 'off'`);
  }
}

if (!/process\.env\[\s*['"]PLAYWRIGHT_NO_COPY_PROMPT['"]\s*\]\s*=\s*['"]1['"]/.test(source)) {
  failures.push('PLAYWRIGHT_NO_COPY_PROMPT must be set to 1');
}

if (failures.length > 0) {
  console.error(`check:config failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
console.log('check:config passed');
