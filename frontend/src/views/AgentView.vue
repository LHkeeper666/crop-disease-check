<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import GlassCard from '../components/GlassCard.vue'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

interface ProviderConfig {
  id: string
  name: string
  website: string
  endpoint: string
  displayEndpoint: string
  apiFormat: string
  authField: string
  models: { id: string; name: string }[]
}

const providers: ProviderConfig[] = [
  {
    id: 'deepseek',
    name: 'DeepSeek',
    website: 'https://platform.deepseek.com',
    endpoint: '/proxy/deepseek/v1/chat/completions',
    displayEndpoint: 'https://api.deepseek.com/v1/chat/completions',
    apiFormat: 'OpenAI Chat Completions',
    authField: 'Authorization (Bearer)',
    models: [
      { id: 'deepseek-v4-flash', name: 'DeepSeek V4 Flash' },
      { id: 'deepseek-v4-pro', name: 'DeepSeek V4 Pro' },
    ],
  },
  {
    id: 'xiaomi-mimo',
    name: 'Xiaomi MiMo',
    website: 'https://platform.xiaomimimo.com',
    endpoint: '/proxy/xiaomi/v1/chat/completions',
    displayEndpoint: 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions',
    apiFormat: 'OpenAI Chat Completions',
    authField: 'Authorization (Bearer)',
    models: [
      { id: 'mimo-v2.5-pro', name: 'MiMo V2.5 Pro' },
      { id: 'mimo-v2.5', name: 'MiMo V2.5' },
    ],
  },
]

const messages = ref<Message[]>([])
const inputText = ref('')
const isLoading = ref(false)
const showSettings = ref(false)
const chatContainerRef = ref<HTMLDivElement>()

// Agent config state
// API key is never persisted to localStorage for security
localStorage.removeItem('agent_api_key')
const selectedProvider = ref(localStorage.getItem('agent_provider') || '')
const selectedModel = ref(localStorage.getItem('agent_model') || '')
const apiKey = ref('')
const isValidating = ref(false)
const validationResult = ref<{ success: boolean; message: string } | null>(null)

const currentProvider = computed(() => providers.find(p => p.id === selectedProvider.value))
const availableModels = computed(() => currentProvider.value?.models || [])

const presetQuestions = [
  '分析当前园区病虫害风险',
  '查看今日待处理工单',
  '总结近7天检测数据趋势',
  '推荐当前应采取的防护措施',
]

function onProviderChange() {
  selectedModel.value = ''
  validationResult.value = null
}

function onModelChange() {
  validationResult.value = null
}

