<script setup lang="ts">
import { ref, computed } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import TiltCard from '../components/TiltCard.vue'

type Tab = 'pest' | 'disease'
const activeTab = ref<Tab>('pest')

// 虫害图鉴 — 文件名已是中文
const pestImages = [
  '000_稻纵卷叶螟', '001_稻螟蛉', '002_稻潜叶蝇', '003_亚洲玉米螟', '004_三化螟',
  '005_稻瘿蚊', '006_稻秆蝇', '007_褐飞虱', '008_白背飞虱', '009_小褐飞虱',
  '010_稻水象甲', '011_稻叶蝉', '012_谷物蓟马', '013_稻苞虫', '014_蛴螬',
  '015_蝼蛄', '016_金针虫', '017_白边蛾', '018_小地老虎', '019_大地老虎',
  '020_黄地老虎', '021_红蜘蛛', '022_玉米螟', '023_夜蛾', '024_蚜虫',
  '025_白星花金龟', '026_桃蛀螟', '027_长角麦蚜', '028_麦二叉蚜', '029_禾谷缢管蚜',
  '030_麦红吸浆虫', '031_麦圆叶爪螨', '032_长腿蜘蛛螨', '033_麦管蓟马', '034_麦叶蜂',
  '035_麦黑斑潜叶蝇', '036_甜菜潜叶花蝇', '037_跳甲', '038_甘蓝夜蛾', '039_甜菜夜蛾',
  '040_甜菜斑蝇', '041_草地螟', '042_甜菜点腹象甲', '043_桑树金龟子', '044_苜蓿叶象甲',
  '045_苜蓿夜蛾', '046_苜蓿盲蝽', '047_牧草盲椿', '048_蝗虫', '049_西班牙绿芜菁_斑蝥',
  '050_豆斑蝥', '051_斑蝥', '052_苜蓿斑蚜', '053_牛角花齿蓟马', '054_蓟马_牧草虫',
  '055_苜蓿广肩小蜂', '056_东方菜粉蝶', '057_绿盲蝽', '058_刺蛾', '061_葡萄短须螨',
  '062_十星瓢萤叶甲', '064_康氏粉蚧', '065_葡萄透翅蛾', '066_葡萄天蛾', '067_斑衣蜡蝉',
  '068_虎天牛', '069_大青叶蝉', '070_盲蝽', '071_温室白粉虱', '072_葡萄二星叶蝉',
  '073_柑橘凤蝶', '074_柑橘全爪螨', '076_吹绵蚧壳虫', '077_矢尖盾蚧', '078_红蜡蚧',
  '079_褐圆盾蚧', '081_堆蜡粉蚧', '082_柑橘刺粉蝨', '083_柑橘大实蝇', '084_柑橘小实蝇',
  '085_蜜柑大实蝇', '086_棉斜纹夜蛾', '087_枯叶夜蛾', '088_桔潜蛾姬小蜂', '089_橘蚜',
  '090_小桔蚜', '091_苹果绣线菊蚜', '092_芒果茶黄蓟马', '093_荔枝叶瘿蚊', '094_茶树白蛾蜡蝉',
  '095_青蛾蜡蝉', '096_芒果切叶象甲', '097_横线尾夜蛾', '098_芒果扁喙叶蝉', '099_脊胸天牛',
  '100_芒果果肉象甲', '101_叶蝉',
]

