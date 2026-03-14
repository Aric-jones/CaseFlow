import { createApp } from 'vue';
import { createPinia } from 'pinia';
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
// @ts-ignore
import zhCn from 'element-plus/dist/locale/zh-cn.mjs';
import * as ElIcons from '@element-plus/icons-vue';
import App from './App.vue';
import router from './router';
import './style.css';

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(Antd);
app.use(ElementPlus, { locale: zhCn });

// 全局注册所有 Element Plus 图标
for (const [name, comp] of Object.entries(ElIcons)) {
  app.component(name, comp);
}

app.mount('#app');
