import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const verifierPath = path.join(scriptDir, 'verify-mvp.mjs')

function runVerifier(args, env = {}) {
  return spawnSync(process.execPath, [verifierPath, ...args], {
    cwd: scriptDir,
    encoding: 'utf8',
    env: {
      ...process.env,
      ...env
    }
  })
}

test('verify-mvp --help prints the expected help text and exits successfully', () => {
  const result = runVerifier(['--help'])

  assert.equal(result.status, 0)
  assert.equal(result.stderr, '')
  assert.match(result.stdout, /Usage:/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs \[--keep-logs\] \[--help\] \[playwright-args\.\.\.\]/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs --keep-logs/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs --help/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs --grep smoke-check/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs --headed/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs --project chromium --grep smoke-check/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs -- --grep smoke-check/)
  assert.match(result.stdout, /node \.\/verify-mvp\.mjs --project chromium --headed/)
  assert.match(result.stdout, /\.\/verify-mvp\.sh --grep smoke-check/)
  assert.match(result.stdout, /\.\/verify-mvp\.sh --help/)
  assert.match(result.stdout, /\.\/verify-mvp\.sh --headed/)
  assert.match(result.stdout, /\.\/verify-mvp\.sh --project chromium --grep smoke-check/)
  assert.match(result.stdout, /\.\/verify-mvp\.sh --keep-logs/)
  assert.match(result.stdout, /\.\/verify-mvp\.sh -- --grep smoke-check/)
  assert.match(result.stdout, /^  verify-mvp\.cmd$/m)
  assert.match(result.stdout, /verify-mvp\.cmd --project chromium/)
  assert.match(result.stdout, /verify-mvp\.cmd --headed/)
  assert.match(result.stdout, /verify-mvp\.cmd --project chromium --grep smoke-check/)
  assert.match(result.stdout, /verify-mvp\.cmd --grep smoke-check/)
  assert.match(result.stdout, /verify-mvp\.cmd --keep-logs/)
  assert.match(result.stdout, /verify-mvp\.cmd --help/)
  assert.match(result.stdout, /verify-mvp\.cmd -- --grep smoke-check/)
  assert.match(result.stdout, /\[playwright-args\.\.\.\] Forward extra args appended after `playwright test`\./)
})

test('verify-mvp forwards passthrough arguments to Playwright', () => {
  const result = runVerifier(['--grep', 'smoke-check'], {
    VERIFY_MVP_ECHO_PLAYWRIGHT_COMMAND: '1'
  })

  assert.equal(result.status, 0)
  assert.match(result.stdout, /PLAYWRIGHT_COMMAND:/)
  assert.match(result.stdout, /\.\/node_modules\/.bin\/playwright test '--grep' 'smoke-check'/)
})

test('verify-mvp strips a standalone -- separator before forwarding Playwright arguments', () => {
  const result = runVerifier(['--', '--grep', 'smoke-check'], {
    VERIFY_MVP_ECHO_PLAYWRIGHT_COMMAND: '1'
  })

  assert.equal(result.status, 0)
  assert.match(result.stdout, /PLAYWRIGHT_COMMAND:/)
  assert.doesNotMatch(result.stdout, /playwright test '--' '--grep' 'smoke-check'/)
  assert.match(result.stdout, /\.\/node_modules\/.bin\/playwright test '--grep' 'smoke-check'/)
})

test('verify-mvp treats built-in flags after -- as Playwright arguments', () => {
  const result = runVerifier(['--', '--help'], {
    VERIFY_MVP_ECHO_PLAYWRIGHT_COMMAND: '1'
  })

  assert.equal(result.status, 0)
  assert.match(result.stdout, /PLAYWRIGHT_COMMAND:/)
  assert.match(result.stdout, /\.\/node_modules\/.bin\/playwright test '--help'/)
  assert.doesNotMatch(result.stdout, /^Usage:/)
})
