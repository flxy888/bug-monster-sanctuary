import { ref } from 'vue'
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker'

// Shared because Monaco's editor services pool workers across editor instances.
export const workerState = ref('waiting')
self.MonacoEnvironment = {
  getWorker() {
    const worker = new EditorWorker()
    workerState.value = 'starting'
    worker.addEventListener('message', () => { workerState.value = 'ready' }, { once: true })
    worker.addEventListener('error', () => { workerState.value = 'error' })
    return worker
  },
}
