<script setup>
import { ref, onMounted } from 'vue'
defineProps({ preview: { type: Object, required: true } })
defineEmits(['apply', 'cancel'])
const cancel = ref(null)
onMounted(() => cancel.value?.focus())
</script>
<template>
<div class="ai-repair-modal" role="dialog" aria-modal="true" aria-labelledby="ai-preview-title" @keydown.esc.stop.prevent="$emit('cancel')">
  <section class="ai-repair-preview">
    <header><h2 id="ai-preview-title">AI 修复预览</h2><button ref="cancel" @click="$emit('cancel')">取消</button></header>
    <p>{{ preview.summary }}</p>
    <p class="ai-validation">Java21静态解析通过 · 五规则问题 {{ preview.beforeIssueCount }} → {{ preview.afterIssueCount }} · AI 本身 0XP</p>
    <p>静态可解析仍需人工确认业务正确；应用只替换草稿。</p>
    <div class="ai-code-pair"><section><h3>原代码</h3><pre tabindex="0" aria-label="AI修复前源码">{{ preview.original }}</pre></section><section><h3>修复后代码</h3><pre tabindex="0" aria-label="AI修复后源码">{{ preview.sourceCode }}</pre></section></div>
    <footer><span>{{ preview.model }}</span><button :disabled="!preview.changed" @click="$emit('apply')">应用到草稿</button><button @click="$emit('cancel')">取消</button></footer>
  </section>
</div>
</template>
