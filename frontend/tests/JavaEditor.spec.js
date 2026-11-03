import { mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import JavaEditor from '../src/JavaEditor.vue'

const state = vi.hoisted(() => ({ models:[],editors:[] }))
vi.mock('../src/monacoRuntime',() => ({workerState:'ready'}))
vi.mock('monaco-editor/editor/editor.api.js',() => ({
  Range: class { constructor(startLineNumber,startColumn,endLineNumber,endColumn) { Object.assign(this,{startLineNumber,startColumn,endLineNumber,endColumn}) } },
  editor: {
    defineTheme:vi.fn(),setTheme:vi.fn(),
    createModel(value) {
      const model={value,getValue(){return this.value},setValue(value){this.value=value},getLineCount:()=>10,getLineMaxColumn:()=>20,
        dispose:vi.fn(),onDidChangeContent:vi.fn(()=>({dispose:vi.fn()}))}; state.models.push(model);return model
    },
    create(_,options) {
      const instance={model:options.model,getModel(){return this.model},setModel(model){this.model=model},updateOptions:vi.fn(),
        setSelection:vi.fn(),revealRangeInCenter:vi.fn(),focus:vi.fn(),dispose:vi.fn(),
        createDecorationsCollection:()=>({set:vi.fn(),clear:vi.fn()})};state.editors.push(instance);return instance
    },
  },
}))
// Side-effect contributions are integration-tested in the browser, not executed by jsdom.
vi.mock('monaco-editor/languages/definitions/java/register.js',()=>({}))
vi.mock('monaco-editor/editor/browser/coreCommands.js',()=>({}))
vi.mock('monaco-editor/editor/contrib/clipboard/browser/clipboard.js',()=>({}))
vi.mock('monaco-editor/editor/contrib/find/browser/findController.js',()=>({}))
vi.mock('monaco-editor/editor/contrib/links/browser/links.js',()=>({}))
vi.mock('monaco-editor/editor/contrib/unicodeHighlighter/browser/unicodeHighlighter.js',()=>({}))
vi.mock('monaco-editor/editor/contrib/readOnlyMessage/browser/contribution.js',()=>({}))
afterEach(()=>{state.models=[];state.editors=[]})
it('keeps draft separate from historical model, selects range, and disposes both on unmount',async()=>{
  Element.prototype.scrollIntoView=vi.fn()
  const wrapper=mount(JavaEditor,{props:{modelValue:'class Draft{}',snapshot:'class Old{}',historyMode:false}})
  const [draft,old]=state.models, editor=state.editors[0]
  expect(editor.model).toBe(draft)
  await wrapper.setProps({historyMode:true}); expect(editor.model).toBe(old)
  expect(editor.updateOptions).toHaveBeenLastCalledWith(expect.objectContaining({readOnly:true}))
  wrapper.vm.locate(4,6);expect(editor.setSelection).toHaveBeenLastCalledWith(expect.objectContaining({startLineNumber:4,endLineNumber:6}))
  await wrapper.setProps({snapshot:'class Other{}'}); expect(draft.getValue()).toBe('class Draft{}')
  await wrapper.setProps({historyMode:false});expect(editor.model).toBe(draft)
  await wrapper.setProps({visualTheme:'silverwolf'});expect(state.models).toHaveLength(2);expect(state.editors).toHaveLength(1);expect(editor.model).toBe(draft)
  await wrapper.setProps({visualTheme:'default'});expect(draft.getValue()).toBe('class Draft{}');expect(editor.model).toBe(draft)
  wrapper.unmount();expect(editor.dispose).toHaveBeenCalledOnce();expect(draft.dispose).toHaveBeenCalledOnce();expect(old.dispose).toHaveBeenCalledOnce()
})