async function validateConfig() {
  if (!selectedProvider.value || !selectedModel.value || !apiKey.value.trim()) {
    validationResult.value = { success: false, message: '请填写完整的配置信息' }
    return
  }

  isValidating.value = true
  validationResult.value = null

  try {
    const provider = currentProvider.value!
    const response = await fetch(provider.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey.value}`,
      },
      body: JSON.stringify({
        model: selectedModel.value,
        max_tokens: 10,
        messages: [{ role: 'user', content: 'Hi' }],
      }),
    })

    if (response.ok) {
      validationResult.value = {
        success: true,
        message: `配置校验成功：${provider.name} - ${selectedModel.value}`,
      }
    } else {
      const error = await response.json().catch(() => ({}))
      validationResult.value = {
        success: false,
        message: `校验失败：${error.error?.message || response.statusText}`,
      }
    }
  } catch (err: any) {
    validationResult.value = {
      success: false,
      message: `连接失败：${err.message}`,
    }
  } finally {
    isValidating.value = false
  }
}

function saveAgentConfig() {
  if (!selectedProvider.value || !selectedModel.value || !apiKey.value.trim()) {
    validationResult.value = { success: false, message: '请填写完整的配置信息' }
    return
  }

  localStorage.setItem('agent_provider', selectedProvider.value)
  localStorage.setItem('agent_model', selectedModel.value)
  // API key is NOT persisted to localStorage for security

  validationResult.value = { success: true, message: 'Agent 配置已保存（API Key 仅在本次会话有效）' }
  showSettings.value = false
}

function isAgentConfigured(): boolean {
  return !!(selectedProvider.value && selectedModel.value && apiKey.value.trim())
}

async function sendMessage(text?: string) {
  const content = text || inputText.value.trim()
  if (!content) return

  if (!isAgentConfigured()) {
    const errorMsg: Message = {
      id: Date.now().toString(),
      role: 'assistant',
      content: '请先配置智能体（点击右上角"API 设置"），配置供应商、模型和 API Key 后才能使用。',
      timestamp: new Date(),
    }
    messages.value.push(errorMsg)
    nextTick(() => scrollToBottom())
    return
  }

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

  try {
    const provider = currentProvider.value!
    const model = selectedModel.value

    const response = await fetch(provider.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey.value}`,
      },
      body: JSON.stringify({
        model,
        max_tokens: 1024,
        messages: [{ role: 'user', content }],
      }),
    })

    if (!response.ok) {
      const error = await response.json().catch(() => ({}))
      throw new Error(error.error?.message || `API 请求失败: ${response.statusText}`)
    }

    const data = await response.json()
    const aiMsg: Message = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: data.choices?.[0]?.message?.content || '未收到有效回复',
      timestamp: new Date(),
    }
    messages.value.push(aiMsg)
  } catch (err: any) {
    const errorMsg: Message = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: `请求失败：${err.message}`,
      timestamp: new Date(),
    }
    messages.value.push(errorMsg)
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
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">智慧大脑</h1>
        <p class="text-xs text-slate-500 font-mono">AI-POWERED AGRICULTURAL ASSISTANT</p>
      </div>
      <button
        class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
        @click="showSettings = !showSettings"
      >
        {{ showSettings ? '关闭设置' : 'API 设置' }}
      </button>
    </div>

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
            @change="onProviderChange"
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
            @change="onModelChange"
          >
            <option value="" disabled>选择模型</option>
            <option v-for="m in availableModels" :key="m.id" :value="m.id">{{ m.name }}</option>
          </select>
        </div>

        <!-- API Key -->
        <div>
          <label class="block text-[10px] text-slate-500 mb-1.5 font-mono uppercase tracking-wider">API Key</label>
          <input
            v-model="apiKey"
            type="password"
            placeholder="请输入 API Key"
            class="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white placeholder-slate-600 text-sm focus:outline-none focus:border-cyber-green/50"
            @input="validationResult = null"
          />
        </div>
      </div>

      <!-- Provider info -->
      <div v-if="currentProvider" class="glass rounded-lg p-3 mb-4">
        <div class="grid grid-cols-2 gap-2 text-[10px] font-mono">
          <div>
            <span class="text-slate-500">官网：</span>
            <a :href="currentProvider.website" target="_blank" class="text-cyber-green hover:underline">{{ currentProvider.website }}</a>
          </div>
          <div>
            <span class="text-slate-500">请求地址：</span>
            <span class="text-slate-400">{{ currentProvider.displayEndpoint }}</span>
          </div>
          <div>
            <span class="text-slate-500">API 格式：</span>
            <span class="text-slate-400">{{ currentProvider.apiFormat }}</span>
          </div>
          <div>
            <span class="text-slate-500">认证字段：</span>
            <span class="text-slate-400">{{ currentProvider.authField }}</span>
          </div>
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
          :disabled="isValidating"
          @click="validateConfig"
        >
          {{ isValidating ? '校验中...' : '校验配置' }}
        </button>
        <button
          class="px-4 py-2 rounded-xl bg-cyber-green/10 border border-cyber-green/20 text-cyber-green text-sm hover:bg-cyber-green/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!selectedProvider || !selectedModel || !apiKey.trim()"
          @click="saveAgentConfig"
        >
          保存 Agent 配置
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
            class="max-w-[70%] px-4 py-3 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap"
            :class="msg.role === 'user'
              ? 'bg-cyber-green/10 border border-cyber-green/20 text-white'
              : 'bg-white/5 border border-white/10 text-slate-300'"
          >
            {{ msg.content }}
          </div>

          <!-- User avatar -->
          <div v-if="msg.role === 'user'" class="w-8 h-8 rounded-lg bg-sunset-from/10 flex items-center justify-center shrink-0">
            <span class="text-xs font-bold text-sunset">U</span>
          </div>
        </div>

        <!-- Loading indicator -->
        <div v-if="isLoading" class="flex gap-3 justify-start">
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
