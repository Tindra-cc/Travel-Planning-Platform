import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      root: fileURLToPath(new URL('./', import.meta.url)),
      // 让 Vite 处理 Element Plus：外部化交给 Node ESM 时，其内对 async-validator 的
      // CJS 默认导入会得到 { default: Schema }，表单校验会静默卡在 validating。
      server: {
        deps: {
          inline: ['element-plus', 'async-validator'],
        },
      },
    },
  }),
)
