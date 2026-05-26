// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
})

describe('vite config', () => {
  it('默认把前端 /api 代理到本地 8080 后端', async () => {
    const { default: config } = await import('../vite.config')

    expect(config.server?.proxy).toEqual(
      expect.objectContaining({
        '/api': expect.objectContaining({
          target: 'http://127.0.0.1:8080'
        })
      })
    )
  })

  it('会在 e2e 指定代理目标时切到隔离后的后端端口', async () => {
    vi.stubEnv('VITE_API_PROXY_TARGET', 'http://127.0.0.1:18080')
    const { default: config } = await import('../vite.config')

    expect(config.server?.proxy).toEqual(
      expect.objectContaining({
        '/api': expect.objectContaining({
          target: 'http://127.0.0.1:18080'
        })
      })
    )
  })
})
