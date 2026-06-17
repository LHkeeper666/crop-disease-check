<script setup lang="ts">
import { ref, computed } from 'vue'
import GlassCard from '../components/GlassCard.vue'
import TiltCard from '../components/TiltCard.vue'

type Tab = 'pest' | 'disease'
const activeTab = ref<Tab>('pest')

// 虫害图鉴 — 文件名已是中文，含描述和防治方法
const pestEntries: { file: string; name: string; description: string; prevention: string }[] = [
  { file: '000_稻纵卷叶螟', name: '稻纵卷叶螟', description: '幼虫卷叶取食，影响水稻光合作用', prevention: '释放赤眼蜂，喷洒甲维盐' },
  { file: '001_稻螟蛉', name: '稻螟蛉', description: '幼虫取食水稻叶片', prevention: '灯光诱杀，喷洒氯虫苯甲酰胺' },
  { file: '002_稻潜叶蝇', name: '稻潜叶蝇', description: '幼虫潜入叶片内部取食', prevention: '清除杂草，喷洒阿维菌素' },
  { file: '003_亚洲玉米螟', name: '亚洲玉米螟', description: '幼虫蛀食玉米茎秆和果穗', prevention: '释放赤眼蜂，喷洒Bt制剂' },
  { file: '004_三化螟', name: '三化螟', description: '幼虫蛀食水稻茎秆造成枯心', prevention: '灯光诱杀，喷洒杀螟丹' },
  { file: '005_稻瘿蚊', name: '稻瘿蚊', description: '幼虫造成水稻叶鞘膨大成瘿', prevention: '清除田间杂草，喷洒吡虫啉' },
  { file: '006_稻秆蝇', name: '稻秆蝇', description: '幼虫蛀食水稻茎秆', prevention: '合理施肥，喷洒噻虫嗪' },
  { file: '007_褐飞虱', name: '褐飞虱', description: '吸食水稻汁液，传播病毒', prevention: '保护天敌，喷洒吡蚜酮' },
  { file: '008_白背飞虱', name: '白背飞虱', description: '吸食水稻汁液导致叶片发黄', prevention: '黄板诱杀，喷洒烯啶虫胺' },
  { file: '009_小褐飞虱', name: '小褐飞虱', description: '吸食水稻汁液', prevention: '合理密植，喷洒噻虫嗪' },
  { file: '010_稻水象甲', name: '稻水象甲', description: '成虫取食叶片，幼虫危害根部', prevention: '清除越冬场所，喷洒氯虫苯甲酰胺' },
  { file: '011_稻叶蝉', name: '稻叶蝉', description: '吸食水稻汁液，传播病毒', prevention: '清除杂草，喷洒吡虫啉' },
  { file: '012_谷物蓟马', name: '谷物蓟马', description: '锉吸谷物叶片和花器', prevention: '蓝色粘板诱杀，喷洒多杀菌素' },
  { file: '013_稻苞虫', name: '稻苞虫', description: '幼虫结苞取食水稻叶片', prevention: '人工摘除虫苞，喷洒Bt制剂' },
  { file: '014_蛴螬', name: '蛴螬', description: '地下害虫，咬食作物根系', prevention: '土壤处理，撒施辛硫磷颗粒' },
  { file: '015_蝼蛄', name: '蝼蛄', description: '地下害虫，咬食种子和根系', prevention: '毒饵诱杀，撒施辛硫磷颗粒' },
  { file: '016_金针虫', name: '金针虫', description: '地下害虫，咬食种子和幼苗根部', prevention: '土壤处理，撒施毒死蜱颗粒' },
  { file: '017_白边蛾', name: '白边蛾', description: '幼虫取食叶片', prevention: '灯光诱杀，喷洒高效氯氟氰菊酯' },
  { file: '018_小地老虎', name: '小地老虎', description: '幼虫咬断幼苗茎基部', prevention: '毒饵诱杀，喷洒辛硫磷' },
  { file: '019_大地老虎', name: '大地老虎', description: '幼虫咬断幼苗茎基部', prevention: '翻耕土壤，喷洒辛硫磷' },
  { file: '020_黄地老虎', name: '黄地老虎', description: '幼虫夜间取食幼苗', prevention: '灯光诱杀，喷洒溴氰菊酯' },
  { file: '021_红蜘蛛', name: '红蜘蛛', description: '叶螨吸食叶片汁液导致叶片失绿', prevention: '喷洒阿维菌素，增加湿度，释放捕食螨' },
  { file: '022_玉米螟', name: '玉米螟', description: '幼虫蛀食玉米茎秆和果穗', prevention: '释放赤眼蜂，心叶期撒施Bt颗粒' },
  { file: '023_夜蛾', name: '夜蛾', description: '幼虫夜间取食叶片', prevention: '灯光诱杀，喷洒甲维盐' },
  { file: '024_蚜虫', name: '蚜虫', description: '吸食叶片汁液，分泌蜜露引发煤污病', prevention: '黄板诱杀，释放瓢虫，喷洒吡虫啉' },
  { file: '025_白星花金龟', name: '白星花金龟', description: '成虫取食果实和花器', prevention: '糖醋液诱杀，喷洒高效氯氟氰菊酯' },
  { file: '026_桃蛀螟', name: '桃蛀螟', description: '幼虫蛀食桃果', prevention: '果实套袋，喷洒氯虫苯甲酰胺' },
  { file: '027_长角麦蚜', name: '长角麦蚜', description: '吸食麦类汁液', prevention: '保护天敌，喷洒吡虫啉' },
  { file: '028_麦二叉蚜', name: '麦二叉蚜', description: '吸食麦类汁液，传播病毒', prevention: '拌种处理，喷洒抗蚜威' },
  { file: '029_禾谷缢管蚜', name: '禾谷缢管蚜', description: '吸食麦类茎秆汁液', prevention: '释放蚜茧蜂，喷洒吡蚜酮' },
  { file: '030_麦红吸浆虫', name: '麦红吸浆虫', description: '幼虫吸食麦粒浆液', prevention: '适时早播，喷洒辛硫磷' },
  { file: '031_麦圆叶爪螨', name: '麦圆叶爪螨', description: '吸食麦叶汁液导致叶片发黄', prevention: '灌溉抗旱，喷洒阿维菌素' },
  { file: '032_长腿蜘蛛螨', name: '长腿蜘蛛螨', description: '吸食叶片汁液', prevention: '增加湿度，喷洒炔螨特' },
  { file: '033_麦管蓟马', name: '麦管蓟马', description: '锉吸麦穗花器', prevention: '喷洒吡虫啉，适时播种' },
  { file: '034_麦叶蜂', name: '麦叶蜂', description: '幼虫取食麦叶', prevention: '喷洒高效氯氰菊酯' },
  { file: '035_麦黑斑潜叶蝇', name: '麦黑斑潜叶蝇', description: '幼虫潜入叶片取食形成潜道', prevention: '清除杂草，喷洒阿维菌素' },
  { file: '036_甜菜潜叶花蝇', name: '甜菜潜叶花蝇', description: '幼虫潜入甜菜叶片取食', prevention: '黄板诱杀，喷洒辛硫磷' },
  { file: '037_跳甲', name: '跳甲', description: '成虫取食叶片形成小孔', prevention: '黄板诱杀，喷洒溴氰菊酯' },
  { file: '038_甘蓝夜蛾', name: '甘蓝夜蛾', description: '幼虫暴食甘蓝类蔬菜叶片', prevention: '灯光诱杀，喷洒Bt制剂' },
  { file: '039_甜菜夜蛾', name: '甜菜夜蛾', description: '幼虫取食多种作物叶片', prevention: '性诱剂诱杀，喷洒甲维盐' },
  { file: '040_甜菜斑蝇', name: '甜菜斑蝇', description: '幼虫潜入甜菜叶片', prevention: '清除杂草，喷洒辛硫磷' },
  { file: '041_草地螟', name: '草地螟', description: '幼虫暴食牧草和作物叶片', prevention: '灯光诱杀，喷洒高效氯氟氰菊酯' },
  { file: '042_甜菜点腹象甲', name: '甜菜点腹象甲', description: '成虫取食甜菜叶片', prevention: '人工捕杀，喷洒辛硫磷' },
  { file: '043_桑树金龟子', name: '桑树金龟子', description: '成虫取食桑叶', prevention: '灯光诱杀，喷洒高效氯氰菊酯' },
  { file: '044_苜蓿叶象甲', name: '苜蓿叶象甲', description: '幼虫取食苜蓿叶片', prevention: '提前刈割，喷洒Bt制剂' },
  { file: '045_苜蓿夜蛾', name: '苜蓿夜蛾', description: '幼虫取食苜蓿叶片和花蕾', prevention: '灯光诱杀，喷洒甲维盐' },
  { file: '046_苜蓿盲蝽', name: '苜蓿盲蝽', description: '吸食苜蓿汁液', prevention: '清除杂草，喷洒吡虫啉' },
  { file: '047_牧草盲椿', name: '牧草盲椿', description: '吸食牧草汁液导致生长不良', prevention: '合理放牧，喷洒噻虫嗪' },
  { file: '048_蝗虫', name: '蝗虫', description: '成虫和若虫暴食禾本科植物', prevention: '保护天敌(鸟类)，喷洒蝗虫微孢子虫' },
  { file: '049_西班牙绿芜菁_斑蝥', name: '西班牙绿芜菁/斑蝥', description: '成虫取食叶片', prevention: '人工捕杀，喷洒高效氯氰菊酯' },
  { file: '050_豆斑蝥', name: '豆斑蝥', description: '成虫取食叶片', prevention: '人工捕杀，喷洒高效氯氰菊酯' },
  { file: '051_斑蝥', name: '斑蝥', description: '成虫取食叶片', prevention: '人工捕杀，喷洒高效氯氰菊酯' },
  { file: '052_苜蓿斑蚜', name: '苜蓿斑蚜', description: '吸食苜蓿汁液', prevention: '释放天敌，喷洒吡蚜酮' },
  { file: '053_牛角花齿蓟马', name: '牛角花齿蓟马', description: '锉吸苜蓿花器', prevention: '喷洒多杀菌素' },
  { file: '054_蓟马_牧草虫', name: '蓟马/牧草虫', description: '锉吸植物嫩叶和花器', prevention: '蓝板诱杀，喷洒乙基多杀菌素' },
  { file: '055_苜蓿广肩小蜂', name: '苜蓿广肩小蜂', description: '幼虫蛀食苜蓿种子', prevention: '提前刈割，喷洒吡虫啉' },
  { file: '056_东方菜粉蝶', name: '东方菜粉蝶', description: '幼虫取食十字花科蔬菜叶片', prevention: '人工捉虫，喷洒Bt制剂' },
  { file: '057_绿盲蝽', name: '绿盲蝽', description: '吸食嫩叶和花器汁液', prevention: '清除杂草，喷洒吡虫啉' },
  { file: '058_刺蛾', name: '刺蛾', description: '幼虫取食多种果树叶片', prevention: '清除越冬茧，喷洒高效氯氰菊酯' },
  { file: '061_葡萄短须螨', name: '葡萄短须螨', description: '吸食叶片汁液', prevention: '喷洒炔螨特，增加湿度' },
  { file: '062_十星瓢萤叶甲', name: '十星瓢萤叶甲', description: '成虫和幼虫取食葡萄叶片', prevention: '人工捕杀，喷洒高效氯氰菊酯' },
  { file: '064_康氏粉蚧', name: '康氏粉蚧', description: '吸食枝干汁液，分泌蜜露', prevention: '喷洒噻嗪酮，保护天敌' },
  { file: '065_葡萄透翅蛾', name: '葡萄透翅蛾', description: '幼虫蛀食葡萄茎蔓', prevention: '剪除虫枝，注射Bt制剂' },
  { file: '066_葡萄天蛾', name: '葡萄天蛾', description: '幼虫取食葡萄叶片', prevention: '人工捕捉，喷洒甲维盐' },
  { file: '067_斑衣蜡蝉', name: '斑衣蜡蝉', description: '吸食枝干汁液', prevention: '清除卵块，喷洒吡虫啉' },
  { file: '068_虎天牛', name: '虎天牛', description: '幼虫蛀食果树枝干', prevention: '剪除虫枝，熏蒸处理' },
  { file: '069_大青叶蝉', name: '大青叶蝉', description: '成虫产卵划伤枝条表皮', prevention: '涂白树干，喷洒高效氯氰菊酯' },
  { file: '070_盲蝽', name: '盲蝽', description: '吸食嫩叶和花器汁液', prevention: '清除杂草，喷洒氟啶虫酰胺' },
  { file: '071_温室白粉虱', name: '温室白粉虱', description: '吸食汁液，分泌蜜露引发煤污病', prevention: '黄板诱杀，释放丽蚜小蜂' },
  { file: '072_葡萄二星叶蝉', name: '葡萄二星叶蝉', description: '吸食葡萄叶片汁液', prevention: '清除落叶，喷洒吡虫啉' },
  { file: '073_柑橘凤蝶', name: '柑橘凤蝶', description: '幼虫取食柑橘叶片', prevention: '人工捕捉，喷洒Bt制剂' },
  { file: '074_柑橘全爪螨', name: '柑橘全爪螨', description: '吸食柑橘叶片汁液', prevention: '释放捕食螨，喷洒阿维菌素' },
  { file: '076_吹绵蚧壳虫', name: '吹绵蚧壳虫', description: '吸食枝干汁液，分泌蜜露', prevention: '释放澳洲瓢虫，喷洒噻嗪酮' },
  { file: '077_矢尖盾蚧', name: '矢尖盾蚧', description: '吸食枝叶汁液', prevention: '修剪枝条，喷洒矿物油' },
  { file: '078_红蜡蚧', name: '红蜡蚧', description: '吸食枝干汁液', prevention: '喷洒噻嗪酮，保护天敌' },
  { file: '079_褐圆盾蚧', name: '褐圆盾蚧', description: '吸食叶片和果实汁液', prevention: '释放寄生蜂，喷洒矿物油' },
  { file: '081_堆蜡粉蚧', name: '堆蜡粉蚧', description: '吸食枝干汁液', prevention: '喷洒噻虫嗪，保护天敌' },
  { file: '082_柑橘刺粉蝨', name: '柑橘刺粉蝨', description: '吸食柑橘汁液', prevention: '黄板诱杀，喷洒吡虫啉' },
  { file: '083_柑橘大实蝇', name: '柑橘大实蝇', description: '幼虫蛀食柑橘果实', prevention: '诱蝇醚诱杀，摘除虫果' },
  { file: '084_柑橘小实蝇', name: '柑橘小实蝇', description: '幼虫蛀食多种水果', prevention: '蛋白诱剂诱杀，套袋' },
  { file: '085_蜜柑大实蝇', name: '蜜柑大实蝇', description: '幼虫蛀食柑橘果实', prevention: '诱杀成虫，摘除落果' },
  { file: '086_棉斜纹夜蛾', name: '棉斜纹夜蛾', description: '幼虫暴食多种作物叶片', prevention: '性诱剂诱杀，喷洒甲维盐' },
  { file: '087_枯叶夜蛾', name: '枯叶夜蛾', description: '成虫吸食果实汁液', prevention: '灯光诱杀，果实套袋' },
  { file: '088_桔潜蛾姬小蜂', name: '桔潜蛾姬小蜂', description: '寄生柑橘潜叶蛾', prevention: '保护天敌，减少农药使用' },
  { file: '089_橘蚜', name: '橘蚜', description: '吸食柑橘嫩梢汁液', prevention: '释放瓢虫，喷洒吡虫啉' },
  { file: '090_小桔蚜', name: '小桔蚜', description: '吸食柑橘嫩梢汁液', prevention: '黄板诱杀，喷洒抗蚜威' },
  { file: '091_苹果绣线菊蚜', name: '苹果绣线菊蚜', description: '吸食苹果嫩梢汁液导致卷叶', prevention: '释放草蛉，喷洒吡虫啉' },
  { file: '092_芒果茶黄蓟马', name: '芒果茶黄蓟马', description: '锉吸芒果嫩叶和花器', prevention: '蓝板诱杀，喷洒乙基多杀菌素' },
  { file: '093_荔枝叶瘿蚊', name: '荔枝叶瘿蚊', description: '幼虫在荔枝叶片上形成虫瘿', prevention: '清除瘿叶，喷洒阿维菌素' },
  { file: '094_茶树白蛾蜡蝉', name: '茶树白蛾蜡蝉', description: '吸食茶树汁液', prevention: '人工拍杀，喷洒吡虫啉' },
  { file: '095_青蛾蜡蝉', name: '青蛾蜡蝉', description: '吸食植物汁液', prevention: '清除杂草，喷洒高效氯氰菊酯' },
  { file: '096_芒果切叶象甲', name: '芒果切叶象甲', description: '成虫切叶产卵', prevention: '清除落叶，喷洒辛硫磷' },
  { file: '097_横线尾夜蛾', name: '横线尾夜蛾', description: '幼虫蛀食芒果嫩梢', prevention: '剪除虫梢，喷洒氯虫苯甲酰胺' },
  { file: '098_芒果扁喙叶蝉', name: '芒果扁喙叶蝉', description: '吸食芒果汁液', prevention: '清除杂草，喷洒噻虫嗪' },
  { file: '099_脊胸天牛', name: '脊胸天牛', description: '幼虫蛀食芒果枝干', prevention: '钩杀幼虫，注入敌敌畏' },
  { file: '100_芒果果肉象甲', name: '芒果果肉象甲', description: '幼虫蛀食芒果果肉', prevention: '果实套袋，喷洒氯虫苯甲酰胺' },
  { file: '101_叶蝉', name: '叶蝉', description: '吸食植物汁液，传播病毒', prevention: '清除杂草，喷洒吡虫啉' },
]

