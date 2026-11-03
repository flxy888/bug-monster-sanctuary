import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import ElementPlus from 'element-plus'
import OperatorIdentity from '../src/components/OperatorIdentity.vue'
import SilverwolfTransition from '../src/components/SilverwolfTransition.vue'
import App from '../src/App.vue'
import { api } from '../src/api'
vi.mock('../src/api',()=>({api:Object.fromEntries(['projects','catalog','history','createProject','saveProject','scan','detail','stats','monsters','ast','health','deleteProject'].map(k=>[k,vi.fn()]))}))
vi.mock('../src/JavaEditor.vue',()=>({default:{name:'JavaEditor',props:['modelValue','snapshot','historyMode','disabled','visualTheme'],template:'<div class="editor-test-double" />'}}))
const Editor=defineComponent({name:'JavaEditor',props:['modelValue','snapshot','historyMode','disabled','visualTheme'],emits:['update:modelValue'],setup(_, {expose}){expose({clear(){},locate(){}});return{}},template:'<div class="editor-stub" />'})
let wrapper
beforeEach(()=>{localStorage.clear();vi.resetAllMocks();vi.stubGlobal('requestAnimationFrame',vi.fn(()=>1));vi.stubGlobal('cancelAnimationFrame',vi.fn())})
afterEach(()=>{wrapper?.unmount();localStorage.clear();document.body.innerHTML='';vi.unstubAllGlobals()})
describe('operator editing',()=>{
 it('validates blank names, cancels with Esc, and renders saved text safely',async()=>{
  wrapper=mount(OperatorIdentity,{props:{nickname:'实验员',theme:'default'}})
  await wrapper.get('.nickname-button').trigger('click');await wrapper.get('input').setValue('  ');await wrapper.get('input').trigger('keydown',{key:'Enter'})
  expect(wrapper.get('[role=alert]').text()).toContain('不能为空');expect(wrapper.emitted('save')).toBeUndefined()
  await wrapper.get('input').trigger('keydown',{key:'Escape'});expect(wrapper.find('input').exists()).toBe(false)
  await wrapper.get('.nickname-button').trigger('click');await wrapper.get('input').setValue('  导入员  ');await wrapper.get('input').trigger('keydown',{key:'Enter'})
  expect(wrapper.emitted('save')).toEqual([['导入员']])
  await wrapper.setProps({nickname:'<b>显示名</b>'});expect(wrapper.get('.nickname-button').text()).toContain('<b>显示名</b>');expect(wrapper.find('.nickname-button b').exists()).toBe(false)
 })
 it('does not commit Enter while a Chinese IME composition is active',async()=>{
  wrapper=mount(OperatorIdentity,{props:{nickname:'实验员',theme:'default'}})
  await wrapper.get('.nickname-button').trigger('click');const input=wrapper.get('input');await input.setValue('狼尊999')
  await input.trigger('compositionstart');await input.trigger('keydown',{key:'Enter',isComposing:true});expect(wrapper.emitted('save')).toBeUndefined()
  await input.trigger('compositionend');await input.trigger('keydown',{key:'Enter'});expect(wrapper.emitted('save')).toEqual([['狼尊999']])
 })
})
it('switches visual theme without remounting the editor, losing drafts, or calling write APIs',async()=>{
 const project={id:1002,name:'订单导入',sourceCode:'class Original {}',updatedAt:'2026-10-31T00:00:00Z'}
 api.projects.mockResolvedValue([project]);api.catalog.mockResolvedValue({enabledRuleCodes:[],definitions:[]});api.history.mockResolvedValue([{scanId:541,score:85}]);api.stats.mockResolvedValue({level:21,levelXp:20,totalXp:2020,foundCount:169,containedCount:101,containedTypes:5,enabledTypes:5});api.monsters.mockResolvedValue([]);api.health.mockResolvedValue({matchingPolicyVersion:'week3-match-v2'});api.ast.mockResolvedValue({scanId:541,status:'SUCCESS',root:null,nodesReturned:0});api.detail.mockResolvedValue({scanId:541,projectId:1002,sourceCode:project.sourceCode,status:'SUCCESS',score:85,issues:[],metadataSnapshot:{definitions:[]},createdAt:'2026-10-31T00:00:00Z',xpEarned:0})
 wrapper=mount(App,{global:{plugins:[ElementPlus],stubs:{JavaEditor:Editor}}});await flushPromises();const state=wrapper.vm.$.setupState
 state.selectProject(project);await state.openScan(541);await flushPromises();const editor=wrapper.findComponent(Editor).vm
 editor.$emit('update:modelValue','class UnsavedThemeDraft {}');await nextTick()
 state.saveOperatorName('狼尊999');await nextTick();expect(wrapper.findComponent(SilverwolfTransition).exists()).toBe(true)
 state.saveOperatorName('狼尊999');await nextTick();expect(wrapper.findAllComponents(SilverwolfTransition)).toHaveLength(1)
 wrapper.findComponent(SilverwolfTransition).vm.$emit('complete');await nextTick();expect(wrapper.get('main').classes()).toContain('silver-theme')
 expect(wrapper.findComponent(Editor).vm.$.uid).toBe(editor.$.uid);expect(wrapper.findComponent(Editor).props('modelValue')).toBe('class UnsavedThemeDraft {}');expect(state.result.scanId).toBe(541);expect(state.selectedId).toBe(1002);expect(wrapper.text()).toContain('总经验 2020 XP')
 state.restoreDefaultTheme();await nextTick();expect(wrapper.get('main').classes()).not.toContain('silver-theme');expect(wrapper.findComponent(Editor).vm.$.uid).toBe(editor.$.uid);expect(state.source).toBe('class UnsavedThemeDraft {}')
 for(const method of ['scan','saveProject','createProject','deleteProject'])expect(api[method]).not.toHaveBeenCalled()
})