// 病害图鉴 — 英文文件名 → 中文名 + 作物
const diseaseEntries: { file: string; name: string; crop: string }[] = [
  { file: '01_Apple___Apple_scab', name: '黑星病', crop: '苹果' },
  { file: '02_Apple___Black_rot', name: '黑腐病', crop: '苹果' },
  { file: '03_Apple___Cedar_apple_rust', name: '雪松锈病', crop: '苹果' },
  { file: '04_Apple___healthy', name: '健康', crop: '苹果' },
  { file: '05_Blueberry___healthy', name: '健康', crop: '蓝莓' },
  { file: '06_Cherry___Powdery_mildew', name: '白粉病', crop: '樱桃' },
  { file: '07_Cherry___healthy', name: '健康', crop: '樱桃' },
  { file: '08_Corn___Cercospora_leaf_spot_Gray_leaf_spot', name: '灰斑病', crop: '玉米' },
  { file: '09_Corn___Common_rust', name: '普通锈病', crop: '玉米' },
  { file: '10_Corn___Northern_Leaf_Blight', name: '北方叶枯病', crop: '玉米' },
  { file: '11_Corn___healthy', name: '健康', crop: '玉米' },
  { file: '12_Grape___Black_rot', name: '黑腐病', crop: '葡萄' },
  { file: '13_Grape___Esca_(Black_Measles)', name: '黑麻病', crop: '葡萄' },
  { file: '14_Grape___Leaf_blight_(Isariopsis_Leaf_Spot)', name: '叶枯病', crop: '葡萄' },
  { file: '15_Grape___healthy', name: '健康', crop: '葡萄' },
  { file: '16_Orange___Haunglongbing_(Citrus_greening)', name: '黄龙病', crop: '柑橘' },
  { file: '17_Peach___Bacterial_spot', name: '细菌性斑点病', crop: '桃' },
  { file: '18_Peach___healthy', name: '健康', crop: '桃' },
  { file: '19_Pepper,_bell___Bacterial_spot', name: '细菌性斑点病', crop: '甜椒' },
  { file: '20_Pepper,_bell___healthy', name: '健康', crop: '甜椒' },
  { file: '21_Potato___Early_blight', name: '早疫病', crop: '马铃薯' },
  { file: '22_Potato___Late_blight', name: '晚疫病', crop: '马铃薯' },
  { file: '23_Potato___healthy', name: '健康', crop: '马铃薯' },
  { file: '24_Raspberry___healthy', name: '健康', crop: '树莓' },
  { file: '25_Soybean___healthy', name: '健康', crop: '大豆' },
  { file: '26_Squash___Powdery_mildew', name: '白粉病', crop: '南瓜' },
  { file: '27_Strawberry___Leaf_scorch', name: '叶焦病', crop: '草莓' },
  { file: '28_Strawberry___healthy', name: '健康', crop: '草莓' },
  { file: '29_Tomato___Bacterial_spot', name: '细菌性斑点病', crop: '番茄' },
  { file: '30_Tomato___Early_blight', name: '早疫病', crop: '番茄' },
  { file: '31_Tomato___Late_blight', name: '晚疫病', crop: '番茄' },
  { file: '32_Tomato___Leaf_Mold', name: '叶霉病', crop: '番茄' },
  { file: '33_Tomato___Septoria_leaf_spot', name: '斑枯病', crop: '番茄' },
  { file: '34_Tomato___Spider_mites_Two-spotted_spider_mite', name: '二斑叶螨', crop: '番茄' },
  { file: '35_Tomato___Target_Spot', name: '靶斑病', crop: '番茄' },
  { file: '36_Tomato___Tomato_Yellow_Leaf_Curl_Virus', name: '黄化曲叶病毒', crop: '番茄' },
  { file: '37_Tomato___Tomato_mosaic_virus', name: '花叶病毒', crop: '番茄' },
  { file: '38_Tomato___healthy', name: '健康', crop: '番茄' },
]

// 搜索
const searchQuery = ref('')

const filteredPests = computed(() => {
  if (!searchQuery.value.trim()) return pestImages
  const q = searchQuery.value.trim().toLowerCase()
  return pestImages.filter(name => name.toLowerCase().includes(q))
})

const filteredDiseases = computed(() => {
  if (!searchQuery.value.trim()) return diseaseEntries
  const q = searchQuery.value.trim().toLowerCase()
  return diseaseEntries.filter(d =>
    d.name.toLowerCase().includes(q) || d.crop.toLowerCase().includes(q)
  )
})

function getPestDisplayName(filename: string): string {
  // "000_稻纵卷叶螟" → "稻纵卷叶螟"
  const parts = filename.split('_')
  return parts.slice(1).join('_')
}

function getDiseaseStatus(name: string): 'healthy' | 'disease' {
  return name === '健康' ? 'healthy' : 'disease'
}
</script>