// 病害图鉴 — 英文文件名 → 中文名 + 作物
const diseaseEntries: { file: string; name: string; crop: string; description: string }[] = [
  { file: '01_Apple___Apple_scab', name: '黑星病', crop: '苹果', description: '由黑星菌引起的苹果叶部和果实病害' },
  { file: '02_Apple___Black_rot', name: '黑腐病', crop: '苹果', description: '由黑腐病菌引起的果实腐烂病害' },
  { file: '03_Apple___Cedar_apple_rust', name: '雪松锈病', crop: '苹果', description: '由锈菌引起的叶部病害，需桧柏作为转主寄主' },
  { file: '04_Apple___healthy', name: '健康', crop: '苹果', description: '苹果植株健康状态' },
  { file: '05_Blueberry___healthy', name: '健康', crop: '蓝莓', description: '蓝莓植株健康状态' },
  { file: '06_Cherry___Powdery_mildew', name: '白粉病', crop: '樱桃', description: '由白粉菌引起的叶面白色粉状物' },
  { file: '07_Cherry___healthy', name: '健康', crop: '樱桃', description: '樱桃植株健康状态' },
  { file: '08_Corn___Cercospora_leaf_spot_Gray_leaf_spot', name: '灰斑病', crop: '玉米', description: '由尾孢菌引起的叶部病害' },
  { file: '09_Corn___Common_rust', name: '普通锈病', crop: '玉米', description: '由锈菌引起的叶部孢子堆' },
  { file: '10_Corn___Northern_Leaf_Blight', name: '北方叶枯病', crop: '玉米', description: '由突脐蠕孢引起的大梭形病斑' },
  { file: '11_Corn___healthy', name: '健康', crop: '玉米', description: '玉米植株健康状态' },
  { file: '12_Grape___Black_rot', name: '黑腐病', crop: '葡萄', description: '由黑腐病菌引起的果实和叶片病害' },
  { file: '13_Grape___Esca_(Black_Measles)', name: '黑麻病', crop: '葡萄', description: '葡萄藤蔓内部腐烂病' },
  { file: '14_Grape___Leaf_blight_(Isariopsis_Leaf_Spot)', name: '叶枯病', crop: '葡萄', description: '由拟盘多毛孢引起的叶部枯斑' },
  { file: '15_Grape___healthy', name: '健康', crop: '葡萄', description: '葡萄植株健康状态' },
  { file: '16_Orange___Haunglongbing_(Citrus_greening)', name: '黄龙病', crop: '柑橘', description: '由韧皮部杆菌引起的毁灭性病害' },
  { file: '17_Peach___Bacterial_spot', name: '细菌性斑点病', crop: '桃', description: '由黄单胞菌引起的叶部穿孔' },
  { file: '18_Peach___healthy', name: '健康', crop: '桃', description: '桃树健康状态' },
  { file: '19_Pepper,_bell___Bacterial_spot', name: '细菌性斑点病', crop: '甜椒', description: '由黄单胞菌引起的叶部和果实斑点' },
  { file: '20_Pepper,_bell___healthy', name: '健康', crop: '甜椒', description: '辣椒植株健康状态' },
  { file: '21_Potato___Early_blight', name: '早疫病', crop: '马铃薯', description: '由链格孢引起的同心轮纹病斑' },
  { file: '22_Potato___Late_blight', name: '晚疫病', crop: '马铃薯', description: '由致病疫霉引起的水渍状病斑' },
  { file: '23_Potato___healthy', name: '健康', crop: '马铃薯', description: '马铃薯植株健康状态' },
  { file: '24_Raspberry___healthy', name: '健康', crop: '树莓', description: '树莓植株健康状态' },
  { file: '25_Soybean___healthy', name: '健康', crop: '大豆', description: '大豆植株健康状态' },
  { file: '26_Squash___Powdery_mildew', name: '白粉病', crop: '南瓜', description: '由白粉菌引起的叶面白色粉状物' },
  { file: '27_Strawberry___Leaf_scorch', name: '叶焦病', crop: '草莓', description: '由真菌引起的叶缘焦枯' },
  { file: '28_Strawberry___healthy', name: '健康', crop: '草莓', description: '草莓植株健康状态' },
  { file: '29_Tomato___Bacterial_spot', name: '细菌性斑点病', crop: '番茄', description: '由黄单胞菌引起的叶部和果实斑点' },
  { file: '30_Tomato___Early_blight', name: '早疫病', crop: '番茄', description: '由链格孢引起的同心轮纹病斑' },
  { file: '31_Tomato___Late_blight', name: '晚疫病', crop: '番茄', description: '由致病疫霉引起的真菌性病害，主要危害叶片和果实' },
  { file: '32_Tomato___Leaf_Mold', name: '叶霉病', crop: '番茄', description: '由番茄叶霉菌引起的真菌性病害，主要危害叶片' },
  { file: '33_Tomato___Septoria_leaf_spot', name: '斑枯病', crop: '番茄', description: '由壳针孢引起的叶部小斑点' },
  { file: '34_Tomato___Spider_mites_Two-spotted_spider_mite', name: '二斑叶螨', crop: '番茄', description: '叶螨吸食叶片汁液导致叶片失绿' },
  { file: '35_Tomato___Target_Spot', name: '靶斑病', crop: '番茄', description: '由棒孢霉引起的靶形病斑' },
  { file: '36_Tomato___Tomato_Yellow_Leaf_Curl_Virus', name: '黄化曲叶病毒', crop: '番茄', description: '由烟粉虱传播的病毒病' },
  { file: '37_Tomato___Tomato_mosaic_virus', name: '花叶病毒', crop: '番茄', description: '由烟草花叶病毒引起的叶片花叶' },
  { file: '38_Tomato___healthy', name: '健康', crop: '番茄', description: '番茄植株健康状态' },
]

