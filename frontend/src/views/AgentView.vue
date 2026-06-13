<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import MarkdownRenderer from '../components/MarkdownRenderer.vue'

const AGRI_EXPERT_SYSTEM_PROMPT = `你是一位资深农业遥测专家 AI 助手，隶属于 TreeForge 智慧农业遥测平台。`

interface ToolCallInfo {
  toolCallId: string
  name: string
  status: 'calling' | 'done'
  result?: string
}

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  toolCalls?: ToolCallInfo[]
}

interface Conversation {
  id: string
  title: string
  updatedAt: string
}

const messages = ref<Message[]>([])
const inputText = ref('')
const isLoading = ref(false)
const showSettings = ref(false)
const chatContainerRef = ref<HTMLDivElement>()
const conversationId = ref<string | null>(null)
const conversations = ref<Conversation[]>([])
const showHistory = ref(false)

// 获取认证 header
function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('treeforge_token')
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

// Config state
const selectedProvider = ref('')
const selectedModel = ref('')
const apiKey = ref('')
const isValidating = ref(false)
const validationResult = ref<{ success: boolean; message: string } | null>(null)
const isSaving = ref(false)

const providers = [
  { id: 'deepseek', name: 'DeepSeek', models: ['deepseek-chat', 'deepseek-v4-flash', 'deepseek-v4-pro'] },
  { id: 'xiaomi-mimo', name: 'Xiaomi MiMo', models: ['mimo-v2.5-pro', 'mimo-v2.5'] },
]

const availableModels = computed(() => {
  const p = providers.find(p => p.id === selectedProvider.value)
  return p ? p.models : []
})

const presetQuestions = [
  '分析当前园区病虫害风险',
  '查看今日待处理工单',
  '总结近7天检测数据趋势',
  '推荐当前应采取的防护措施',
]

// Load config from backend
async function loadConfig() {
  try {
    const res = await fetch('/api/agri-brain/config', { headers: getAuthHeaders() })
    const data = await res.json()
    if (data.code === 200 && data.data) {
      selectedProvider.value = data.data.provider || ''
      selectedModel.value = data.data.model || ''
      if (data.data.hasApiKey) {
        apiKey.value = '' // Don't fill in the masked key
      }
    }
  } catch (e) {
    console.error('加载配置失败', e)
  }
}

// Save config to backend
async function saveConfig() {
  if (!selectedModel.value) {
    validationResult.value = { success: false, message: '请选择模型' }
    return
  }

  isSaving.value = true
  validationResult.value = null

  try {
    const body: Record<string, string> = {
      provider: selectedProvider.value,
      model: selectedModel.value,
    }
    if (apiKey.value.trim()) {
      body.apiKey = apiKey.value.trim()
    }

    const res = await fetch('/api/agri-brain/config', {
      method: 'PUT',
      headers: getAuthHeaders(),
      body: JSON.stringify(body),
    })
    const data = await res.json()

    if (data.code === 200) {
      validationResult.value = { success: true, message: '配置已保存' }
      showSettings.value = false
      apiKey.value = '' // Clear input after save
    } else {
      validationResult.value = { success: false, message: data.message || '保存失败' }
    }
  } catch (e: any) {
    validationResult.value = { success: false, message: '保存失败: ' + e.message }
  } finally {
    isSaving.value = false
  }
}

// Validate config
async function validateConfig() {
  if (!apiKey.value.trim() || !selectedModel.value) {
    validationResult.value = { success: false, message: '请填写 API Key 并选择模型' }
    return
  }

  isValidating.value = true
  validationResult.value = null

  try {
    const res = await fetch('/api/agri-brain/config/validate', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ apiKey: apiKey.value.trim(), model: selectedModel.value }),
    })
    const data = await res.json()

    if (data.code === 200) {
      validationResult.value = { success: true, message: '配置有效' }
    } else {
      validationResult.value = { success: false, message: data.message || '校验失败' }
    }
  } catch (e: any) {
    validationResult.value = { success: false, message: '校验失败: ' + e.message }
  } finally {
    isValidating.value = false
  }
}

// Load conversation history list
async function loadConversations() {
  try {
    const res = await fetch('/api/agri-brain/history', { headers: getAuthHeaders() })
    const data = await res.json()
    if (data.code === 200 && data.data?.records) {
      conversations.value = data.data.records
    }
  } catch (e) {
    console.error('加载对话列表失败', e)
  }
}

// Load messages for a specific conversation
async function loadConversationMessages(convId: string) {
  try {
    const res = await fetch(`/api/agri-brain/history?conversationId=${convId}`, { headers: getAuthHeaders() })
    const data = await res.json()
    if (data.code === 200 && data.data) {
      messages.value = data.data.map((m: any) => ({
        id: m.id,
        role: m.role.toLowerCase(),
        content: m.content,
        timestamp: new Date(m.createdAt),
      }))
      conversationId.value = convId
      showHistory.value = false
      nextTick(() => scrollToBottom())
    }
  } catch (e) {
    console.error('加载对话消息失败', e)
  }
}

