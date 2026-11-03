<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch, defineAsyncComponent } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'
import AiRepairPreview from './components/AiRepairPreview.vue'
import './ai-repair.css'
import { createRequestContext } from './requestContext'
import JavaEditor from './JavaEditor.vue'
import AstPanel from './components/AstPanel.vue'
import Chambers from './components/Chambers.vue'
import avatar from './assets/lab/avatar.svg'
import { monsterSprites } from './monsterSprites'
import OperatorIdentity from './components/OperatorIdentity.vue'
import SilverwolfTransition from './components/SilverwolfTransition.vue'
import { createOperatorProfile } from './operatorProfile'
import silverOperator from './assets/silverwolf/operator.png'
import silverMark from './assets/silverwolf/mark.svg'
const preferences=createOperatorProfile()
const operatorProfile=ref(preferences.snapshot())
const theme=ref(operatorProfile.value.theme),transitioning=ref(false),nicknameWarning=ref(''),operatorArtFailed=ref(false),systemReduce=ref(false)
let motionQuery
const motionChange=e=>systemReduce.value=e.matches
function saveOperatorName(name){try{const value=preferences.save(name);operatorProfile.value=value.profile;nicknameWarning.value=value.warning;if(value.playTransition)transitioning.value=true}catch(e){ElMessage.error(e.message)}}
function finishThemeTransition(){preferences.finishTransition();theme.value=operatorProfile.value.theme;transitioning.value=false}
function restoreDefaultTheme(){const value=preferences.restoreDefault();operatorProfile.value=value.profile;nicknameWarning.value=value.warning;transitioning.value=false;theme.value='default'}
watch(theme,value=>{document.documentElement.dataset.sanctuaryTheme=value},{immediate:true})
const GrowthPanel=defineAsyncComponent(()=>import('./components/GrowthPanel.vue'))
const reduceMotion=ref(false)
const effectiveReduce=computed(()=>reduceMotion.value||systemReduce.value)
const view=ref('console'),version=ref(null),ast=ref(null),astLoading=ref(false),astError=ref('')
const astContext=createRequestContext(),chamberPage=ref(0),selectedKey=ref(null),logs=ref([]),showDelete=ref(false),deleting=ref(false)
function event(message,projectId=selectedId.value,scanId=null) {
 if(deletedProjects.has(projectId))return
 logs.value.push({at:new Date().toISOString(),projectId,scanId,message});if(logs.value.length>200) logs.value.shift()
}
const activeLogs=computed(()=>logs.value.filter(e=>e.projectId===selectedId.value && (e.scanId===null || e.scanId===result.value?.scanId)))
const definitions=computed(()=>result.value?.metadataSnapshot?.definitions || catalog.value?.definitions || [])
function resetAst(){astContext.invalidate();ast.value=null;astError.value='';astLoading.value=false;selectedKey.value=null;chamberPage.value=0}
async function loadAst(){
 resetAst();const id=result.value?.scanId;if(!id)return
 const current=pageContext.capture(),latest=astContext.capture();astLoading.value=true
 try {const data=await api.ast(id);if(data.scanId!==id)throw new Error('AST扫描标识不符');if(current()&&latest()&&result.value?.scanId===id){ast.value=data;event('AST快照加载：'+data.nodesReturned+'节点'+(data.truncated?'（已截断）':''),selectedId.value,id)}}
 catch(e){if(current()&&latest()){astError.value=e.message;event('AST加载失败：'+e.message,selectedId.value,id)}}
 finally{if(current()&&latest())astLoading.value=false}
}
async function locateAst(node){if(!node.range)return;historyMode.value=!!differs.value;await nextTick();editor.value?.locate(node.range.startLine,node.range.endLine)}
async function deleteProject(){
 const id=selectedId.value;if(id===null||deleting.value)return;deleting.value=true
 try{await api.deleteProject(id);deletedProjects.add(id);drafts.delete(id);mutations.set(id,(mutations.get(id)||0)+1);invalidateList();projects.value=projects.value.filter(p=>p.id!==id);logs.value=logs.value.filter(e=>e.projectId!==id)
 if(selectedId.value===id){pageContext.invalidate();detailContext.invalidate();historyContext.invalidate();resetAst();selectedId.value=null;source.value=savedSource.value=name.value=savedName.value='';result.value=null;history.value=[];historyMode.value=false;working.value=false;view.value='projects'}
 showDelete.value=false
 }catch(e){error.value=e.message;event('删除失败：'+e.message,id)}finally{deleting.value=false}
}
function shortcut(e){if(transitioning.value||e.defaultPrevented)return;if((e.ctrlKey||e.metaKey)&&e.key==='Enter'){e.preventDefault();scan()}}
function suggestions(){const issue=result.value?.issues.find(i=>i.issueKey===selectedKey.value)||result.value?.issues[0];if(issue){locate(issue);ElMessage.info(issue.suggestion)}else{ElMessage.info('选中真实问题后查看后端规则建议')}}