// 搜索
const searchQuery = ref('')

const filteredPests = computed(() => {
  if (!searchQuery.value.trim()) return pestEntries
  const q = searchQuery.value.trim().toLowerCase()
  return pestEntries.filter(p => p.name.toLowerCase().includes(q))
})

const filteredDiseases = computed(() => {
  if (!searchQuery.value.trim()) return diseaseEntries
  const q = searchQuery.value.trim().toLowerCase()
  return diseaseEntries.filter(d =>
    d.name.toLowerCase().includes(q) || d.crop.toLowerCase().includes(q)
  )
})

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
        <span class="ml-1.5 text-xs font-mono opacity-70">{{ pestEntries.length }}</span>
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
          v-for="entry in filteredPests"
          :key="entry.file"
          :max-tilt="8"
        >
          <GlassCard :hoverable="true" class="group overflow-hidden !p-0">
            <div class="aspect-square overflow-hidden bg-slate-900/50 relative">
              <img
                :src="`/images/pest_handbook/${entry.file}.jpg`"
                :alt="entry.name"
                class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
                loading="lazy"
              />
              <!-- Description + Prevention overlay on hover -->
              <div
                class="absolute inset-0 bg-black/80 backdrop-blur-sm flex flex-col items-center justify-center p-3 gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
              >
                <p class="text-xs text-slate-200 leading-relaxed text-center">{{ entry.description }}</p>
                <div class="w-8 h-px bg-white/20"></div>
                <p class="text-[11px] text-emerald-300 leading-relaxed text-center">防治: {{ entry.prevention }}</p>
              </div>
            </div>
            <div class="px-3 py-2.5">
              <div class="text-xs text-white font-medium truncate">{{ entry.name }}</div>
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
          <GlassCard :hoverable="true" class="group overflow-hidden !p-0 relative">
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
              <!-- Description overlay on hover -->
              <div
                class="absolute inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-3 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
              >
                <p class="text-xs text-slate-200 leading-relaxed text-center">{{ entry.description }}</p>
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
