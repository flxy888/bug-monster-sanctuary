import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import ElementPlus from 'element-plus'
import App from '../src/App.vue'
import { api } from '../src/api'

vi.mock('../src/api', () => ({ api: Object.fromEntries(['projects','catalog','history','createProject','saveProject','scan','detail','stats','monsters','ast','health','deleteProject'].map(k => [k, vi.fn()])) }))
vi.mock('../src/JavaEditor.vue', () => ({ default: { name: 'JavaEditor', props: ['modelValue','snapshot','historyMode','disabled'], template: '<div class="editor-test-double" />' } }))
const pending = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); return {promise,resolve,reject} }
const project = (id=1,sourceCode='class A {}') => ({ id,name:'项目'+id,sourceCode,updatedAt:'2026-10-26T00:00:00Z' })
const scanResult = (id=10,sourceCode='class A {}') => ({ scanId:id,projectId:1,sourceCode,score:92,status:'SUCCESS',rulesetVersion:'week2-v1',enabledRuleCodes:['EMPTY_IF'],metadataSnapshot:{},parseProblems:[],createdAt:'2026-10-26T00:00:00Z',issues:[{issueKey:'x',ruleCode:'EMPTY_IF',monsterName:'API空壳',startLine:2,endLine:4,message:'API说明',suggestion:'API建议'}] })
const locate = vi.fn(), clear = vi.fn()
const Editor = defineComponent({ name:'JavaEditor', props:['modelValue','snapshot','historyMode','disabled'],emits:['update:modelValue'],setup(_, { expose }) { expose({ locate, clear }); return {} },template:'<div class="editor-stub" />' })
let wrapper
const button = text => wrapper.findAll('button').find(b => b.text() === text)
const select = async id => { await wrapper.findAll('.project-item').find(b => b.text().includes('#'+id)).trigger('click'); await flushPromises() }
const edit = async text => { wrapper.findComponent(Editor).vm.$emit('update:modelValue',text); await nextTick() }
async function start() { wrapper=mount(App,{global:{plugins:[ElementPlus],stubs:{JavaEditor:Editor}}}); await flushPromises() }
beforeEach(() => {
  vi.resetAllMocks(); api.ast.mockImplementation(id=>Promise.resolve({scanId:id,status:'SUCCESS',root:null,nodesReturned:0,parseProblems:[]})); api.health.mockResolvedValue({matchingPolicyVersion:'week3-match-v2'}); api.projects.mockResolvedValue([project(),project(2)]); api.history.mockResolvedValue([])
  api.stats.mockResolvedValue({totalXp:0,level:1,levelXp:0,xpPerLevel:100,xpToNextLevel:100,foundCount:0,containedCount:0,containedTypes:0,enabledTypes:5})
  api.monsters.mockResolvedValue([{ruleCode:'EMPTY_IF',monsterName:'API空壳',description:'API描述',state:'UNDISCOVERED',foundCount:0,containedCount:0,enabled:true}])
  api.catalog.mockResolvedValue({rulesetVersion:'week2-v1',enabledRuleCodes:['EMPTY_IF'],definitions:[{ruleCode:'EMPTY_IF',monsterName:'API空壳',description:'API口径',suggestion:'API建议',deduction:8,enabled:true}]})
})
afterEach(() => { wrapper?.unmount(); document.body.innerHTML='' })
describe('actual App component async ordering and snapshot routing', () => {
  it('ignores a list refresh arriving after create succeeds', async () => {
    await start(); const late=pending(); api.projects.mockReturnValueOnce(late.promise)
    await button('刷新项目').trigger('click')
    expect(wrapper.text()).toContain('正在加载项目')
    api.createProject.mockResolvedValue(project(3)); wrapper.vm.$.setupState.newName='新项目'; await wrapper.vm.$.setupState.createProject(); await flushPromises()
    late.resolve([project()]); await flushPromises()
    expect(wrapper.findAll('.project-item')).toHaveLength(3); expect(wrapper.find('.project-item.active').text()).toContain('#3')
  })
  it('ignores a list refresh arriving after save and reselects the saved source', async () => {
    await start(); await select(1); const late=pending(); api.projects.mockReturnValueOnce(late.promise)
    await button('刷新项目').trigger('click'); await edit('class Saved {}'); api.saveProject.mockResolvedValue(project(1,'class Saved {}'))
    await button('保存草稿').trigger('click'); await flushPromises(); late.resolve([project(),project(2)]); await flushPromises()
    expect(wrapper.vm.$.setupState.projects.find(p => p.id === 1).sourceCode).toBe('class Saved {}')
    await select(2); await select(1); expect(wrapper.findComponent(Editor).props('modelValue')).toBe('class Saved {}')
    expect(wrapper.text()).toContain('已与数据库草稿同步')
  })
  it('locates current source, then readonly snapshot after edits without losing draft', async () => {
    await start(); await select(1); const original='class A {\nvoid m(){\nif(true){}\n}\n}'
    await edit(original); api.scan.mockResolvedValue(scanResult(10,original)); await button('提交扫描').trigger('click'); await flushPromises()
    await wrapper.find('.issue').trigger('click'); expect(locate).toHaveBeenLastCalledWith(2,4); expect(wrapper.findComponent(Editor).props('historyMode')).toBe(false)
    await edit(original+'\n// unsaved draft'); await wrapper.find('.issue').trigger('keydown',{key:'Enter'}); await flushPromises()
    expect(wrapper.findComponent(Editor).props('historyMode')).toBe(true); expect(wrapper.findComponent(Editor).props('snapshot')).toBe(original)
    expect(wrapper.text()).toContain('只读历史快照'); await button('返回草稿').trigger('click')
    expect(wrapper.findComponent(Editor).props('modelValue')).toBe(original+'\n// unsaved draft'); expect(wrapper.findComponent(Editor).props('historyMode')).toBe(false)
    await select(2); await select(1); expect(wrapper.findComponent(Editor).props('modelValue')).toBe(original+'\n// unsaved draft')
  })
  it('rejects stale scan, save and history responses after switching project', async () => {
    await start(); await select(1); const late=pending(); api.scan.mockReturnValue(late.promise)
    await button('提交扫描').trigger('click'); await select(2); late.resolve(scanResult()); await flushPromises()
    expect(wrapper.text()).not.toContain('API说明'); expect(wrapper.find('.project-item.active').text()).toContain('#2')
    const saved=pending(); api.saveProject.mockReturnValue(saved.promise); await button('保存草稿').trigger('click'); await select(1)
    saved.resolve(project(2,'class OldSave {}')); await flushPromises(); expect(wrapper.findComponent(Editor).props('modelValue')).toBe('class A {}')
    const hist=pending(); api.history.mockReturnValueOnce(hist.promise); await select(2); await select(1); hist.resolve([{scanId:999,score:0}]); await flushPromises()
    expect(wrapper.findAll('.history-item')).toHaveLength(0)
  })
  it('rejects old scan detail and older detail ordering', async () => {
    await start(); await select(1); const a=pending(),b=pending(); api.detail.mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise)
    wrapper.vm.$.setupState.openScan(10); wrapper.vm.$.setupState.openScan(11); b.resolve(scanResult(11)); await flushPromises(); a.resolve(scanResult(10)); await flushPromises()
    expect(wrapper.text()).toContain('扫描 #11'); expect(wrapper.text()).not.toContain('扫描 #10')
    const c=pending(); api.detail.mockReturnValueOnce(c.promise); wrapper.vm.$.setupState.openScan(12); await select(2); c.resolve(scanResult(12)); await flushPromises()
    expect(wrapper.findAll('.issue')).toHaveLength(0)
  })
  it('does not show a late scan failure over a newer historical result', async () => {
    await start(); await select(1); const oldScan=pending(); api.scan.mockReturnValueOnce(oldScan.promise)
    await button('提交扫描').trigger('click'); api.detail.mockResolvedValueOnce(scanResult(22))
    await wrapper.vm.$.setupState.openScan(22); await flushPromises(); oldScan.reject(new Error('旧扫描错误')); await flushPromises()
    expect(wrapper.text()).toContain('扫描 #22'); expect(wrapper.text()).not.toContain('旧扫描错误')
  })
  it('renders catalog failures, parse failure, empty result and request failure', async () => {
    api.catalog.mockRejectedValueOnce(new Error('目录断开')); await start(); expect(wrapper.text()).toContain('目录断开')
    await button('重试目录').trigger('click'); await flushPromises(); await select(1); expect(wrapper.text()).toContain('API口径')
    api.scan.mockResolvedValue({...scanResult(),score:null,status:'PARSE_ERROR',issues:[],parseProblems:[{message:'unexpected token',startLine:1,startColumn:4}]})
    await button('提交扫描').trigger('click'); await flushPromises(); expect(wrapper.text()).toContain('unexpected token')
    api.scan.mockResolvedValue({...scanResult(),score:100,issues:[]}); await button('提交扫描').trigger('click'); await flushPromises(); expect(wrapper.text()).toContain('本次启用规则未发现问题')
    api.scan.mockRejectedValue(new Error('接口断开')); await button('提交扫描').trigger('click'); await flushPromises(); expect(wrapper.text()).toContain('接口断开')
  })
  it('shares one verification request and refreshes current growth from the backend', async () => {
    await start(); await select(1)
    api.detail.mockResolvedValue({...scanResult(),comparisonStatus:'BASELINE_CREATED',xpEarned:0,resolutions:[],resolvedCount:0,containedCount:0,removedCount:0,ambiguousCount:0})
    await wrapper.vm.$.setupState.openScan(10); await edit('class A {void m(){work();}}')
    const verification=pending(); api.scan.mockReturnValueOnce(verification.promise)
    wrapper.vm.$.setupState.scan(); wrapper.vm.$.setupState.scan()
    expect(api.scan).toHaveBeenCalledTimes(1)
    api.stats.mockResolvedValue({totalXp:20,level:1,levelXp:20,xpPerLevel:100,xpToNextLevel:80,foundCount:1,containedCount:1,containedTypes:1,enabledTypes:5})
    verification.resolve({...scanResult(11),issues:[],score:100,comparisonStatus:'COMPARED',xpEarned:20,resolvedCount:1,containedCount:1,removedCount:0,ambiguousCount:0,resolutions:[{lineageKey:'stable',ruleCode:'EMPTY_IF',resolution:'CONFIRMED_FIXED',xp:20,reason:'FIRST_EFFECTIVE_CONTAINMENT'}],growthSnapshot:{level:1,totalXp:20}})
    await flushPromises()
    expect(wrapper.find('[aria-label="当前个人成长"]').text()).toContain('总经验 20 XP')
    expect(wrapper.find('.settlement').text()).toContain('首次有效收容 1');expect(wrapper.find('.settlement').text()).toContain('本次 20 XP')
  })
  it('keeps current global growth when opening an older snapshot and rejects older growth replies', async () => {
    api.stats.mockResolvedValue({totalXp:120,level:2,levelXp:20,xpPerLevel:100,xpToNextLevel:80,foundCount:8,containedCount:6,containedTypes:3,enabledTypes:5})
    await start();await select(1)
    api.detail.mockResolvedValue({...scanResult(3),comparisonStatus:'COMPARED',xpEarned:20,resolutions:[],growthSnapshot:{level:1,totalXp:20}})
    await wrapper.vm.$.setupState.openScan(3); await flushPromises()
    expect(wrapper.find('[aria-label="当前个人成长"]').text()).toContain('总经验 120 XP')
    const older=pending(); api.stats.mockReturnValueOnce(older.promise)
    const first=wrapper.vm.$.setupState.loadGrowth()
    api.stats.mockResolvedValue({totalXp:140,level:2,levelXp:40,xpPerLevel:100,xpToNextLevel:60,foundCount:9,containedCount:7,containedTypes:3,enabledTypes:5})
    await wrapper.vm.$.setupState.loadGrowth()
    older.resolve({totalXp:0,level:1,foundCount:0});await first;await flushPromises()
    expect(wrapper.find('[aria-label="当前个人成长"]').text()).toContain('总经验 140 XP')
  })
  it('shows no-award reasons and growth errors without fabricating success', async () => {
    api.stats.mockRejectedValueOnce(new Error('成长服务断开'));await start();await select(1)
    expect(wrapper.text()).toContain('成长服务断开');expect(wrapper.text()).not.toContain('总经验 0 XP')
    api.detail.mockResolvedValue({...scanResult(),comparisonStatus:'COMPARED',xpEarned:0,resolvedCount:1,containedCount:0,removedCount:0,ambiguousCount:0,resolutions:[{lineageKey:'paid',ruleCode:'EMPTY_IF',resolution:'CONFIRMED_FIXED',xp:0,reason:'ALREADY_REWARDED'}]})
    await wrapper.vm.$.setupState.openScan(10);await flushPromises();expect(wrapper.text()).toContain('该身份已经领奖，本次0XP')
  })
})

