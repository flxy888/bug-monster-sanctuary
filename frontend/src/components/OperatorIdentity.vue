<script setup>
import { nextTick, ref } from 'vue'
import { normalizeNickname } from '../operatorProfile'
const props = defineProps({ nickname: String, theme: String, busy: Boolean, warning: String })
const emit = defineEmits(['save', 'reset'])
const editing = ref(false), draft = ref(''), error = ref(''), input = ref(null)
let composing = false
async function begin() { draft.value=props.nickname; error.value=''; editing.value=true; await nextTick(); input.value?.focus(); input.value?.select() }
function cancel() { editing.value=false; error.value='' }
function save() {
  if (composing || props.busy) return
  try { const value=normalizeNickname(draft.value); emit('save', value); editing.value=false; error.value='' }
  catch(e) { error.value=e.message }
}
function keydown(e) {
  e.stopPropagation()
  if (e.isComposing || composing || e.keyCode===229) return
  if(e.key==='Enter'){e.preventDefault();save()}
  if(e.key==='Escape'){e.preventDefault();cancel()}
}
</script>
<template>
<div class="operator-identity" data-nickname-editor>
  <template v-if="!editing"><small class="operator-label">OPERATOR / 显示名</small><button class="nickname-button" :aria-label="'编辑显示名：'+nickname" title="编辑本地显示名" :disabled="busy" @click="begin">{{ nickname }} <span aria-hidden="true">✎</span></button></template>
  <div v-else class="nickname-edit"><label for="operator-nickname">显示名 · 最多20字符</label><input id="operator-nickname" ref="input" v-model="draft" maxlength="40" autocomplete="off" @compositionstart="composing=true" @compositionend="composing=false" @keydown="keydown"/><div><button type="button" :disabled="busy" @click="save">保存名字</button><button type="button" @click="cancel">取消</button></div><p v-if="error" role="alert">{{ error }}</p></div>
  <p v-if="warning" class="profile-warning" role="status">{{ warning }}</p>
  <button v-if="theme==='silverwolf'" class="restore-theme" :disabled="busy" @click="emit('reset')">恢复默认主题</button>
</div>
</template>
