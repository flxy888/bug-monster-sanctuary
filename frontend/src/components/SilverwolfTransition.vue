<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import logo from '../assets/silverwolf/logo.png'
import runnerSheet from '../assets/silverwolf/runner-sheet.png'
defineOptions({ name:'SilverwolfTransition' })
const props=defineProps({ reducedMotion:Boolean })
const emit=defineEmits(['complete'])
const progress=ref(0), failed=ref(false), logoFailed=ref(false), skip=ref(null)
const dots=computed(()=>'.'.repeat(1+Math.floor(progress.value/14)%3))
const timer=computed(()=>`00:00:${String(Math.floor(progress.value/100*3.5)).padStart(2,'0')}`)
let frame=0, start=0, finished=false, previousFocus
function complete(){if(finished)return;finished=true;cancelAnimationFrame(frame);emit('complete')}
function key(e){if(e.key==='Escape'){e.preventDefault();e.stopImmediatePropagation();complete()}}
function tick(time){if(finished)return;if(!start)start=time;progress.value=Math.min(100,Math.floor((time-start)/(props.reducedMotion?150:3500)*100));if(progress.value>=100)complete();else frame=requestAnimationFrame(tick)}
onMounted(()=>{previousFocus=document.activeElement;skip.value?.focus();window.addEventListener('keydown',key,true);frame=requestAnimationFrame(tick)})
onBeforeUnmount(()=>{finished=true;cancelAnimationFrame(frame);window.removeEventListener('keydown',key,true);previousFocus?.focus?.()})
</script>
<template>
<section class="wolf-transition" :class="{'motion-off':reducedMotion}" role="dialog" aria-modal="true" aria-label="银狼主题过场">
  <div class="wolf-hud" aria-hidden="true"><div>PLAYER A<br><b>00000000</b></div><div>WORLD<br><b>1−1</b></div><div>THEME LINK<br><b>{{ timer }}</b></div></div>
  <button ref="skip" class="wolf-skip" @click="complete">跳过过场 <kbd>Esc</kbd></button>
  <div class="wolf-title"><img v-if="!logoFailed" :src="logo" alt="SILVERWOLF" @error="logoFailed=true;failed=true"/><h2 v-else>SILVERWOLF</h2></div>
  <div class="wolf-load"><span class="wolf-runner"><img :src="runnerSheet" alt="像素银狼跑步" @error="failed=true"/></span><div class="wolf-loading-word">LOADING<span>{{ dots }}</span></div><div class="wolf-progress" role="progressbar" aria-label="主题资源过渡" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100"><i v-for="n in 20" :key="n" :class="{filled:n<=Math.ceil(progress/5)}"></i></div><small>THEME INITIALIZATION · {{ progress }}%</small><p v-if="failed" role="status">部分视觉素材未加载，可跳过进入主题。</p></div>
  <p class="wolf-copyright">2026 STELLARON HUNTERS</p>
</section>
</template>