describe('week4 actual App AST, deletion, keyboard and event safety',()=>{
 it('rejects AST from the previous scan and previous project',async()=>{
  await start();await select(1);const a=pending(),b=pending();api.ast.mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise)
  api.detail.mockResolvedValueOnce(scanResult(10)).mockResolvedValueOnce(scanResult(11));await wrapper.vm.$.setupState.openScan(10);await wrapper.vm.$.setupState.openScan(11)
  b.resolve({scanId:11,status:'SUCCESS',nodesReturned:22,root:null});await flushPromises();a.resolve({scanId:10,status:'SUCCESS',nodesReturned:99,root:null});await flushPromises()
  expect(wrapper.vm.$.setupState.ast.scanId).toBe(11)
  const c=pending();api.ast.mockReturnValueOnce(c.promise);wrapper.vm.$.setupState.loadAst();await select(2);c.resolve({scanId:11,nodesReturned:500});await flushPromises();expect(wrapper.vm.$.setupState.ast).toBe(null)
 })
 it('AST real range opens a history model and retains unsaved draft',async()=>{
  await start();await select(1);api.detail.mockResolvedValue(scanResult(10));await wrapper.vm.$.setupState.openScan(10);await edit('class Draft {int x;}')
  await wrapper.vm.$.setupState.locateAst({range:{startLine:1,endLine:1}});expect(locate).toHaveBeenLastCalledWith(1,1);expect(wrapper.findComponent(Editor).props('historyMode')).toBe(true)
  wrapper.vm.$.setupState.returnToDraft();await nextTick();expect(wrapper.findComponent(Editor).props('modelValue')).toBe('class Draft {int x;}')
 })
 it('deletion clears context, cached draft and late scan result',async()=>{
  await start();await select(1);await edit('class SecretDraft {}');const late=pending();api.scan.mockReturnValueOnce(late.promise);wrapper.vm.$.setupState.scan();api.deleteProject.mockResolvedValue(null)
  await wrapper.vm.$.setupState.deleteProject();late.resolve(scanResult(10,'class SecretDraft {}'));await flushPromises()
  expect(wrapper.vm.$.setupState.selectedId).toBe(null);expect(wrapper.vm.$.setupState.result).toBe(null);expect(wrapper.findComponent(Editor).props('modelValue')).toBe('');expect(wrapper.vm.$.setupState.projects.some(p=>p.id===1)).toBe(false);expect(wrapper.vm.$.setupState.logs.some(e=>e.projectId===1)).toBe(false)
 })
 it('shows deletion failure and keeps draft available for retry',async()=>{
  await start();await select(1);await edit('class Keep {}');api.deleteProject.mockRejectedValue(new Error('删除事务失败'));await wrapper.vm.$.setupState.deleteProject();await flushPromises()
  expect(wrapper.text()).toContain('删除事务失败');expect(wrapper.findComponent(Editor).props('modelValue')).toBe('class Keep {}')
 })
 it('uses only Ctrl/Command Enter and records bounded real events without source',async()=>{
  await start();await select(1);api.scan.mockResolvedValue(scanResult());window.dispatchEvent(new KeyboardEvent('keydown',{key:'c'}));window.dispatchEvent(new KeyboardEvent('keydown',{key:'F5'}));expect(api.scan).not.toHaveBeenCalled()
  window.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',ctrlKey:true}));await flushPromises();expect(api.scan).toHaveBeenCalledTimes(1)
  expect(wrapper.vm.$.setupState.logs.some(e=>e.message.includes('客户端往返'))).toBe(true)
  for(let i=0;i<220;i++)wrapper.vm.$.setupState.event('event '+i)
  expect(wrapper.vm.$.setupState.logs).toHaveLength(200);expect(wrapper.vm.$.setupState.logs[0].at).toMatch(/^20/);expect(JSON.stringify(wrapper.vm.$.setupState.logs)).not.toContain('sourceCode')
 })
 it('displays HTML-looking source and labels as text with a failure gap',async()=>{
  await start();await select(1);api.detail.mockResolvedValue({...scanResult(),issues:[{issueKey:'html',ruleCode:'EMPTY_IF',monsterName:'<img src=x onerror=alert(1)>',message:'<script>bad</script>',suggestion:'<b>safe text</b>',startLine:1,endLine:1}]});await wrapper.vm.$.setupState.openScan(10);await flushPromises()
  expect(wrapper.find('.issue').text()).toContain('<script>bad</script>');expect(wrapper.find('.issue script').exists()).toBe(false);expect(wrapper.find('.issue img').exists()).toBe(false)
 })
})
