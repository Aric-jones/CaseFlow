<template>
  <div ref="editorRef" class="code-editor-wrap" :style="{ minHeight: minHeight + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { EditorState } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { syntaxHighlighting, defaultHighlightStyle, bracketMatching, foldGutter } from '@codemirror/language';
import { json } from '@codemirror/lang-json';
import { javascript } from '@codemirror/lang-javascript';

const props = withDefaults(defineProps<{
  modelValue?: string;
  language?: 'json' | 'groovy' | 'javascript';
  readonly?: boolean;
  minHeight?: number;
  placeholder?: string;
}>(), {
  modelValue: '',
  language: 'json',
  readonly: false,
  minHeight: 200,
  placeholder: '',
});

const emit = defineEmits<{ (e: 'update:modelValue', val: string): void }>();
const editorRef = ref<HTMLElement>();
let view: EditorView | null = null;
let isUpdatingFromProp = false;

function getLangExtension() {
  if (props.language === 'json') return json();
  return javascript();
}

function createEditor() {
  if (!editorRef.value) return;
  const extensions = [
    lineNumbers(),
    highlightActiveLine(),
    highlightActiveLineGutter(),
    foldGutter(),
    history(),
    bracketMatching(),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    keymap.of([...defaultKeymap, ...historyKeymap]),
    getLangExtension(),
    EditorView.updateListener.of((update) => {
      if (update.docChanged && !isUpdatingFromProp) {
        emit('update:modelValue', update.state.doc.toString());
      }
    }),
    EditorView.theme({
      '&': { fontSize: '13px', fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace" },
      '.cm-content': { minHeight: props.minHeight + 'px', padding: '8px 0' },
      '.cm-gutters': { background: '#f8f9fa', borderRight: '1px solid #e8e8e8', color: '#999' },
      '.cm-activeLineGutter': { background: '#e8f0fe' },
      '.cm-activeLine': { background: '#f5f7fa' },
      '&.cm-focused': { outline: '1px solid #409eff' },
      '.cm-scroller': { overflow: 'auto' },
    }),
    EditorState.readOnly.of(props.readonly),
  ];

  if (props.placeholder) {
    extensions.push(EditorView.contentAttributes.of({ 'aria-placeholder': props.placeholder }));
  }

  const state = EditorState.create({ doc: props.modelValue || '', extensions });
  view = new EditorView({ state, parent: editorRef.value });
}

watch(() => props.modelValue, (val) => {
  if (!view) return;
  const current = view.state.doc.toString();
  if (val !== current) {
    isUpdatingFromProp = true;
    view.dispatch({ changes: { from: 0, to: current.length, insert: val || '' } });
    isUpdatingFromProp = false;
  }
});

onMounted(createEditor);
onUnmounted(() => { view?.destroy(); view = null; });
</script>

<style scoped>
.code-editor-wrap { border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; }
.code-editor-wrap:hover { border-color: #c0c4cc; }
</style>
