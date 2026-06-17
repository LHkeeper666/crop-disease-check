<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import DaveChatDialog from './DaveChatDialog.vue'

// --- State ---
const state = ref<'idle' | 'think' | 'success' | 'interact' | 'warn'>('idle')
const frame = ref(0)
const bubbleText = ref('')
const bubbleVisible = ref(false)
const isDragging = ref(false)
const petX = ref(0)
const petY = ref(0)
const chatDialog = ref<InstanceType<typeof DaveChatDialog>>()

// --- Chat callbacks ---
function onChatStart() {
  setState('think', true)
}

function onChatEnd() {
  setState('success', true)
}

// --- Frame config ---
const FRAMES: Record<string, number> = {
  idle: 15,
  think: 8,
  success: 8,
  interact: 8,
  warn: 8,
}

const FRAME_SPEED: Record<string, number> = {
  idle: 180,
  think: 200,
  success: 150,
  interact: 80,
  warn: 160,
}

// How long each state lasts before returning to idle (ms)
const STATE_DURATION: Record<string, number> = {
  success: 4000,
  interact: 4000,
  warn: 4000,
}

// File name mapping: state → frame index → filename
function getFramePath(state: string, index: number): string {
  if (state === 'idle') {
    return `/images/pet-dave/dave_idle/idle_${index + 1}.png`
  }
  // think/success/interact/warn use Chinese names: 图层 1.png ~ 图层 8.png
  return `/images/pet-dave/dave_${state}/图层 ${index + 1}.png`
}

// --- Messages ---
const IDLE_MESSAGES = [
  '歪比歪卜？歪比巴卜！',
  '你知道吗？把土豆雷埋在地里需要一点艺术细胞。',
  '今天的天气……非常适合种一株向日葵！',
  '别忘了给作物浇水哦～',
  '僵尸……哦不，害虫们今天很安静。',
  '向日葵在对你微笑呢！',
  '我觉得这块地需要更多阳光。',
  '培根冰淇淋，要不要来一口？',
]

const THINK_MESSAGES = [
  '等等，让我的大脑里那颗卷心菜转一转……',
  '哦！这批作物的病害数据比僵尸博士的脑子还复杂！',
  '嗯……让我想想……',
  '数据量有点大，我的平底锅需要冷却一下。',
]

const SUCCESS_MESSAGES = [
  '干得漂亮！这方案简直比我的培根冰淇淋还要完美！',
  '长势喜人！长势喜人啊！快给它浇点水！',
  '完美！植物们会感谢你的！',
  '哈哈！又搞定一个！',
]

const WARN_MESSAGES = [
  '一大波僵尸……哦不，一大波害虫正在接近！！',
  '警告！这里的湿度不对劲，植物们在向我抗议！',
  '快！这里有情况！需要立即处理！',
  '植物在呼救！你听到了吗？',
]

const INTERACT_MESSAGES = [
  '嘿！别戳我，去戳那些可恶的害虫！',
  '因为我疯了！……哦不对，你找我有事吗？',
  '哎呀！轻点轻点！',
  '你在看我的平底锅吗？它可是传家宝！',
]

const MSG_MAP: Record<string, string[]> = {
  idle: IDLE_MESSAGES,
  think: THINK_MESSAGES,
  success: SUCCESS_MESSAGES,
  warn: WARN_MESSAGES,
  interact: INTERACT_MESSAGES,
}

// --- Animation ---
let animTimer: ReturnType<typeof setInterval> | null = null
let bubbleTimer: ReturnType<typeof setTimeout> | null = null
let idleTimer: ReturnType<typeof setInterval> | null = null
let returnTimer: ReturnType<typeof setTimeout> | null = null

// 预加载的图片缓存：state → Image[]
const frameCache: Record<string, HTMLImageElement[]> = {}
const petImgRef = ref<HTMLImageElement | null>(null)

function preloadFrames() {
  for (const s of Object.keys(FRAMES)) {
    frameCache[s] = []
    for (let i = 0; i < FRAMES[s]; i++) {
      const img = new Image()
      img.src = getFramePath(s, i)
      frameCache[s].push(img)
    }
  }
}

function showFrame() {
  const img = petImgRef.value
  const cached = frameCache[state.value]?.[frame.value]
  if (img && cached) {
    img.src = cached.src
  }
}

function startAnimation() {
  stopAnimation()
  showFrame()
  const speed = FRAME_SPEED[state.value]
  animTimer = setInterval(() => {
    frame.value = (frame.value + 1) % FRAMES[state.value]
    showFrame()
  }, speed)
}

function stopAnimation() {
  if (animTimer) {
    clearInterval(animTimer)
    animTimer = null
  }
}

function showBubble(msg?: string, duration = 4000) {
  const messages = MSG_MAP[state.value]
  bubbleText.value = msg || messages[Math.floor(Math.random() * messages.length)]
  bubbleVisible.value = true
  if (bubbleTimer) clearTimeout(bubbleTimer)
  bubbleTimer = setTimeout(() => {
    bubbleVisible.value = false
  }, duration)
}