const projects = ref([])
const selectedId = ref(null)
const name = ref('')
const source = ref('')
const savedName = ref('')
const savedSource = ref('')
const projectsLoading = ref(false)
const working = ref(false)
const historyLoading = ref(false)
const resultLoading = ref(false)
const error = ref('')
const historyError = ref('')
const result = ref(null)
const history = ref([])
const showCreate = ref(false)
const newName = ref('')
const creating = ref(false)
const createError = ref('')
const pageContext = createRequestContext()
const detailContext = createRequestContext()
const historyContext = createRequestContext()
const listContext = createRequestContext()
const drafts = new Map()
const deletedProjects=new Set()
const mutations = new Map()
const editor = ref(null)
const historyMode = ref(false)
const catalog = ref(null)
const catalogError = ref('')
const catalogLoading = ref(false)
const growth = ref(null)
const monsters = ref([])
const growthLoading = ref(false)
const growthError = ref('')
const growthContext = createRequestContext()
const statusNames = { NEW: '待处理 · 新发现', PERSISTING: '待处理 · 持续存在', CONFIRMED_FIXED: '修复已确认', REMOVED_WITH_SCOPE: '结构移除 · 未奖励', AMBIGUOUS: '匹配歧义 · 未奖励', UNSUPPORTED_SCOPE: '此范围不支持自动奖励', BASELINE_CREATED: '已建立身份基线', INCOMPATIBLE_RULESET: '规则配置变化 · 新比较基线', COMPARED: '已与成功基线比较', PARSE_ERROR: '语法失败 · 未结算', NOT_RECORDED: '旧版未记录收容结果' }
const reasonNames = { ALREADY_REWARDED: '该身份已经领奖，本次0XP', FIRST_EFFECTIVE_CONTAINMENT: '首次有效收容', METHOD_OR_TYPE_NOT_CONTINUOUS: '原方法或类型未确认保留', UNCONFIRMED_GROUP_MEMBERSHIP: '无法确认此身份在歧义组中的实际存在，未奖励', HISTORICAL_IDENTITY_RECURRED: '历史问题复发，继承原身份', METHOD_CONTINUITY_UNCERTAIN: '无法确认方法连续性', NOT_A_DIRECT_METHOD_IN_A_NAMED_NONLOCAL_TYPE: '构造器、初始化块、lambda、匿名或局部类型暂不自动奖励', GROUP_FROZEN_UNTIL_ALL_OBSERVATIONS_DISAPPEAR: '歧义组仍有问题，保留原身份组并暂停结算' }
reasonNames.FORMAT_ONLY_RULE_DISAPPEARANCE = '仅排版或注释变化使行数减少，尚未确认有效修复，0XP'
reasonNames.UNSUPPORTED_OBSERVATION_PREVENTS_CONFIRMATION = '同类问题出现在不支持的范围，无法排除位置迁移，暂停自动奖励'
const monsterStates = { UNDISCOVERED: '未发现', DISCOVERED: '已发现 · 未收容', CONTAINED: '已收容' }
const ruleName = code => catalog.value?.definitions.find(d => d.ruleCode === code)?.monsterName || code
async function loadGrowth() {
  growthContext.invalidate()
  const latest = growthContext.capture()
  growthLoading.value = true
  growthError.value = ''
  try {
    const [stats, entries] = await Promise.all([api.stats(), api.monsters()])
    if (latest() && (!growth.value || (stats.totalXp >= growth.value.totalXp && stats.foundCount >= growth.value.foundCount))) {
      growth.value = stats; monsters.value = entries
    }
  } catch (e) { if (latest()) growthError.value = e.message }
  finally { if (latest()) growthLoading.value = false }
}
const selected = computed(() => selectedId.value !== null)
const dirty = computed(() => source.value !== savedSource.value || name.value !== savedName.value)
const differs = computed(() => result.value && result.value.sourceCode !== source.value)
const validSource = computed(() => source.value.trim().length > 0 && source.value.length <= 20000)
const rules = computed(() => catalog.value?.enabledRuleCodes.join(' · ') || '规则目录尚未加载')
const time = value => new Date(value).toLocaleString('zh-CN', { hour12: false })
const aiBusy=ref(false),aiPreview=ref(null),aiError=ref('')
let aiController=null,aiSequence=0,aiTimer=null
const aiCanStart=computed(()=>theme.value==='silverwolf'&&!transitioning.value&&selected.value&&validSource.value&&!historyMode.value&&!working.value&&!deleting.value&&!resultLoading.value&&!creating.value&&!aiBusy.value)
function invalidateAi(message='') {
 const active=aiBusy.value||aiPreview.value
 ++aiSequence;aiController?.abort();aiController=null;clearTimeout(aiTimer);aiTimer=null;aiBusy.value=false;aiPreview.value=null
 if(active)aiError.value=message
}
watch([selectedId,source,theme,historyMode,view,transitioning,deleting,showDelete,working,resultLoading,()=>result.value?.scanId],()=>{
 if(aiBusy.value||aiPreview.value)invalidateAi('代码或上下文已变化，旧AI建议已丢弃。')
},{flush:'sync'})
async function requestAiRepair(){
 if(!aiCanStart.value)return
 invalidateAi();aiError.value=''
 const id=selectedId.value,original=source.value,sequence=++aiSequence,controller=new AbortController()
 aiController=controller;aiBusy.value=true
 let timedOut=false
 const timer=setTimeout(()=>{timedOut=true;controller.abort()},65000);aiTimer=timer
 const current=()=>sequence===aiSequence&&selectedId.value===id&&source.value===original&&theme.value==='silverwolf'&&!historyMode.value&&!deletedProjects.has(id)
 try {
  const data=await api.aiRepair(id,original,{signal:controller.signal})
  if(!current())return
  if(typeof data?.sourceCode!=='string'||!data.sourceCode.trim()||data.sourceCode.length>20000||data.syntaxValid!==true)throw new Error('AI修复返回无效，草稿已保留')
  aiPreview.value={...data,original,id,sequence}
  event('AI修复预览已生成（0XP）')
 }catch(e){if(current())aiError.value=timedOut?'AI等待超时，草稿已保留，请稍后重试':e.name==='AbortError'?'AI等待已取消，草稿已保留':e.message}
 finally{clearTimeout(timer);if(sequence===aiSequence){aiBusy.value=false;aiController=null;aiTimer=null}}
}
function cancelAiRepair(){invalidateAi('已取消AI等待或预览，草稿保持原样。')}
function applyAiRepair(){
 const preview=aiPreview.value
 if(!preview||preview.sequence!==aiSequence||preview.id!==selectedId.value||preview.original!==source.value||theme.value!=='silverwolf'||historyMode.value||working.value||deleting.value){invalidateAi('上下文已变化，未应用旧AI建议。');return}
 if(!preview.changed)return
 const fixed=preview.sourceCode
 invalidateAi();aiError.value='';source.value=fixed;editor.value?.clear()
 event('AI建议已应用到草稿，尚未保存或扫描（0XP）')
 ElMessage.success('已应用到草稿，可提交扫描或验证收容')
}


