import { defineConfig } from '@playwright/test'

const disableWebServer = process.env.PLAYWRIGHT_DISABLE_WEBSERVER === '1'

export default defineConfig({
  testDir: './tests/e2e',
  use: {
    baseURL: 'http://127.0.0.1:4174',
    headless: true
  },
  webServer: disableWebServer
    ? undefined
    : [
        {
          command:
            'mvn -f ../pom.xml -pl backend spring-boot:run -Dspring-boot.run.arguments=--server.port=18080',
          port: 18080,
          reuseExistingServer: false,
          timeout: 120000
        },
        {
          command: 'corepack pnpm dev --host 127.0.0.1 --port 4174 --strictPort',
          port: 4174,
          reuseExistingServer: false,
          timeout: 120000,
          env: {
            VITE_API_PROXY_TARGET: 'http://127.0.0.1:18080'
          }
        }
      ]
})