// New conversation
function newConversation() {
  conversationId.value = null
  messages.value = []
}

// Select conversation from history
function selectConversation(conv: Conversation) {
  loadConversationMessages(conv.id)
}

// Send message with SSE streaming
async function sendMessage(text?: string) {
  const content = text || inputText.value.trim()
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

  await nextTick()
  scrollToBottom()

  // Create placeholder for assistant message
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

    if (!res.ok) {
      throw new Error(`请求失败: ${res.status}`)
    }

    const reader = res.body?.getReader()
    if (!reader) throw new Error('无法读取响应流')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // Keep incomplete line in buffer

      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue

        // 处理 SSE 格式: "data: {...}" 或 "data:{...}"
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
          } else if (event.type === 'tool_call' && event.toolCallId) {
            // 工具调用开始
            if (!messages.value[assistantMsgIndex].toolCalls) {
              messages.value[assistantMsgIndex].toolCalls = []
            }
            messages.value[assistantMsgIndex].toolCalls.push({
              toolCallId: event.toolCallId,
              name: event.toolName || 'unknown',
              status: 'calling',
            })
            nextTick(() => scrollToBottom())
          } else if (event.type === 'tool_result' && event.toolCallId) {
            // 工具执行完成
            const toolCalls = messages.value[assistantMsgIndex].toolCalls
            if (toolCalls) {
              const tc = toolCalls.find(t => t.toolCallId === event.toolCallId)
              if (tc) {
                tc.status = 'done'
                tc.result = event.content
              }
            }
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
    nextTick(() => scrollToBottom())
  }
}

function scrollToBottom() {
  if (chatContainerRef.value) {
    chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight
  }
}

function getToolDisplayName(name: string): string {
  const toolNames: Record<string, string> = {
    work_order: '工单数据',
  }
  return toolNames[name] || name
}

