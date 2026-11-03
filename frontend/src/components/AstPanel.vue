<script setup>
import AstNode from './AstNode.vue'
defineProps({ ast:Object, loading:Boolean, error:String, scanId:Number })
defineEmits(['load','locate'])
</script>
<template><section class="metal ast-panel"><header class="panel-title"><h2>⌘ AST 语法树</h2><button :disabled="!scanId || loading" @click="$emit('load')">{{ loading?'查询中…':'刷新' }}</button></header><div class="panel-scroll"><p v-if="error" role="alert" class="danger">{{ error }}</p><p v-if="!scanId" class="empty-note">提交扫描后，观察真实语法结构。</p><template v-else-if="ast"><p class="panel-caption">扫描 #{{ ast.scanId }} · {{ ast.nodesReturned }} 节点 <b v-if="ast.truncated">· 已截断</b></p><p class="panel-caption">JavaParser {{ ast.parserVersion }} · Java 21<br>保存快照经当前解析器重新解析</p><p v-if="ast.status==='PARSE_ERROR'">语法失败，无可用树。{{ ast.parseProblems?.[0]?.message }}</p><ul v-if="ast.root" class="ast-tree"><AstNode :node="ast.root" @locate="$emit('locate',$event)" /></ul><p v-if="ast.truncated" class="hint">预算：最多500节点、根深0至12。省略部分不代表没有结构。</p></template><p v-else-if="!loading" class="empty-note">AST尚未加载。可点击刷新重试。</p></div></section></template>
