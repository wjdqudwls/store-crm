import { setup } from '@storybook/vue3-vite'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ko from 'element-plus/es/locale/lang/ko'

// 앱(src/main.js)과 같은 글꼴·색 토큰·Element Plus 덮어쓰기 순서 (05 1.6·1.7, ADR-034)
import '@fontsource/ibm-plex-sans-kr/400.css'
import '@fontsource/ibm-plex-sans-kr/500.css'
import '@fontsource/ibm-plex-sans-kr/600.css'
import '@fontsource/ibm-plex-sans-kr/700.css'
import '../src/styles/tokens.css'
import '../src/styles/element-theme.css'
import '../src/styles/base.css'

setup((app) => {
  app.use(createPinia())
  app.use(ElementPlus, { locale: ko })
})

export default {
  parameters: {
    layout: 'padded',
    backgrounds: { options: { app: { name: '앱 배경', value: '#f5f6f8' } } },
  },
  initialGlobals: { backgrounds: { value: 'app' } },
}
