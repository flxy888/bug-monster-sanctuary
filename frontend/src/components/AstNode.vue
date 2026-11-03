<script setup>
import { ref } from 'vue'
const props=defineProps({ node:Object, depth:{type:Number,default:0} })
const emit=defineEmits(['locate'])
const expanded=ref(props.depth===0)
</script>
<template><li class="ast-node"><div class="ast-line"><button v-if="node.children.length" class="tree-toggle" :aria-label="(expanded ? '折叠 ' : '展开 ') + node.type" @click="expanded=!expanded">{{ expanded ? '−' : '+' }}</button><span v-else class="tree-leaf">·</span><button class="tree-label" :disabled="!node.range" :title="node.label" @click="emit('locate',node)">{{ node.type }}<small v-if="node.label!==node.type"> {{ node.label }}</small><i v-if="node.range"> L{{ node.range.startLine }}</i></button></div><ul v-if="expanded"><AstNode v-for="child in node.children" :key="child.id" :node="child" :depth="depth+1" @locate="emit('locate',$event)" /></ul></li></template>
