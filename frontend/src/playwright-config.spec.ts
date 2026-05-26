import { afterEach, describe, expect, it, vi } from 'vitest'
import config from '../playwright.config'

describe('playwright config', () => {
  afterEach(() => {
    delete process.env.PLAYWRIGHT_DISABLE_WEBSERVER
    vi.resetModules()
  })

  it('会为真实接口 e2e 在隔离端口启动新的前后端服务', () => {
    expect(Array.isArray(config.webServer)).toBe(true)
    expect(config.webServer).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          command:
            'mvn -f ../pom.xml -pl backend spring-boot:run -Dspring-boot.run.arguments=--server.port=18080',
          port: 18080,
          reuseExistingServer: false
        }),
        expect.objectContaining({
          command: 'corepack pnpm dev --host 127.0.0.1 --port 4174 --strictPort',
          port: 4174,
          reuseExistingServer: false,
          env: expect.objectContaining({
            VITE_API_PROXY_TARGET: 'http://127.0.0.1:18080'
          })
        })
      ])
    )
  })

  it('允许在本地手动启动前后端时关闭 Playwright webServer', async () => {
    process.env.PLAYWRIGHT_DISABLE_WEBSERVER = '1'
    vi.resetModules()

    const { default: overriddenConfig } = await import('../playwright.config')

    expect(overriddenConfig.webServer).toBeUndefined()
  })
})