function setState(s: typeof state.value, showMsg = true) {
  if (state.value === s && s !== 'interact') return
  state.value = s
  frame.value = 0
  startAnimation()
  if (showMsg) showBubble()

  // Return to idle after non-idle states (except think, which is event-driven)
  // Skip if dragging — drag end handles the return
  if (s !== 'idle' && s !== 'think' && !isDragging.value) {
    if (returnTimer) clearTimeout(returnTimer)
    const duration = STATE_DURATION[s] || 3000
    returnTimer = setTimeout(() => {
      if (state.value === s) {
        state.value = 'idle'
        frame.value = 0
        startAnimation()
      }
    }, duration)
  }
}

function returnToIdle() {
  state.value = 'idle'
  frame.value = 0
  startAnimation()
}

// --- Idle random talk ---
function startIdleTalk() {
  idleTimer = setInterval(() => {
    if (state.value === 'idle') {
      showBubble()
    }
  }, 10000)
}

// --- Mouse interaction ---
function onClick() {
  if (hasDragged) return
  chatDialog.value?.open()
}

// --- Drag ---
let dragStartX = 0
let dragStartY = 0
let startPetX = 0
let startPetY = 0
let hasDragged = false

function onDragStart(e: MouseEvent) {
  e.preventDefault()
  hasDragged = false
  isDragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  startPetX = petX.value
  startPetY = petY.value
  // Cancel any pending return-to-idle, keep interact alive
  if (returnTimer) { clearTimeout(returnTimer); returnTimer = null }
  if (state.value !== 'think' && state.value !== 'success' && state.value !== 'warn') {
    setState('interact', true)
  }
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    hasDragged = true
  }
  if (hasDragged) {
    petX.value = startPetX - dx
    petY.value = startPetY + dy
  }
}

function onDragEnd() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  // Return to idle immediately, but keep bubble visible for 3s
  if (state.value === 'interact') {
    returnToIdle()
    // Keep current bubble text visible
    bubbleVisible.value = true
    if (bubbleTimer) clearTimeout(bubbleTimer)
    bubbleTimer = setTimeout(() => {
      bubbleVisible.value = false
    }, 3000)
  }
}

// --- Public API (emit events from parent) ---
function triggerThink() { setState('think') }
function triggerSuccess() { setState('success') }
function triggerWarn() { setState('warn') }

defineExpose({ triggerThink, triggerSuccess, triggerWarn })

// --- Lifecycle ---
onMounted(() => {
  preloadFrames()
  startAnimation()
  startIdleTalk()
})

onUnmounted(() => {
  stopAnimation()
  if (bubbleTimer) clearTimeout(bubbleTimer)
  if (returnTimer) clearTimeout(returnTimer)
  if (idleTimer) clearInterval(idleTimer)
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
})
</script>

<template>
  <div
    class="dave-pet-wrapper"
    :style="{ right: `${24 + petX}px`, bottom: `${24 - petY}px` }"
  >
    <!-- Bubble -->
    <Transition name="bubble">
      <div v-if="bubbleVisible" class="dave-bubble">
        <span class="dave-bubble-text">{{ bubbleText }}</span>
      </div>
    </Transition>

    <!-- Pet Image -->
    <div
      class="dave-pet-body"
      @mousedown.left="onDragStart"
      @click="onClick"
    >
      <img
        ref="petImgRef"
        alt="Dave"
        class="dave-pet-img"
        draggable="false"
      />
    </div>

    <!-- Chat Dialog -->
    <DaveChatDialog ref="chatDialog" :on-chat-start="onChatStart" :on-chat-end="onChatEnd" />
  </div>
</template>

<style scoped>
.dave-pet-wrapper {
  position: fixed;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  pointer-events: none;
  user-select: none;
}

.dave-pet-body {
  pointer-events: auto;
  cursor: grab;
  transition: transform 0.2s ease;
}

.dave-pet-body:active {
  cursor: grabbing;
}

.dave-pet-hover {
  transform: scale(1.05);
}

.dave-pet-img {
  width: auto;
  height: 160px;
  transform: scaleX(-1);
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.4));
}

/* Bubble */
.dave-bubble {
  pointer-events: auto;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  padding: 10px 14px;
  max-width: 220px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  position: relative;
  margin-bottom: 4px;
}

.dave-bubble::after {
  content: '';
  position: absolute;
  bottom: -6px;
  right: 20px;
  width: 12px;
  height: 12px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-top: none;
  border-left: none;
  transform: rotate(45deg);
}

.dave-bubble-text {
  font-size: 13px;
  color: #1a1a1a;
  line-height: 1.5;
  font-family: -apple-system, 'Microsoft YaHei', sans-serif;
}

/* Bubble transition */
.bubble-enter-active {
  animation: bubble-in 0.3s ease-out;
}
.bubble-leave-active {
  animation: bubble-in 0.2s ease-in reverse;
}

@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
