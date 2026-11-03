<script setup>
import { computed } from 'vue'
import { trendPoints,trendSegments } from '../trend'
const props=defineProps({ history:Array })
defineEmits(['open'])
const points=computed(()=>trendPoints(props.history));const segments=computed(()=>trendSegments(points.value))
</script>
<template><section class="trend"><h3>质量趋势 · 当前项目最近50次记录</h3><p class="hint">使用保存分数；×为解析失败。未知或不同评分配置断线。分数上升不直接等于有效修复。</p><svg viewBox="0 0 700 210" role="img" aria-label="最近扫描质量趋势"><path d="M20 30H680 M20 100H680 M20 170H680" stroke="#444" fill="none"/><text x="0" y="25">100</text><text x="0" y="190">0</text><line v-for="(s,i) in segments" :key="i" :x1="s.a.x" :y1="s.a.y" :x2="s.b.x" :y2="s.b.y" stroke="#ddd" stroke-width="2"/><g v-for="p in points" :key="p.scanId" role="button" tabindex="0" :aria-label="'打开扫描 #'+p.scanId" @click="$emit('open',p.scanId)" @keydown.enter="$emit('open',p.scanId)"><title>#{{ p.scanId }} · {{ p.score===null?'解析失败':p.score+'分' }} · {{ p.rulesetVersion }} · {{ p.scoringConfigId || '旧评分元数据未知' }}</title><circle v-if="p.y!==null" :cx="p.x" :cy="p.y" r="5" fill="#ddd"/><text v-else :x="p.x-4" y="195">×</text></g></svg><div class="trend-records"><button v-for="p in [...points].reverse()" :key="p.scanId" @click="$emit('open',p.scanId)">#{{ p.scanId }} · {{ p.score===null?'解析失败':p.score+' 分' }} · {{ p.rulesetVersion }} · {{ p.scoringConfigId?.slice(0,8) || '口径未知' }}</button></div><p v-if="!points.length">暂无扫描记录</p></section></template>