async function loadProjects() {
  listContext.invalidate()
  const latest = listContext.capture()
  projectsLoading.value = true
  try { const data = await api.projects(); if (latest()) projects.value = data }
  catch (e) { if (latest()) error.value = e.message }
  finally { if (latest()) projectsLoading.value = false }
}
function invalidateList() { listContext.invalidate(); projectsLoading.value = false }
async function loadCatalog() {
  catalogLoading.value = true; catalogError.value = ''
  try { catalog.value = await api.catalog() }
  catch (e) { catalogError.value = e.message; catalog.value = null }
  finally { catalogLoading.value = false }
}
async function loadHistory(id = selectedId.value) {
  const currentPage = pageContext.capture()
  historyContext.invalidate()
  const latest = historyContext.capture()
  historyLoading.value = true
  historyError.value = ''
  try {
    const data = await api.history(id)
    if (currentPage() && latest()) history.value = data
  } catch (e) { if (currentPage() && latest()) historyError.value = e.message }
  finally { if (currentPage() && latest()) historyLoading.value = false }
}
function selectProject(project) {
  if (selectedId.value === project.id) return
  if (selectedId.value !== null) drafts.set(selectedId.value, { name: name.value, source: source.value, savedName: savedName.value, savedSource: savedSource.value })
  pageContext.invalidate()
  detailContext.invalidate()
  resetAst()
  selectedId.value = project.id
  name.value = savedName.value = project.name
  source.value = savedSource.value = project.sourceCode
  const draft = drafts.get(project.id)
  if (draft) { name.value = draft.name; source.value = draft.source; savedName.value = draft.savedName; savedSource.value = draft.savedSource }
  historyMode.value = false
  result.value = null
  history.value = []
  error.value = ''
  working.value = false
  resultLoading.value = false
  loadHistory(project.id)
}
async function createProject() {
  if (!newName.value.trim()) { createError.value = '请输入项目名称'; return }
  creating.value = true
  invalidateList()
  createError.value = ''
  try {
    const project = await api.createProject(newName.value.trim(), '')
    invalidateList()
    projects.value.unshift(project)
    showCreate.value = false
    newName.value = ''
    selectProject(project)
    ElMessage.success('项目已创建，可以输入 Java 源码')
  } catch (e) { createError.value = e.message }
  finally { creating.value = false }
}
async function save() {
  if(aiBusy.value)return
  const isCurrent = pageContext.capture()
  const id = selectedId.value, draftName = name.value, draftSource = source.value
  const mutation = (mutations.get(id) || 0) + 1
  mutations.set(id, mutation)
  invalidateList()
  working.value = true
  error.value = ''
  try {
    const project = await api.saveProject(id, draftName, draftSource)
    if (mutations.get(id) !== mutation) return
    invalidateList()
    const index = projects.value.findIndex(p => p.id === id)
    if (index !== -1) projects.value[index] = project
    if (isCurrent()) {
      savedSource.value = draftSource
      savedName.value = draftName
      event('草稿保存成功')
      ElMessage.success('草稿已保存到数据库')
    } else if (drafts.has(id)) {
      Object.assign(drafts.get(id), { savedName: draftName, savedSource: draftSource })
    }
  } catch (e) { if (isCurrent()) error.value = e.message }
  finally { if (isCurrent()) working.value = false }
}
async function scan() {
  if (working.value || aiBusy.value || selectedId.value === null) return
  if (!validSource.value) { error.value = '请输入非空白 Java 源码，最多 20000 字符'; return }
  const isCurrent = pageContext.capture()
  const id = selectedId.value, submitted = source.value
  detailContext.invalidate()
  const latestDetail = detailContext.capture()
  resultLoading.value = false
  working.value = true
  resetAst()
  const started=performance.now()
  event('扫描请求提交',id)
  error.value = ''
  try {
    const data = await api.scan(id, submitted)
    event('扫描响应：'+data.status+' · '+data.issues.length+'个问题 · 客户端往返 '+Math.round(performance.now()-started)+'ms',id,data.scanId)
    if (isCurrent()) {
      if (latestDetail()) { result.value = data; historyMode.value = false; editor.value?.clear(); loadAst() }
      await loadHistory(id)
    }
    await loadGrowth()
  } catch (e) { if (isCurrent() && latestDetail()){error.value = e.message;event('扫描失败：'+e.message,id)} }
  finally { if (isCurrent()) working.value = false }
}
async function openScan(id) {
  const isCurrent = pageContext.capture()
  detailContext.invalidate()
  resetAst()
  const latest = detailContext.capture()
  resultLoading.value = true
  error.value = ''
  try {
    const data = await api.detail(id)
    if (isCurrent() && latest()) { result.value = data; historyMode.value = false; editor.value?.clear(); loadAst() }
  } catch (e) { if (isCurrent() && latest()) error.value = e.message }
  finally { if (isCurrent() && latest()) resultLoading.value = false }
}
async function locate(issue) {
  selectedKey.value=issue.issueKey
  const index=definitions.value.findIndex(d=>d.ruleCode===issue.ruleCode);if(index>=0)chamberPage.value=Math.floor(index/3)
  historyMode.value = !!differs.value
  await nextTick()
  editor.value?.locate(issue.startLine, issue.endLine)
}
function returnToDraft() { historyMode.value = false; editor.value?.clear() }
onMounted(() => { motionQuery=window.matchMedia?.('(prefers-reduced-motion: reduce)');systemReduce.value=motionQuery?.matches||false;motionQuery?.addEventListener?.('change',motionChange);loadProjects(); loadCatalog(); loadGrowth(); api.health?.().then(v=>version.value=v).catch(()=>{});window.addEventListener('keydown',shortcut) })
onBeforeUnmount(()=>{invalidateAi();motionQuery?.removeEventListener?.('change',motionChange);delete document.documentElement.dataset.sanctuaryTheme;window.removeEventListener('keydown',shortcut);pageContext.invalidate();detailContext.invalidate();astContext.invalidate();historyContext.invalidate();growthContext.invalidate();listContext.invalidate()})
</script>
<template>
<main class="shell" :class="{'reduce-motion':effectiveReduce,'silver-theme':theme==='silverwolf'}" :inert="transitioning || undefined" :aria-hidden="transitioning || undefined">
<header class="masthead"><img :src="theme==='silverwolf' ? silverMark : avatar" alt="" width="64" height="64"/><div class="brand"><p class="eyebrow">BUG MONSTER SANCTUARY / LAB 01</p><h1>BUG 怪兽收容所</h1><p>Java 静态代码分析平台 <span>捕获坏味道，让代码更清晰。</span></p></div><div class="lab-scene" aria-hidden="true"><i></i><b>BUG<br>LAB</b></div><div class="header-actions"><button @click="view='projects'">项目 / 历史</button><button :disabled="!selected || working || aiBusy" @click="save">保存草稿</button></div></header>
<nav class="main-nav" aria-label="功能入口"><button :class="{active:view==='console'}" @click="view='console'">01 主控台</button><button :class="{active:view==='projects'}" @click="view='projects'">02 项目 / 历史</button><button :class="{active:view==='catalog'}" @click="view='catalog'">03 怪兽图鉴</button><button :class="{active:view==='growth'}" @click="view='growth'">04 个人成长</button><span>{{ version ? '● '+version.matchingPolicyVersion : '○ 后端版本未确认' }}</span></nav>
<p v-if="error" role="alert" class="error-banner">{{ error }}</p>
<div class="laboratory">
<section class="metal editor-panel"><header class="panel-title"><h2>⌨ {{ selected ? name+'.java' : '代码观察区' }}</h2><span>{{ historyMode ? '历史只读' : dirty ? '有未保存修改' : '已与数据库草稿同步' }}</span></header><div v-if="selected" class="editor-toolbar"><label for="project-name">项目</label><input id="project-name" v-model="name" maxlength="100" :disabled="working"/><span>{{ source.length }} / 20000</span></div><div v-if="historyMode" role="status" class="snapshot-banner">只读历史快照 · 扫描 #{{ result.scanId }} · 未保存草稿已保留 <button @click="returnToDraft">返回草稿</button></div><div v-else class="code-meta"><span>{{ selected ? '完整 Java 源码草稿 · 扫描单独保存快照' : '创建项目，开始代码实验。' }}</span><span>Java 21</span></div><JavaEditor ref="editor" v-model="source" :snapshot="result?.sourceCode || ''" :history-mode="historyMode" :visual-theme="theme" :disabled="!selected || working"/><div class="editor-status"><span>{{ selected ? '#'+selectedId : '未选择项目' }} · UTF-16</span><button v-if="!selected" @click="view='projects';showCreate=true">创建项目</button><span>Ctrl / ⌘ + Enter 扫描</span></div></section>
<Chambers :scan-status="result?.status || 'NOT_SCANNED'" :definitions="definitions" :issues="result?.issues || []" :page="chamberPage" :selected-key="selectedKey" @page="chamberPage=$event" @select="locate"/>
<aside class="metal control-panel"><section aria-label="当前个人成长" class="growth-panel"><div class="operator"><img :src="theme==='silverwolf' && !operatorArtFailed ? silverOperator : avatar" width="64" height="64" :alt="theme==='silverwolf' ? '银狼操作者' : '像素实验员'" @error="operatorArtFailed=true"/><div><OperatorIdentity :nickname="operatorProfile.nickname" :theme="theme" :busy="transitioning" :warning="nicknameWarning" @save="saveOperatorName" @reset="restoreDefaultTheme"/><strong>{{ growth ? 'Lv.'+growth.level : '等级未加载' }}</strong></div></div><p v-if="theme==='silverwolf'" class="silver-dialogue">准备好了？<br>让代码世界更干净。</p><p v-if="growthError" class="danger" role="alert">{{ growthError }} <button @click="loadGrowth">重试成长</button></p><template v-if="growth"><progress :value="growth.levelXp" max="100" aria-label="本级经验"/><p class="xp-label">{{ growth.levelXp }} / 100 XP · 总经验 {{ growth.totalXp }} XP</p><div class="catalog-progress"><h3>收容进度 <b>{{ growth.containedTypes }} / {{ growth.enabledTypes }} 类</b></h3><progress :value="growth.containedTypes" :max="growth.enabledTypes || 1"/><small>累计收容 {{ growth.containedCount }} 次 · 发现 {{ growth.foundCount }} 个身份</small></div></template><p v-else class="hint">成长数据来自后端，尚未取得。</p></section><div class="side-catalog"><h3>怪兽图鉴</h3><div><button v-for="d in catalog?.definitions || []" :key="d.ruleCode" :title="d.monsterName" @click="view='catalog'"><img v-if="theme==='silverwolf'" :src="monsterSprites[d.ruleCode]" alt=""/><span v-else>{{ d.monsterName.slice(0,1) }}</span></button></div><button @click="view='catalog'">查看五种怪兽 →</button></div><div class="primary-actions"><div v-if="theme==='silverwolf'" class="scan-ai-row"><button class="primary" :disabled="!selected || working || aiBusy || !validSource || !catalog" @click="scan">{{ working?'请求处理中…':'提交扫描' }}</button><button class="ai-repair-button" :disabled="!aiCanStart" @click="requestAiRepair"><span>{{ aiBusy?'修复中…':'AI 修复' }}</span><small>作弊模式</small></button></div><button v-else class="primary" :disabled="!selected || working || !validSource || !catalog" @click="scan">{{ working?'请求处理中…':'提交扫描' }} </button><p v-if="theme==='silverwolf'" class="ai-send-note">将当前代码发送至DeepSeek生成修复预览</p><p v-if="aiBusy" class="ai-wait-note" role="status">正在生成建议… <button @click="cancelAiRepair">取消等待</button></p><p v-if="aiError && theme==='silverwolf'" class="ai-error" role="alert">{{ aiError }}</p><button :disabled="!selected || working || aiBusy || !validSource || !catalog || !result || result.status!=='SUCCESS' || !differs" @click="scan">验证收容 <small>重新扫描</small></button><button :disabled="!result?.issues.length" @click="suggestions">修复建议 <small>规则建议</small></button><button @click="view='catalog'">怪兽图鉴 <small>5 TYPES</small></button></div><div class="operator-note"><img :src="avatar" width="64" height="64" alt=""/><p>{{ result?.containedCount>0 ? '后端确认新增 '+result.containedCount+' 次有效收容！' : '保存、扫描、定位、修复。每一步都留下真实记录。' }}</p></div><small>本地单用户演示 · 仅静态解析</small></aside>
<section class="metal results-panel"><header class="panel-title"><h2>▤ 分析结果</h2><span v-if="result">扫描 #{{ result.scanId }} · {{ result.score===null?'未评分':result.score+' / 100 分' }}</span></header><div class="panel-scroll"><p v-if="resultLoading">正在读取历史快照…</p><p v-if="catalogError" class="danger">{{ catalogError }} <button @click="loadCatalog">重试目录</button></p><template v-if="result"><div class="result-meta">{{ time(result.createdAt) }} · {{ result.rulesetVersion }} · 基线 {{ result.baselineScanId ? '#'+result.baselineScanId : '无' }}</div><p v-if="differs" class="hint">此结果与当前草稿不同；点击位置进入只读历史快照。</p><div v-if="result.status==='PARSE_ERROR'" class="parse-errors"><strong>Java 语法错误 · 未评分 · 0XP</strong><p v-for="(p,i) in result.parseProblems" :key="i">{{ p.startLine ? 'L'+p.startLine+':'+p.startColumn : '位置未知' }} {{ p.message }}</p></div><p v-else-if="!result.issues.length" class="empty-note">本次启用规则未发现问题</p><table v-else class="issue-table"><thead><tr><th>怪兽 / 规则</th><th>位置</th><th>问题与修复建议</th></tr></thead><tbody><tr v-for="issue in result.issues" :key="issue.issueKey" class="issue" :class="{chosen:selectedKey===issue.issueKey}" role="button" tabindex="0" :aria-label="'定位 '+issue.ruleCode+' 第'+issue.startLine+'行'" @click="locate(issue)" @keydown.enter="locate(issue)" @keydown.space.prevent="locate(issue)"><td><strong>{{ issue.monsterName }}</strong><small>{{ issue.ruleCode }}</small><small>{{ statusNames[issue.matchStatus] || '旧观察未记录身份' }}</small></td><td>L{{ issue.startLine }}–{{ issue.endLine }}</td><td>{{ issue.message }}<small>建议：{{ issue.suggestion }}</small></td></tr></tbody></table><div class="settlement" :class="{'containment-success':result.containedCount>0}" role="status"><strong>{{ statusNames[result.comparisonStatus] || '收容结果未记录' }}</strong><p v-if="result.xpEarned!==null && result.xpEarned!==undefined">确认修复 {{ result.resolvedCount }} · 首次有效收容 {{ result.containedCount }} · 结构移除 {{ result.removedCount }} · 待确认 {{ result.ambiguousCount }} · 本次 {{ result.xpEarned }} XP</p><p v-if="result.comparisonStatus==='BASELINE_CREATED'">首次成功扫描只建立身份基线，本次不发经验。</p><p v-if="result.comparisonStatus==='INCOMPATIBLE_RULESET'">规则、启用集合或匹配口径不兼容，新比较基线0XP。</p><p v-if="result.ambiguousCount>0 || result.issues.some(i=>i.matchStatus==='AMBIGUOUS')">争用相关方法持续冻结自动奖励，不会自动解冻。</p><article v-for="(r,i) in result.resolutions || []" :key="r.lineageKey+i" class="resolution">{{ ruleName(r.ruleCode) }} · {{ statusNames[r.resolution] || r.resolution }} · {{ r.xp }} XP <small>{{ reasonNames[r.reason] || (r.resolution==='AMBIGUOUS'?'身份无法确认，未奖励':r.reason) }}</small></article><small v-if="result.growthSnapshot">该次扫描成长快照：Lv.{{ result.growthSnapshot.level }} · {{ result.growthSnapshot.totalXp }} XP；当前个人状态见右侧。</small></div></template><p v-else class="empty-note">选择或创建项目，提交源码后查看真实问题、评分与收容结果。</p></div></section>
<AstPanel :ast="ast" :loading="astLoading" :error="astError" :scan-id="result?.scanId" @load="loadAst" @locate="locateAst"/>
<section class="metal log-panel"><header class="panel-title"><h2>≡ 事件日志</h2><span>LIVE</span></header><div class="panel-scroll"><p v-if="!activeLogs.length" class="empty-note">此项目 / 扫描的客户端事件未记录。历史事件不回放。</p><ol class="event-log"><li v-for="(e,i) in activeLogs" :key="i"><time>{{ new Date(e.at).toLocaleTimeString('zh-CN',{hour12:false}) }}</time> {{ e.message }}<small v-if="e.scanId"> #{{ e.scanId }}</small></li></ol></div></section>
</div>
<section v-show="view==='projects'" class="overlay" aria-label="项目与历史"><header class="panel-title"><h2>项目 / 扫描历史</h2><button @click="view='console'">返回主控台 ×</button></header><div class="project-layout"><section class="projects"><div class="section-heading"><h3>项目档案</h3><button @click="showCreate=true">创建项目</button></div><p v-if="projectsLoading">正在加载项目…</p><button v-for="p in projects" :key="p.id" class="project-item" :class="{active:p.id===selectedId}" @click="selectProject(p)"><span>{{ p.name }}</span><small>#{{ p.id }} · {{ time(p.updatedAt) }}</small></button><p v-if="!projects.length && !projectsLoading">创建第一个项目，源码保存在本机数据库。</p><button @click="loadProjects">刷新项目</button></section><section><div class="section-heading"><h3>{{ selected ? name+' · 扫描历史' : '请选择项目' }}</h3><button :disabled="!selected" @click="loadHistory()">刷新</button></div><p v-if="historyError" class="danger">{{ historyError }}</p><p v-if="historyLoading">正在查询数据库…</p><button v-for="item in history" :key="item.scanId" class="history-item" :class="{chosen:result?.scanId===item.scanId}" @click="openScan(item.scanId);view='console'"><span>#{{ item.scanId }} <b>{{ item.score===null?'语法错误':item.score+' 分' }}</b></span><small>{{ time(item.createdAt) }} · {{ item.rulesetVersion }}</small></button><p class="hint">最近200次记录。定位历史快照不会覆盖草稿。</p><button v-if="selected" class="danger" @click="showDelete=true">删除当前项目与源码</button></section></div></section>
<section v-show="view==='catalog'" class="overlay" aria-label="怪兽图鉴"><header class="panel-title"><h2>怪兽图鉴 / 五条静态规则</h2><button @click="view='console'">返回主控台 ×</button></header><div class="catalog-grid"><article v-for="d in catalog?.definitions || []" :key="d.ruleCode"><img :src="monsterSprites[d.ruleCode]" width="128" height="128" alt=""/><h3>{{ d.monsterName }}</h3><code>{{ d.ruleCode }}</code><p>{{ d.description }}</p><p>{{ d.suggestion }}</p><small>每次扣 {{ d.deduction }} 分 · {{ d.enabled?'已启用':'已停用' }}</small><p>{{ monsterStates[monsters.find(m=>m.ruleCode===d.ruleCode)?.state] || '成长状态未取得' }}</p><small>发现 {{ monsters.find(m=>m.ruleCode===d.ruleCode)?.foundCount ?? '—' }} 个身份 · 有效收容 {{ monsters.find(m=>m.ruleCode===d.ruleCode)?.containedCount ?? '—' }} 次</small></article></div></section>
<section v-show="view==='growth'" class="overlay" aria-label="成长与趋势"><header class="panel-title"><h2>个人成长 / 质量记录</h2><button @click="view='console'">返回主控台 ×</button></header><p v-if="growth">当前全局：Lv.{{ growth.level }} · 累计 {{ growth.totalXp }} XP · 已收容 {{ growth.containedTypes }}/{{ growth.enabledTypes }} 类</p><GrowthPanel v-if="view==='growth'" :history="history" @open="openScan($event);view='console'"/></section>
<el-dialog v-model="showCreate" title="创建项目" width="min(420px,90vw)"><label>项目名称<el-input v-model="newName" maxlength="100" placeholder="例如：异常处理练习" @keyup.enter="createProject"/></label><p v-if="createError" role="alert" class="danger">{{ createError }}</p><template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" :loading="creating" @click="createProject">创建项目</el-button></template></el-dialog>
<el-dialog v-model="showDelete" title="删除项目与源码" width="min(520px,90vw)"><p>将清除当前项目草稿、全部扫描源码、解析错误及方法/类型等派生状态；项目不能恢复或再次扫描。累计XP、发现/收容次数与无源码奖励审计保留，删除本身0XP。</p><p>范围为当前应用数据库和本应用缓存；既有备份、手工导出和旧交付证据不由此按钮删除。</p><template #footer><el-button @click="showDelete=false">取消</el-button><el-button type="danger" :loading="deleting" @click="deleteProject">确认删除源码</el-button></template></el-dialog>
<AiRepairPreview v-if="aiPreview" :preview="aiPreview" @apply="applyAiRepair" @cancel="cancelAiRepair"/>
<footer><button :aria-pressed="reduceMotion" @click="reduceMotion=!reduceMotion">减少动态 {{ reduceMotion ? '已开启' : '关闭' }}</button><span>{{ version ? version.rulesetVersion+' · '+version.matchingPolicyVersion+' · '+version.buildId : '运行版本未取得' }}</span><span>JavaParser {{ version?.parserVersion || '—' }} · Java 21 · 后端确认经验</span></footer>
</main>
<SilverwolfTransition v-if="transitioning" :reduced-motion="effectiveReduce" @complete="finishThemeTransition"/>
</template>
