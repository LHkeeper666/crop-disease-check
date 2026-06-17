<script setup lang="ts">
import { ref, nextTick } from 'vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import { usePageContextInjector } from '../composables/usePageContext'

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
const isMinimized = ref(false)
const isMaximized = ref(false)

// 拖拽状态
const isDragging = ref(false)
const dragOffsetX = ref(0)
const dragOffsetY = ref(0)
const panelX = ref(0)
const panelY = ref(0)

const getContext = usePageContextInjector()

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
  isMinimized.value = false
}

function close() {
  isOpen.value = false
  isMinimized.value = false
  // 清空聊天记录，下次打开是新对话
  messages.value = []
  conversationId.value = null
  inputText.value = ''
}

function minimize() {
  isMinimized.value = true
}

function restore() {
  isMinimized.value = false
}

function toggleMaximize() {
  isMaximized.value = !isMaximized.value
  // 最大化时重置位置
  if (isMaximized.value) {
    panelX.value = 0
    panelY.value = 0
  }
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

// 拖拽
function onHeaderMouseDown(e: MouseEvent) {
  if (isMaximized.value) return
  e.preventDefault()
  isDragging.value = true
  dragOffsetX.value = e.clientX - panelX.value
  dragOffsetY.value = e.clientY - panelY.value
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  panelX.value = e.clientX - dragOffsetX.value
  panelY.value = e.clientY - dragOffsetY.value
}

function onDragEnd() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
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
    const context = getContext() || undefined

    const res = await fetch('/api/agri-brain/chat', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({
        message: content,
        conversationId: conversationId.value,
        context,
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
    <!-- 最小化恢复按钮 -->
    <Transition name="restore-btn">
      <button
        v-if="isOpen && isMinimized"
        class="dave-restore-btn"
        @click="restore"
        title="恢复聊天窗口"
      >
        <img src="/images/pet-dave/dave_idle/idle_1.png" class="w-6 h-9" alt="Dave" style="object-fit: contain;" />
        <span class="dave-restore-badge">💬</span>
      </button>
    </Transition>

    <!-- 聊天窗口 -->
      <div
        v-if="isOpen && !isMinimized"
        class="dave-chat-panel"
        :class="{ 'dave-chat-maximized': isMaximized }"
        :style="isMaximized ? {} : { transform: `translate(${panelX}px, ${panelY}px)` }"
      >
        <!-- Header -->
        <div class="dave-chat-header" @mousedown="onHeaderMouseDown">
          <div class="flex items-center gap-2">
            <img src="/images/pet-dave/dave_idle/idle_1.png" class="w-5 h-8" alt="Dave" style="object-fit: contain;" />
            <div>
              <div class="text-sm font-bold text-white">智慧农业助手</div>
              <div class="text-[10px] text-slate-500 font-mono">SMART AGRICULTURAL ASSISTANT</div>
            </div>
          </div>
          <div class="flex items-center gap-1">
            <!-- 最小化 -->
            <button class="dave-chat-btn" @click="minimize" title="最小化">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M5 12h14" />
              </svg>
            </button>
            <!-- 最大化/还原 -->
            <button class="dave-chat-btn" @click="toggleMaximize" :title="isMaximized ? '还原' : '最大化'">
              <svg v-if="!isMaximized" class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <rect x="3" y="3" width="18" height="18" rx="2" />
              </svg>
              <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M8 3v3a2 2 0 01-2 2H3m18 0h-3a2 2 0 01-2-2V3m0 18v-3a2 2 0 012-2h3M3 16h3a2 2 0 012 2v3" />
              </svg>
            </button>
            <!-- 关闭 -->
            <button class="dave-chat-btn dave-chat-btn-close" @click="close" title="关闭">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
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
  </Teleport>
</template>

<style scoped>
/* 恢复按钮 */
.dave-restore-btn {
  position: fixed;
  right: 24px;
  bottom: 200px;
  z-index: 10000;
  width: 48px;
  height: 64px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.9);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
  transition: all 0.2s ease;
  position: relative;
}
.dave-restore-btn:hover {
  transform: scale(1.08);
  border-color: rgba(34, 197, 94, 0.4);
  box-shadow: 0 4px 24px rgba(34, 197, 94, 0.2);
}
.dave-restore-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  font-size: 14px;
  animation: badge-bounce 1.5s ease-in-out infinite;
}
@keyframes badge-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

/* 聊天面板 */
.dave-chat-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 10000;
  width: 420px;
  height: 560px;
  background: rgba(15, 23, 42, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  will-change: transform;
}

/* 最大化状态 */
.dave-chat-maximized {
  width: 50vw !important;
  height: 100vh !important;
  right: 0 !important;
  bottom: 0 !important;
  border-radius: 0 !important;
  transform: none !important;
}

.dave-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  cursor: move;
  user-select: none;
}

.dave-chat-btn {
  padding: 5px;
  border-radius: 6px;
  color: #64748b;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dave-chat-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}
.dave-chat-btn-close:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
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
.restore-btn-enter-active {
  animation: restore-in 0.3s ease-out;
}
.restore-btn-leave-active {
  animation: restore-in 0.2s ease-in reverse;
}
@keyframes restore-in {
  from { opacity: 0; transform: scale(0.8); }
  to { opacity: 1; transform: scale(1); }
}
</style>
