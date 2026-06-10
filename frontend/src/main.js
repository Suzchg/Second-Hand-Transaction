import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import './dark.css'
import App from './App.vue'
import router from './router'
import { vLazyBg } from './lazyBg.js'
import AppIcon from './components/AppIcon.vue'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('lazy-bg', vLazyBg)
app.component('AppIcon', AppIcon)
app.mount('#app')
