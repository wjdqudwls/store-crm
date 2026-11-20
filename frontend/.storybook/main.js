/**
 * Storybook 시범 도입 (화면 컴포넌트를 따로 띄워 보기). 앱 빌드·CI와 무관하다.
 * 실행: npm run storybook → http://localhost:6006
 */
export default {
  stories: ['../src/**/*.stories.js'],
  framework: '@storybook/vue3-vite',
  addons: [],
  core: { disableTelemetry: true }, // 사용 통계를 보내지 않는다
  // 앱 전용 플러그인(Vue DevTools)은 Storybook에서 빼고, 나머지 Vite 설정(별칭 '@' 등)은 그대로 쓴다
  viteFinal: (config) => {
    const withoutDevTools = (list) =>
      (list ?? []).flat(Infinity).filter((p) => p && !String(p.name ?? '').includes('devtools'))
    return { ...config, plugins: withoutDevTools(config.plugins) }
  },
}