<template>
  <div class="h-full flex flex-col p-4 gap-4 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between shrink-0">
      <div>
        <h1 class="text-lg font-bold text-white">病虫害图鉴</h1>
        <p class="text-xs text-slate-500 font-mono">PEST & DISEASE HANDBOOK</p>
      </div>
      <div class="flex items-center gap-3">
        <!-- Search -->
        <div class="relative">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索..."
            class="w-48 px-3 py-1.5 pl-8 rounded-lg bg-white/5 border border-white/10 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-cyber-green/50 transition-colors"
          />
          <svg class="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
        </div>
        <!-- Count -->
        <span class="text-xs font-mono text-slate-500">
          {{ activeTab === 'pest' ? filteredPests.length : filteredDiseases.length }} 项
        </span>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex gap-2 shrink-0">
      <button
        class="px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200"
        :class="activeTab === 'pest'
          ? 'bg-gradient-to-r from-sunset-from to-sunset-to text-base shadow-lg shadow-sunset-from/20'
          : 'bg-white/5 text-slate-400 border border-white/10 hover:text-white hover:bg-white/10'"
        @click="activeTab = 'pest'; searchQuery = ''"
      >
        虫害图鉴
        <span class="ml-1.5 text-xs font-mono opacity-70">{{ pestImages.length }}</span>
      </button>
      <button
        class="px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200"
        :class="activeTab === 'disease'
          ? 'bg-gradient-to-r from-sunset-from to-sunset-to text-base shadow-lg shadow-sunset-from/20'
          : 'bg-white/5 text-slate-400 border border-white/10 hover:text-white hover:bg-white/10'"
        @click="activeTab = 'disease'; searchQuery = ''"
      >
        病害图鉴
        <span class="ml-1.5 text-xs font-mono opacity-70">{{ diseaseEntries.length }}</span>
      </button>
    </div>

    <!-- Pest Grid -->
    <div v-if="activeTab === 'pest'" class="flex-1 min-h-0 overflow-y-auto">
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-8 gap-3">
        <TiltCard
          v-for="filename in filteredPests"
          :key="filename"
          :max-tilt="8"
        >
          <GlassCard :hoverable="true" class="group overflow-hidden !p-0">
            <div class="aspect-square overflow-hidden bg-slate-900/50">
              <img
                :src="`/images/pest_handbook/${filename}.jpg`"
                :alt="getPestDisplayName(filename)"
                class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
                loading="lazy"
              />
            </div>
            <div class="px-3 py-2.5">
              <div class="text-xs text-white font-medium truncate">{{ getPestDisplayName(filename) }}</div>
              <div class="text-[10px] text-slate-500 font-mono mt-0.5">害虫</div>
            </div>
          </GlassCard>
        </TiltCard>
      </div>
      <!-- Empty -->
      <div v-if="filteredPests.length === 0" class="flex items-center justify-center h-48">
        <p class="text-sm text-slate-500">未找到匹配的虫害</p>
      </div>
    </div>

    <!-- Disease Grid -->
    <div v-if="activeTab === 'disease'" class="flex-1 min-h-0 overflow-y-auto">
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-8 gap-3">
        <TiltCard
          v-for="entry in filteredDiseases"
          :key="entry.file"
          :max-tilt="8"
        >
          <GlassCard :hoverable="true" class="group overflow-hidden !p-0">
            <div class="aspect-square overflow-hidden bg-slate-900/50 relative">
              <img
                :src="`/images/disease_handbook/${entry.file}.jpg`"
                :alt="`${entry.crop} ${entry.name}`"
                class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
                loading="lazy"
              />
              <!-- Status badge -->
              <div
                class="absolute top-2 right-2 px-1.5 py-0.5 rounded text-[9px] font-mono font-bold"
                :class="getDiseaseStatus(entry.name) === 'healthy'
                  ? 'bg-cyber-green/80 text-base'
                  : 'bg-sakura/80 text-white'"
              >
                {{ getDiseaseStatus(entry.name) === 'healthy' ? '健康' : '病害' }}
              </div>
            </div>
            <div class="px-3 py-2.5">
              <div class="text-xs text-white font-medium truncate">{{ entry.crop }} {{ entry.name }}</div>
              <div class="text-[10px] text-slate-500 font-mono mt-0.5">{{ entry.crop }}</div>
            </div>
          </GlassCard>
        </TiltCard>
      </div>
      <!-- Empty -->
      <div v-if="filteredDiseases.length === 0" class="flex items-center justify-center h-48">
        <p class="text-sm text-slate-500">未找到匹配的病害</p>
      </div>
    </div>
  </div>
</template>
