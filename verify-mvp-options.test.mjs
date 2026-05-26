import test from 'node:test'
import assert from 'node:assert/strict'

import { parseVerifyMvpOptions, verifyMvpHelpText } from './verify-mvp-options.mjs'

test('parseVerifyMvpOptions enables keepLogs for the keep-logs flag', () => {
  assert.deepEqual(parseVerifyMvpOptions(['--keep-logs']), {
    help: false,
    keepLogs: true,
    passthroughArgs: []
  })
})

test('parseVerifyMvpOptions enables help for the help flag', () => {
  assert.deepEqual(parseVerifyMvpOptions(['--help']), {
    help: true,
    keepLogs: false,
    passthroughArgs: []
  })
})

test('parseVerifyMvpOptions preserves unknown arguments for passthrough', () => {
  assert.deepEqual(parseVerifyMvpOptions(['--keep-logs', '--sample', 'value']), {
    help: false,
    keepLogs: true,
    passthroughArgs: ['--sample', 'value']
  })
})

test('parseVerifyMvpOptions strips a standalone -- separator before passthrough args', () => {
  assert.deepEqual(parseVerifyMvpOptions(['--keep-logs', '--', '--grep', 'smoke-check']), {
    help: false,
    keepLogs: true,
    passthroughArgs: ['--grep', 'smoke-check']
  })
})

test('parseVerifyMvpOptions stops parsing built-in flags after a standalone -- separator', () => {
  assert.deepEqual(parseVerifyMvpOptions(['--', '--help', '--keep-logs']), {
    help: false,
    keepLogs: false,
    passthroughArgs: ['--help', '--keep-logs']
  })
})

test('verifyMvpHelpText describes the main entrypoints and flags', () => {
  assert.match(verifyMvpHelpText, /node \.\/verify-mvp\.mjs/)
  assert.match(verifyMvpHelpText, /--keep-logs/)
  assert.match(verifyMvpHelpText, /--help/)
  assert.match(verifyMvpHelpText, /--grep smoke-check/)
  assert.match(verifyMvpHelpText, /node \.\/verify-mvp\.mjs -- --grep smoke-check/)
  assert.match(verifyMvpHelpText, /node \.\/verify-mvp\.mjs --project chromium --headed/)
  assert.match(verifyMvpHelpText, /node \.\/verify-mvp\.mjs --project chromium --keep-logs/)
  assert.match(verifyMvpHelpText, /\.\/verify-mvp\.sh --help/)
  assert.match(verifyMvpHelpText, /\.\/verify-mvp\.sh --project chromium --keep-logs/)
  assert.match(verifyMvpHelpText, /node \.\/verify-mvp\.mjs --project chromium --grep smoke-check/)
  assert.match(verifyMvpHelpText, /node \.\/verify-mvp\.mjs --headed/)
  assert.match(verifyMvpHelpText, /node \.\/verify-mvp\.mjs --project chromium --headed/)
  assert.match(verifyMvpHelpText, /\.\/verify-mvp\.sh --project chromium --headed/)
  assert.match(verifyMvpHelpText, /\.\/verify-mvp\.sh -- --grep smoke-check/)
  assert.match(verifyMvpHelpText, /verify-mvp\.cmd -- --grep smoke-check/)
  assert.match(verifyMvpHelpText, /verify-mvp\.cmd --headed/)
  assert.match(verifyMvpHelpText, /verify-mvp\.cmd --project chromium --headed/)
  assert.match(verifyMvpHelpText, /verify-mvp\.cmd --keep-logs/)
  assert.match(verifyMvpHelpText, /^  verify-mvp\.cmd$/m)
  assert.match(verifyMvpHelpText, /verify-mvp\.cmd --project chromium --keep-logs/)
  assert.match(verifyMvpHelpText, /appended after `playwright test`/)
})