onMounted(() => {
  loadConfig()
  loadConversations()
})
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">智慧大脑</h1>
        <p class="text-xs text-slate-500 font-mono">AI-POWERED AGRICULTURAL ASSISTANT</p>
      </div>
      <div class="flex gap-2">
        <button
          class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          @click="showHistory = !showHistory; showSettings = false"
        >
          {{ showHistory ? '关闭历史' : '历史对话' }}
        </button>
        <button
          class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          @click="newConversation"
        >
          新建对话
        </button>
        <button
          class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          @click="showSettings = !showSettings; showHistory = false"
        >
          {{ showSettings ? '关闭设置' : 'API 设置' }}
        </button>
      </div>
    </div>

    <!-- History Panel -->
    <GlassCard v-if="showHistory" class="shrink-0 max-h-60 overflow-y-auto">
      <div class="text-xs text-slate-400 tracking-wider mb-3">历史对话</div>
      <div v-if="conversations.length === 0" class="text-sm text-slate-500">暂无历史对话</div>
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="px-3 py-2 rounded-lg hover:bg-white/5 cursor-pointer transition-colors mb-1"
        :class="conversationId === conv.id ? 'bg-white/10 border border-white/10' : ''"
        @click="selectConversation(conv)"
      >
        <div class="text-sm text-white truncate">{{ conv.title }}</div>
        <div class="text-[10px] text-slate-500 font-mono">{{ conv.updatedAt }}</div>
      </div>
    </GlassCard>

    <!-- Agent Settings -->
    <GlassCard v-if="showSettings" class="shrink-0">
      <div class="text-xs text-slate-400 tracking-wider mb-4">智能体配置</div>

      <div class="grid grid-cols-3 gap-4 mb-4">
        <!-- Provider -->
        <div>
          <label class="block text-[10px] text-slate-500 mb-1.5 font-mono uppercase tracking-wider">供应商</label>
          <select
            v-model="selectedProvider"
            class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50 select-dark"
            @change="selectedModel = ''"
          >
            <option value="" disabled>选择供应商</option>
            <option v-for="p in providers" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>

        <!-- Model -->
        <div>
          <label class="block text-[10px] text-slate-500 mb-1.5 font-mono uppercase tracking-wider">模型</label>
          <select
            v-model="selectedModel"
            class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white text-sm focus:outline-none focus:border-cyber-green/50 select-dark"
            :disabled="!selectedProvider"
          >
            <option value="" disabled>选择模型</option>
            <option v-for="m in availableModels" :key="m" :value="m">{{ m }}</option>
          </select>
        </div>

        <!-- API Key -->
        <div>
          <label class="block text-[10px] text-slate-500 mb-1.5 font-mono uppercase tracking-wider">API Key（可选）</label>
          <input
            v-model="apiKey"
            type="password"
            placeholder="留空使用后端默认配置"
            class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50"
            @input="validationResult = null"
          />
        </div>
      </div>

      <!-- Validation result -->
      <div v-if="validationResult" class="mb-4 px-3 py-2 rounded-lg text-xs font-mono" :class="validationResult.success ? 'bg-cyber-green/10 text-cyber-green border border-cyber-green/20' : 'bg-sakura/10 text-sakura border border-sakura/20'">
        {{ validationResult.message }}
      </div>

      <!-- Action buttons -->
      <div class="flex gap-3">
        <button
          class="px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-sm text-slate-400 hover:text-white hover:bg-white/10 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="isValidating || !apiKey.trim()"
          @click="validateConfig"
        >
          {{ isValidating ? '校验中...' : '校验配置' }}
        </button>
        <button
          class="px-4 py-2 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="isSaving || !selectedModel"
          @click="saveConfig"
        >
          {{ isSaving ? '保存中...' : '保存配置' }}
        </button>
      </div>
    </GlassCard>

    <!-- Chat Area -->
    <GlassCard class="flex-1 min-h-0 flex flex-col">
      <!-- Messages -->
      <div
        ref="chatContainerRef"
        class="flex-1 min-h-0 overflow-y-auto space-y-4 mb-4"
      >
        <!-- Empty state -->
        <div v-if="messages.length === 0" class="h-full flex flex-col items-center justify-center">
          <div class="w-16 h-16 rounded-2xl bg-gradient-to-br from-cyber-green to-cyber-green-dark flex items-center justify-center mb-4">
            <svg class="w-8 h-8 text-white" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
              <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
            </svg>
          </div>
          <h3 class="text-lg font-bold text-white mb-2">智慧农业助手</h3>
          <p class="text-sm text-slate-400 text-center max-w-md mb-6">
            我可以帮您分析园区数据、解读工单信息、提供防治建议。直接输入问题或选择下方快捷问题开始对话。
          </p>
          <div class="grid grid-cols-2 gap-2 w-full max-w-lg">
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
          class="flex gap-3"
          :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <!-- AI avatar -->
          <div v-if="msg.role === 'assistant'" class="w-8 h-8 rounded-lg bg-cyber-green/10 flex items-center justify-center shrink-0">
            <svg class="w-4 h-4 text-cyber-green" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
            </svg>
          </div>

          <!-- Message bubble -->
          <div
            class="max-w-[70%] px-4 py-3 rounded-2xl text-sm leading-relaxed"
            :class="msg.role === 'user'
              ? 'bg-cyber-green/10 border border-cyber-green/20 text-white whitespace-pre-wrap'
              : 'bg-white/5 border border-white/10 text-slate-300'"
          >
            <template v-if="msg.role === 'user'">
              {{ msg.content }}
            </template>
            <template v-else>
              <!-- Tool calls status -->
              <div v-if="msg.toolCalls && msg.toolCalls.length > 0" class="mb-3 space-y-2">
                <div
                  v-for="tc in msg.toolCalls"
                  :key="tc.toolCallId"
                  class="flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-mono"
                  :class="tc.status === 'calling' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' : 'bg-cyber-green/10 text-cyber-green border border-cyber-green/20'"
                >
                  <svg v-if="tc.status === 'calling'" class="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  <svg v-else class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path d="M5 13l4 4L19 7" />
                  </svg>
                  <span>{{ tc.status === 'calling' ? '正在查询' : '查询完成' }}: {{ getToolDisplayName(tc.name) }}</span>
                </div>
              </div>
              <MarkdownRenderer :content="msg.content" />
            </template>
          </div>

          <!-- User avatar -->
          <div v-if="msg.role === 'user'" class="w-8 h-8 rounded-lg bg-sunset-from/10 flex items-center justify-center shrink-0">
            <span class="text-xs font-bold text-sunset">U</span>
          </div>
        </div>

        <!-- Loading indicator -->
        <div v-if="isLoading && messages[messages.length - 1]?.content === ''" class="flex gap-3 justify-start">
          <div class="w-8 h-8 rounded-lg bg-cyber-green/10 flex items-center justify-center shrink-0">
            <svg class="w-4 h-4 text-cyber-green" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
            </svg>
          </div>
          <div class="px-4 py-3 rounded-2xl bg-white/5 border border-white/10">
            <div class="flex gap-1">
              <div class="w-2 h-2 rounded-full bg-slate-500 animate-bounce" style="animation-delay: 0ms" />
              <div class="w-2 h-2 rounded-full bg-slate-500 animate-bounce" style="animation-delay: 150ms" />
              <div class="w-2 h-2 rounded-full bg-slate-500 animate-bounce" style="animation-delay: 300ms" />
            </div>
          </div>
        </div>
      </div>

      <!-- Input area -->
      <div class="shrink-0 flex gap-3">
        <input
          v-model="inputText"
          type="text"
          placeholder="输入问题，例如：分析当前园区病虫害风险"
          class="flex-1 px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50 focus:ring-1 focus:ring-cyber-green/20 transition-all"
          @keyup.enter="sendMessage()"
        />
        <button
          class="px-6 py-3 rounded-xl bg-gradient-to-r from-cyber-green to-cyber-green-dark text-white text-sm font-medium hover:shadow-lg hover:shadow-cyber-green/20 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!inputText.trim() || isLoading"
          @click="sendMessage()"
        >
          发送
        </button>
      </div>
    </GlassCard>
  </div>
</template>
