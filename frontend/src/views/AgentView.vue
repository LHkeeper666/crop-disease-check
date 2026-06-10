<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import {
  mockEnvironmentData,
  mockEnergyData,
  mockGrowthMetrics,
  mockGreenhouseMeta,
  mockGridHeatmap,
  mockWorkOrders,
  mockStatsOverview,
  mockDailyReports,
} from '../mock/data'

// ============ Agriculture Expert System Prompt ============
const AGRI_EXPERT_SYSTEM_PROMPT = `你是一位资深农业遥测专家 AI 助手，隶属于 TreeForge 智慧农业遥测平台。你的知识涵盖以下领域：

【核心专长】
- 作物病虫害识别与防治（番茄晚疫病、白粉病、灰霉病、霜霉病、红蜘蛛、蚜虫、螟虫、白粉虱等）
- 农业环境监测与调控（温度、湿度、土壤水分、光照、CO₂浓度）
- 土壤养分分析（N/P/K、pH值、EC电导率）
- 温室智能化管理（通风、灌溉、施肥策略）
- 农药与肥料使用规范（安全间隔期、配比建议）

【数据感知能力】
你可以访问以下遥测数据进行分析：
- 环境参数：空气温度、土壤湿度、空气湿度、光照强度
- 能耗数据：当前功耗与最大负载
- 生长指标：CO₂、土壤pH、EC、温度、N/P/K含量
- 温室元数据：区域编号、作物种类、定植日期、地理位置、面积
- 网格热力图：各区域风险评分与病虫害类型
- 工单系统：告警级别(PENDING/PROCESSING/DONE/IGNORED)、置信度、处理状态
- 历史统计：总上报数、日趋势、病害/虫害分布

【回答规范】
1. 使用中文回答，语气专业但易懂
2. 涉及数值时必须使用精确数据，格式如 "23.6°C"、"65.2%"
3. 对于病虫害问题，给出：风险等级、传播概率、推荐防治措施、安全用药建议
4. 对于环境异常，给出：原因分析、调控建议、预期改善时间
5. 必要时提供分级建议（紧急/重要/常规）
6. 回答末尾给出可执行的行动建议

【报告生成规范】
当用户要求生成日报/周报/报告时，请按以下结构输出：
1. 报告概览（日期、监测区域、整体健康评级）
2. 环境数据摘要（关键指标与趋势）
3. 病虫害态势（新增/存量、风险等级分布）
4. 工单处理情况（待处理/处理中/已完成）
5. 风险预警（高风险网格与建议措施）
6. 明日工作建议（基于数据趋势的预判）`

// ============ Report Generation ============
const isGeneratingReport = ref(false)

function buildReportPrompt(): string {
  const today = new Date().toISOString().slice(0, 10)
  const env = mockEnvironmentData
  const energy = mockEnergyData
  const growth = mockGrowthMetrics
  const meta = mockGreenhouseMeta
  const grid = mockGridHeatmap
  const orders = mockWorkOrders
  const stats = mockStatsOverview
  const recent = mockDailyReports.slice(0, 3)

  return `请基于以下实时遥测数据，生成今日(${today})的农情日报。

【温室信息】
- 区域编号：${meta.sectorId}
- 作物种类：${meta.cropSpecies}
- 定植日期：${meta.plantingDate}
- 地理位置：${meta.location}
- 面积：${meta.area}
- 状态：${meta.status}

【环境数据】
- 空气温度：${env.airTemp.value}${env.airTemp.unit}（状态：${env.airTemp.status}）
- 土壤湿度：${env.soilMoisture.value}${env.soilMoisture.unit}（状态：${env.soilMoisture.status}）
- 空气湿度：${env.humidity.value}${env.humidity.unit}（状态：${env.humidity.status}）
- 光照强度：${env.lightLevel.value}${env.lightLevel.unit}（状态：${env.lightLevel.status}）

【能耗】
- 当前功耗：${energy.current} ${energy.unit} / ${energy.max} ${energy.unit}

【生长指标】
${growth.map(m => `- ${m.label}：${m.value} ${m.unit}`).join('\n')}

【网格热力图风险评分】
${grid.map(g => `- 网格 ${g.label}：风险 ${g.score}${g.pest ? '，检测到 ' + g.pest + '（类型：' + g.type + '）' : ''}`).join('\n')}

【工单状态】
${orders.map(o => `- ${o.title} | 级别：${o.severity} | 状态：${o.status} | 置信度：${(o.confidence * 100).toFixed(0)}%`).join('\n')}

【近期统计】
- 累计上报：${stats.totalReports} 次
- 今日上报：${stats.todayReports} 次
- 待审核：${stats.pendingAudit} 项
- 已处理：${stats.processed} 项
- 高风险预警：${stats.highRiskAlerts} 项

【近3日趋势】
${recent.map(r => `- ${r.date}：识别 ${r.detections} 次，病害 ${r.disease}，虫害 ${r.pest}，处理率 ${(r.handledRate * 100).toFixed(0)}%`).join('\n')}

请按照报告生成规范，输出完整的今日农情日报。`
}

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
  '生成今日农情日报',
  '分析Grid-B3红蜘蛛风险并给出防治方案',
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

async function sendMessage(text?: string, withSystemPrompt = false) {
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

    const apiMessages = withSystemPrompt
      ? [
          { role: 'system', content: AGRI_EXPERT_SYSTEM_PROMPT },
          { role: 'user', content },
        ]
      : [{ role: 'user', content }]

    const response = await fetch(provider.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey.value}`,
      },
      body: JSON.stringify({
        model,
        max_tokens: 2048,
        messages: apiMessages,
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

async function generateDailyReport() {
  if (isGeneratingReport.value) return
  isGeneratingReport.value = true
  const prompt = buildReportPrompt()
  await sendMessage(prompt, true)
  isGeneratingReport.value = false
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
      <div class="flex gap-2">
        <button
          class="px-4 py-1.5 rounded-lg bg-gradient-to-r from-[#FF6A00] to-[#FFB300] text-[#0B0F19] text-sm font-bold hover:shadow-lg hover:shadow-[#FF6A00]/20 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="isGeneratingReport || isLoading || !isAgentConfigured()"
          @click="generateDailyReport"
        >
          {{ isGeneratingReport ? '生成中...' : '生成今日日报' }}
        </button>
        <button
          class="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-sm text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          @click="showSettings = !showSettings"
        >
          {{ showSettings ? '关闭设置' : 'API 设置' }}
        </button>
      </div>
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
              @click="q === '生成今日农情日报' ? generateDailyReport() : sendMessage(q)"
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
