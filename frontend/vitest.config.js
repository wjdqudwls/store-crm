import { fileURLToPath } from 'node:url'
import process from 'node:process'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.js'

// 매장 화면은 한국 시간 브라우저를 기준으로 날짜를 보여준다. CI(UTC)에서도 같은 결과가 나오게 맞춘다
process.env.TZ = 'Asia/Seoul'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
    },
  }),
)
