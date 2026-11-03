import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import ElementPlus from 'element-plus'
import App from '../src/App.vue'
import AiRepairPreview from '../src/components/AiRepairPreview.vue'
import { api } from '../src/api'
vi.mock('../src/api',()=>({api:Object.fromEntries(['projects','catalog','history','createProject','saveProject','scan','detail','stats','monsters','ast','health','deleteProject','aiRepair'].map(k=>[k,vi.fn()]))}))
vi.mock('../src/JavaEditor.vue',()=>({default:{name:'JavaEditor',props:['modelValue','snapshot','historyMode','disabled','visualTheme'],methods:{clear(){},locate(){}},template:'<div />'}}))
const project={id:1001,name:'test',sourceCode:'class A {}',updatedAt:'2026-10-31T00:00:00Z'}
const reply={sourceCode:'class A { void fixed() {} }',summary:'修复说明',model:'deepseek-flash',syntaxValid:true,changed:true,beforeIssueCount:1,afterIssueCount:0}
const pending=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
let wrapper,state
async function start(silver=true){
 if(silver)localStorage.setItem('sanctuary.operator.v1',JSON.stringify({nickname:'狼尊999',theme:'silverwolf'}))
 wrapper=mount(App,{global:{plugins:[ElementPlus]}});await flushPromises();state=wrapper.vm.$.setupState;state.selectProject(project);await flushPromises()
}
beforeEach(()=>{localStorage.clear();vi.resetAllMocks();api.projects.mockResolvedValue([project,{...project,id:1002}]);api.catalog.mockResolvedValue({enabledRuleCodes:[],definitions:[]});api.history.mockResolvedValue([]);api.stats.mockResolvedValue({level:21,levelXp:20,totalXp:2020,foundCount:169,containedCount:101,containedTypes:5,enabledTypes:5});api.monsters.mockResolvedValue([]);api.health.mockResolvedValue({});api.aiRepair.mockResolvedValue(reply)})
afterEach(()=>{wrapper?.unmount();localStorage.clear();document.body.innerHTML=''})
it('keeps default scan full width and splits only silver theme into two equal columns',async()=>{
 await start(false);expect(wrapper.find('.scan-ai-row').exists()).toBe(false);expect(wrapper.find('.ai-repair-button').exists()).toBe(false);expect(api.aiRepair).not.toHaveBeenCalled()
 state.theme='silverwolf';await nextTick();expect(wrapper.get('.scan-ai-row').findAll('button')).toHaveLength(2);expect(wrapper.get('.ai-repair-button').text()).toContain('AI 修复')
 expect(wrapper.findAll('button').find(b=>b.text()==='验证收容 重新扫描')).toBeDefined()
 const css=await import('node:fs/promises').then(fs=>fs.readFile('src/ai-repair.css','utf8'));expect(css).toContain('grid-template-columns:minmax(0,1fr) minmax(0,1fr)')
})
it('sends unsaved current code, previews without writing, cancels and applies only to draft',async()=>{
 await start();state.source='class A { // unsaved\n }';await nextTick();await state.requestAiRepair();await flushPromises()
 expect(api.aiRepair).toHaveBeenCalledWith(1001,'class A { // unsaved\n }',expect.objectContaining({signal:expect.any(AbortSignal)}))
 expect(wrapper.findComponent(AiRepairPreview).exists()).toBe(true);expect(state.source).toContain('unsaved');expect(wrapper.text()).toContain('修复说明')
 state.cancelAiRepair();await nextTick();expect(state.source).toContain('unsaved');expect(state.aiPreview).toBeNull()
 await state.requestAiRepair();state.applyAiRepair();await nextTick();expect(state.source).toBe(reply.sourceCode);expect(state.savedSource).toBe(project.sourceCode)
 for(const method of ['scan','saveProject','createProject','deleteProject'])expect(api[method]).not.toHaveBeenCalled()
})
it('invalidates and aborts late results after edit, project, theme and history changes',async()=>{
 await start()
 for(const change of [()=>{state.source='class New {}'},()=>{state.selectProject({...project,id:1002})},()=>{state.theme='default'},()=>{state.result={scanId:10,issues:[],sourceCode:'old',createdAt:'2026-10-31T00:00:00Z',status:'SUCCESS',metadataSnapshot:{}};state.historyMode=true}]){
  state.theme='silverwolf';state.historyMode=false;state.selectProject(project);state.source=project.sourceCode;await nextTick()
  const late=pending();api.aiRepair.mockReturnValueOnce(late.promise);const waiting=state.requestAiRepair();const signal=api.aiRepair.mock.calls.at(-1)[2].signal
  change();await nextTick();expect(signal.aborted).toBe(true);late.resolve(reply);await waiting;expect(state.aiPreview).toBeNull();expect(state.source).not.toBe(reply.sourceCode)
 }
})
it('prevents duplicate work and disables blank, long, readonly and working drafts',async()=>{
 await start();for(const code of [' ', 'x'.repeat(20001)]){state.source=code;await nextTick();expect(wrapper.get('.ai-repair-button').attributes('disabled')).toBeDefined()}
 state.source=project.sourceCode;state.result={scanId:10,issues:[],sourceCode:'old',createdAt:'2026-10-31T00:00:00Z',status:'SUCCESS',metadataSnapshot:{}};state.historyMode=true;await nextTick();expect(state.aiCanStart).toBe(false);state.historyMode=false;state.working=true;await nextTick();expect(state.aiCanStart).toBe(false);state.working=false
 const late=pending();api.aiRepair.mockReturnValueOnce(late.promise);const request=state.requestAiRepair();await nextTick();expect(state.aiBusy).toBe(true);await state.requestAiRepair();await state.scan();await state.save();expect(api.aiRepair).toHaveBeenCalledTimes(1);expect(api.scan).not.toHaveBeenCalled();expect(api.saveProject).not.toHaveBeenCalled()
 state.cancelAiRepair();late.resolve(reply);await request;expect(state.source).toBe(project.sourceCode)
})
it('retains draft on error, rejects stale preview and clearly shows unchanged results',async()=>{
 await start();api.aiRepair.mockRejectedValueOnce(new Error('DeepSeek余额不足'));await state.requestAiRepair();expect(state.aiError).toContain('余额不足');expect(state.aiBusy).toBe(false);expect(state.source).toBe(project.sourceCode)
 await state.requestAiRepair();state.source='class Later {}';state.applyAiRepair();expect(state.source).toBe('class Later {}');expect(state.aiPreview).toBeNull()
 api.aiRepair.mockResolvedValueOnce({...reply,sourceCode:state.source,changed:false,summary:'未建议修改'});await state.requestAiRepair();await nextTick();expect(wrapper.text()).toContain('未建议修改');expect(wrapper.findComponent(AiRepairPreview).findAll('button').find(b=>b.text()==='应用到草稿').attributes('disabled')).toBeDefined()
})
