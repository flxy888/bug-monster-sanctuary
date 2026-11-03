<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as monaco from 'monaco-editor/editor/editor.api.js'
import 'monaco-editor/languages/definitions/java/register.js'
import 'monaco-editor/editor/browser/coreCommands.js'
import 'monaco-editor/editor/contrib/clipboard/browser/clipboard.js'
import 'monaco-editor/editor/contrib/find/browser/findController.js'
import 'monaco-editor/editor/contrib/links/browser/links.js'
import 'monaco-editor/editor/contrib/unicodeHighlighter/browser/unicodeHighlighter.js'
import 'monaco-editor/editor/contrib/readOnlyMessage/browser/contribution.js'
import { workerState } from './monacoRuntime'
const props = defineProps({ modelValue: { type: String, default: '' }, snapshot: { type: String, default: '' },
  historyMode: Boolean, disabled: Boolean, visualTheme: { type:String, default:'default' } })
const emit = defineEmits(['update:modelValue'])
const host = ref(null)
let editor, draftModel, historyModel, subscription, decorations
const applyVisualTheme=()=>monaco.editor.setTheme(props.visualTheme==='silverwolf'?'silver-lab':'vs-dark')
const clear = () => decorations?.clear()
onMounted(() => {
  monaco.editor.defineTheme('silver-lab',{base:'vs-dark',inherit:true,rules:[{token:'keyword',foreground:'C69BFF'},{token:'string',foreground:'A1E2D3'},{token:'comment',foreground:'8E7AA6'},{token:'number',foreground:'F2B8E7'},{token:'type.identifier',foreground:'CFC0FF'}],colors:{'editor.background':'#0B0E20','editor.foreground':'#E4DAF4','editorLineNumber.foreground':'#77648E','editorLineNumber.activeForeground':'#E5C5FF','editor.selectionBackground':'#713CA066','editor.inactiveSelectionBackground':'#4D315E55','editor.lineHighlightBackground':'#21162F','editorCursor.foreground':'#B4F7FF','editorIndentGuide.background1':'#30213E'}})
  draftModel = monaco.editor.createModel(props.modelValue, 'java')
  historyModel = monaco.editor.createModel(props.snapshot, 'java')
  editor = monaco.editor.create(host.value, { model: props.historyMode ? historyModel : draftModel,
    readOnly: props.historyMode || props.disabled, automaticLayout: true, minimap: { enabled: false },
    fontSize: 14, lineHeight: 22, scrollBeyondLastLine: false, tabSize: 4, theme: props.visualTheme==='silverwolf'?'silver-lab':'vs-dark', editContext: false,
    ariaLabel: props.historyMode ? '只读历史 Java 快照' : '完整 Java 源码草稿' })
  decorations = editor.createDecorationsCollection()
  subscription = draftModel.onDidChangeContent(() => {
    clear()
    if (draftModel.getValue() !== props.modelValue) emit('update:modelValue', draftModel.getValue())
  })
})
watch(() => props.visualTheme,()=>{if(editor)applyVisualTheme()})
watch(() => props.modelValue, value => { if (draftModel && draftModel.getValue() !== value) { clear(); draftModel.setValue(value) } })
watch(() => props.snapshot, value => { if (historyModel && historyModel.getValue() !== value) { clear(); historyModel.setValue(value) } })
watch(() => [props.historyMode, props.disabled], () => {
  if (!editor) return
  clear()
  editor.setModel(props.historyMode ? historyModel : draftModel)
  editor.updateOptions({ readOnly: props.historyMode || props.disabled,
    ariaLabel: props.historyMode ? '只读历史 Java 快照' : '完整 Java 源码草稿' })
})
function locate(startLine, endLine) {
  if (!editor) return
  const model = editor.getModel()
  const first = Math.max(1, Math.min(startLine, model.getLineCount()))
  const last = Math.max(first, Math.min(endLine, model.getLineCount()))
  const range = new monaco.Range(first, 1, last, model.getLineMaxColumn(last))
  decorations.set([{ range, options: { isWholeLine: true, className: 'issue-highlight', linesDecorationsClassName: 'issue-gutter' } }])
  editor.setSelection(range)
  editor.revealRangeInCenter(range)
  host.value.scrollIntoView({ block: 'center' })
  editor.focus()
}
onBeforeUnmount(() => { subscription?.dispose(); decorations?.clear(); editor?.dispose(); draftModel?.dispose(); historyModel?.dispose() })
defineExpose({ locate, clear })
</script>

<template><div ref="host" class="java-editor" :data-mode="historyMode ? 'history' : 'draft'" data-language="java" :data-worker-state="workerState"></div></template>
