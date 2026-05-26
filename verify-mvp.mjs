#!/usr/bin/env node
import http from 'node:http'
import https from 'node:https'
import { spawn } from 'node:child_process'
import { createWriteStream, mkdtempSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

import { parseVerifyMvpOptions, verifyMvpHelpText } from './verify-mvp-options.mjs'

const options = parseVerifyMvpOptions(process.argv.slice(2))
const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const frontendDir = path.join(scriptDir, 'frontend')
const backendPom = path.join(scriptDir, 'pom.xml')
const logsDir = mkdtempSync(path.join(os.tmpdir(), 'verify-mvp-'))
const backendLog = path.join(logsDir, 'backend.log')
const frontendLog = path.join(logsDir, 'frontend.log')

let startedBackend = null
let startedFrontend = null

function toBashPath(filePath) {
  const normalized = filePath.replace(/\\/g, '/')
  if (/^[A-Za-z]:\//.test(normalized)) {
    return `/${normalized[0].toLowerCase()}${normalized.slice(2)}`
  }
  return normalized
}

function quoteBash(value) {
  return `'${value.replace(/'/g, `'\\''`)}'`
}

function runBash(command, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn('bash', ['-lc', command], {
      cwd: scriptDir,
      stdio: options.stdio ?? 'inherit'
    })

    child.on('error', reject)
    child.on('exit', (code, signal) => {
      if (signal) {
        reject(new Error(`Command terminated by signal ${signal}`))
        return
      }
      if (code === 0) {
        resolve(child)
        return
      }
      reject(new Error(`Command failed with exit code ${code}`))
    })
  })
}

function joinBashArgs(args) {
  return args.map((arg) => quoteBash(arg)).join(' ')
}

function buildPlaywrightCommand(frontendDirBash, passthroughArgs = []) {
  const args = passthroughArgs.length > 0 ? ` ${joinBashArgs(passthroughArgs)}` : ''
  return `cd ${frontendDirBash} && export PLAYWRIGHT_DISABLE_WEBSERVER=1 && exec ./node_modules/.bin/playwright test${args}`
}

function startLoggedBash(command, logPath) {
  const logStream = createWriteStream(logPath, { flags: 'w' })
  const child = spawn('bash', ['-lc', command], {
    cwd: scriptDir,
    stdio: ['ignore', 'pipe', 'pipe']
  })

  child.stdout.pipe(logStream)
  child.stderr.pipe(logStream)

  return child
}

function isUrlReady(url) {
  return new Promise((resolve) => {
    const client = url.startsWith('https:') ? https : http
    const request = client.get(url, (response) => {
      response.resume()
      resolve(Boolean(response.statusCode && response.statusCode >= 200 && response.statusCode < 300))
    })

    request.on('error', () => resolve(false))
    request.setTimeout(2000, () => {
      request.destroy()
      resolve(false)
    })
  })
}

async function waitForUrl(url, label, child) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    if (await isUrlReady(url)) {
      return
    }
    if (child?.exitCode != null) {
      throw new Error(`${label} process exited before becoming ready`)
    }
    await delay(1000)
  }

  throw new Error(`Timed out waiting for ${label} at ${url}`)
}

function stopChild(child) {
  if (!child || child.exitCode != null) {
    return Promise.resolve()
  }

  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      if (child.exitCode == null) {
        child.kill('SIGKILL')
      }
    }, 5000)

    child.once('exit', () => {
      clearTimeout(timer)
      resolve()
    })

    child.kill('SIGTERM')
  })
}

async function cleanup(success, options) {
  await stopChild(startedFrontend)
  await stopChild(startedBackend)

  if (success && !options.keepLogs) {
    rmSync(logsDir, { recursive: true, force: true })
    return
  }

  const logs = []
  if (startedBackend) {
    logs.push(`- backend: ${backendLog}`)
  }
  if (startedFrontend) {
    logs.push(`- frontend: ${frontendLog}`)
  }

  if (logs.length > 0) {
    console.error(`\nVerification failed. Logs kept at:\n${logs.join('\n')}`)
  }
}

async function main() {
  const backendPomBash = quoteBash(toBashPath(backendPom))
  const frontendDirBash = quoteBash(toBashPath(frontendDir))

  console.log('==> Running backend verify')
  await runBash(`exec mvn -f ${backendPomBash} -pl backend -DforkCount=0 -Dexec.skip=true verify`)

  console.log('==> Ensuring backend server on 18080')
  if (await isUrlReady('http://127.0.0.1:18080/api/task-pool')) {
    console.log('Reusing backend server at http://127.0.0.1:18080')
  } else {
    startedBackend = startLoggedBash(
      `exec mvn -f ${backendPomBash} -pl backend spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`,
      backendLog
    )
    await waitForUrl('http://127.0.0.1:18080/api/task-pool', 'backend', startedBackend)
  }

  console.log('==> Ensuring frontend server on 4174')
  if (await isUrlReady('http://127.0.0.1:4174/api/task-pool')) {
    console.log('Reusing frontend server at http://127.0.0.1:4174')
  } else {
    startedFrontend = startLoggedBash(
      `cd ${frontendDirBash} && export VITE_API_PROXY_TARGET='http://127.0.0.1:18080' && exec ./node_modules/.bin/vite --host 127.0.0.1 --port 4174 --strictPort`,
      frontendLog
    )
    await waitForUrl('http://127.0.0.1:4174/api/task-pool', 'frontend', startedFrontend)
  }

  console.log('==> Running frontend unit tests')
  await runBash(`cd ${frontendDirBash} && exec ./node_modules/.bin/vitest --config ./vite.config.ts run`)

  const playwrightCommand = buildPlaywrightCommand(frontendDirBash, options.passthroughArgs)

  if (process.env.VERIFY_MVP_ECHO_PLAYWRIGHT_COMMAND === '1') {
    console.log(`PLAYWRIGHT_COMMAND:${playwrightCommand}`)
    return
  }

  console.log('==> Running frontend e2e tests')
  await runBash(playwrightCommand)

  console.log('==> Running frontend typecheck')
  await runBash(`cd ${frontendDirBash} && exec ./node_modules/.bin/vue-tsc --noEmit`)

  console.log('==> Running frontend build')
  await runBash(`cd ${frontendDirBash} && exec ./node_modules/.bin/vite build`)

  if (options.keepLogs) {
    console.log(`\nLogs kept at:\n- backend: ${backendLog}\n- frontend: ${frontendLog}`)
  }

  console.log('\nMVP verification passed.')
}

let success = false

if (options.help) {
  console.log(verifyMvpHelpText)
  success = true
} else {
  try {
    await main()
    success = true
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error))
    process.exitCode = 1
  } finally {
    await cleanup(success, options)
  }
}
