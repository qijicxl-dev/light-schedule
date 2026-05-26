export const verifyMvpHelpText = `Usage:
  node ./verify-mvp.mjs [--keep-logs] [--help] [playwright-args...]

Common commands:
  node ./verify-mvp.mjs
  node ./verify-mvp.mjs --keep-logs
  node ./verify-mvp.mjs --help
  node ./verify-mvp.mjs -- --grep smoke-check
  node ./verify-mvp.mjs --grep smoke-check
  node ./verify-mvp.mjs --headed
  node ./verify-mvp.mjs --project chromium --headed
  node ./verify-mvp.mjs --project chromium --keep-logs
  node ./verify-mvp.mjs --project chromium --grep smoke-check
  ./verify-mvp.sh --keep-logs
  ./verify-mvp.sh --help
  ./verify-mvp.sh -- --grep smoke-check
  ./verify-mvp.sh --grep smoke-check
  ./verify-mvp.sh --headed
  ./verify-mvp.sh --project chromium --headed
  ./verify-mvp.sh --project chromium --keep-logs
  ./verify-mvp.sh --project chromium --grep smoke-check
  verify-mvp.cmd
  verify-mvp.cmd --keep-logs
  verify-mvp.cmd --help
  verify-mvp.cmd -- --grep smoke-check
  verify-mvp.cmd --grep smoke-check
  verify-mvp.cmd --project chromium --keep-logs
  verify-mvp.cmd --project chromium
  verify-mvp.cmd --headed
  verify-mvp.cmd --project chromium --headed
  verify-mvp.cmd --project chromium --grep smoke-check

Options:
  --keep-logs          Keep backend and frontend startup logs after a successful run.
  --help               Show this help message and exit.
  [playwright-args...] Forward extra args appended after \`playwright test\`.
`

export function parseVerifyMvpOptions(args) {
  const passthroughArgs = []
  let help = false
  let keepLogs = false
  let passthroughOnly = false

  for (const arg of args) {
    if (passthroughOnly) {
      passthroughArgs.push(arg)
      continue
    }

    if (arg === '--') {
      passthroughOnly = true
      continue
    }

    if (arg === '--help') {
      help = true
      continue
    }

    if (arg === '--keep-logs') {
      keepLogs = true
      continue
    }

    passthroughArgs.push(arg)
  }

  return {
    help,
    keepLogs,
    passthroughArgs
  }
}
