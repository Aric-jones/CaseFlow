/// <reference types="vite/client" />
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
declare module 'simple-mind-map';
declare module 'simple-mind-map/src/plugins/Export';
declare module 'simple-mind-map/src/plugins/Select';
declare module 'simple-mind-map/src/plugins/Drag';
declare module 'simple-mind-map/src/plugins/RichText';
declare module 'simple-mind-map/src/plugins/Scrollbar';
