<script setup lang="ts">
import { ref, nextTick } from 'vue'
import MarkdownRenderer from './MarkdownRenderer.vue'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

const messages = ref<Message[]>([])
const inputText = ref('')
const isLoading = ref(false)
const chatContainerRef = ref<HTMLDivElement>()
const conversationId = ref<string | null>(null)
const isOpen = ref(false)

const props = defineProps<{
  onChatStart?: () => void
  onChatEnd?: () => void
}>()

function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('treeforge_token')
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

const presetQuestions = [
  '分析当前园区病虫害风险',
  '查看今日待处理工单',
  '总结近7天检测数据趋势',
  '推荐当前应采取的防护措施',
]

function open() {
  isOpen.value = true
}

function close() {
  isOpen.value = false
}

function onEnter() {
  if (inputText.value.trim()) {
    sendMessage()
  }
}

function scrollToBottom() {
  if (chatContainerRef.value) {
    chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight
  }
}

async function sendMessage(text?: string) {
  const content = (text || inputText.value).replace(/[\r\n]/g, '').trim()
  if (!content || isLoading.value) return

  const userMsg: Message = {
    id: Date.now().toString(),
    role: 'user',
    content,
    timestamp: new Date(),
  }
  messages.value.push(userMsg)
  inputText.value = ''
  isLoading.value = true
  props.onChatStart?.()

  await nextTick()
  scrollToBottom()

  const assistantMsgIndex = messages.value.length
  messages.value.push({
    id: (Date.now() + 1).toString(),
    role: 'assistant',
    content: '',
    timestamp: new Date(),
  })

  try {
    const res = await fetch('/api/agri-brain/chat', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({
        message: content,
        conversationId: conversationId.value,
      }),
    })

    if (!res.ok) throw new Error(`请求失败: ${res.status}`)

    const reader = res.body?.getReader()
    if (!reader) throw new Error('无法读取响应流')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue

        let jsonStr = trimmedLine
        if (trimmedLine.startsWith('data:')) {
          jsonStr = trimmedLine.substring(5).trim()
        }

        if (!jsonStr || jsonStr === '[DONE]') continue

        try {
          const event = JSON.parse(jsonStr)
          if (event.type === 'token' && event.content && event.content !== 'null') {
            messages.value[assistantMsgIndex].content += event.content
            nextTick(() => scrollToBottom())
          } else if (event.type === 'done' && event.conversationId) {
            conversationId.value = event.conversationId
          } else if (event.type === 'error') {
            messages.value[assistantMsgIndex].content += `\n\n错误: ${event.content}`
          }
        } catch (e) {
          // Skip invalid JSON
        }
      }
    }
  } catch (err: any) {
    messages.value[assistantMsgIndex].content = `请求失败: ${err.message}`
  } finally {
    isLoading.value = false
    props.onChatEnd?.()
    nextTick(() => scrollToBottom())
  }
}

defineExpose({ open, close, isLoading })
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="isOpen" class="dave-chat-overlay" @click.self="close">
        <div class="dave-chat-panel">
          <!-- Header -->
          <div class="dave-chat-header">
            <div class="flex items-center gap-2">
              <img src="/images/pet-dave/dave_idle/idle_1.png" class="w-5 h-8" alt="Dave" style="object-fit: contain;" />
              <div>
                <div class="text-sm font-bold text-white">智慧农业助手</div>
                <div class="text-[10px] text-slate-500 font-mono">SMART AGRICULTURAL ASSISTANT</div>
              </div>
            </div>
            <button class="dave-chat-close" @click="close">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- Messages -->
          <div ref="chatContainerRef" class="dave-chat-messages">
            <!-- Empty state -->
            <div v-if="messages.length === 0" class="h-full flex flex-col items-center justify-center px-4">
              <img src="/images/pet-dave/dave_success/图层 4.png" class="w-14 h-20 mb-3" alt="Dave" style="object-fit: contain;" />
              <p class="text-sm text-slate-400 text-center mb-4">
                你好！我是智慧大脑，有什么农业问题尽管问我！
              </p>
              <div class="grid grid-cols-1 gap-2 w-full">
                <button
                  v-for="q in presetQuestions"
                  :key="q"
                  class="px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-xs text-slate-400 hover:text-white hover:bg-white/10 transition-colors text-left"
                  @click="sendMessage(q)"
                >
                  {{ q }}
                </button>
              </div>
            </div>

            <!-- Message list -->
            <div
              v-for="msg in messages"
              :key="msg.id"
              v-show="msg.content"
              class="flex gap-2 mb-3"
              :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
            >
              <div v-if="msg.role === 'assistant'" class="w-7 h-10 rounded-lg bg-cyber-green/10 flex items-center justify-center shrink-0 mt-1 overflow-hidden">
                <img src="/images/pet-dave/dave_idle/idle_1.png" class="w-4 h-7" alt="Dave" style="object-fit: contain;" />
              </div>
              <div
                class="max-w-[80%] px-3 py-2 rounded-xl text-xs leading-relaxed"
                :class="msg.role === 'user'
                  ? 'bg-cyber-green/10 border border-cyber-green/20 text-white whitespace-pre-wrap'
                  : 'bg-white/5 border border-white/10 text-slate-300'"
              >
                <MarkdownRenderer :content="msg.content" />
              </div>
            </div>

            <!-- Loading -->
            <div v-if="isLoading && messages[messages.length - 1]?.content === ''" class="flex gap-2 justify-start mb-3">
              <div class="w-7 h-10 rounded-lg bg-cyber-green/10 flex items-center justify-center shrink-0 mt-1 overflow-hidden">
                <img src="/images/pet-dave/dave_idle/idle_1.png" class="w-4 h-7" alt="Dave" style="object-fit: contain;" />
              </div>
              <div class="px-3 py-2 rounded-xl bg-white/5 border border-white/10">
                <div class="flex gap-1">
                  <div class="w-1.5 h-1.5 rounded-full bg-slate-500 animate-bounce" style="animation-delay: 0ms" />
                  <div class="w-1.5 h-1.5 rounded-full bg-slate-500 animate-bounce" style="animation-delay: 150ms" />
                  <div class="w-1.5 h-1.5 rounded-full bg-slate-500 animate-bounce" style="animation-delay: 300ms" />
                </div>
              </div>
            </div>
          </div>

          <!-- Input -->
          <div class="dave-chat-input">
            <input
              v-model="inputText"
              type="text"
              placeholder="输入问题..."
              class="flex-1 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-white placeholder-slate-600 text-xs focus:outline-none focus:border-cyber-green/50"
              @keydown.enter.exact.prevent="onEnter"
            />
            <button
              class="px-3 py-1.5 rounded-lg bg-cyber-green/20 border border-cyber-green/30 text-cyber-green text-xs hover:bg-cyber-green/30 transition-colors disabled:opacity-50"
              :disabled="!inputText.trim() || isLoading"
              @click="sendMessage()"
            >
              发送
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dave-chat-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dave-chat-panel {
  width: 480px;
  height: 600px;
  background: rgba(15, 23, 42, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.dave-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.dave-chat-close {
  padding: 4px;
  border-radius: 6px;
  color: #64748b;
  transition: all 0.15s;
}
.dave-chat-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.dave-chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
}

.dave-chat-input {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

/* Transition */
.dialog-enter-active {
  animation: dialog-in 0.25s ease-out;
}
.dialog-leave-active {
  animation: dialog-in 0.2s ease-in reverse;
}
@keyframes dialog-in {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
