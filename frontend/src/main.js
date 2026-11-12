import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ko from 'element-plus/es/locale/lang/ko'

// 글꼴 파일은 앱에 넣는다. 화면에서 쓰는 4가지 굵기만 (05 1.7)
import '@fontsource/ibm-plex-sans-kr/400.css'
import '@fontsource/ibm-plex-sans-kr/500.css'
import '@fontsource/ibm-plex-sans-kr/600.css'
import '@fontsource/ibm-plex-sans-kr/700.css'

// 우리 색·글꼴 이름(05 1.6·1.7). Element Plus 스타일 뒤에 둬야 덮어쓴다 (ADR-034)
import './styles/tokens.css'
import './styles/element-theme.css'
import './styles/base.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: ko })

app.mount('#app')
