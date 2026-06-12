import { defineStore } from 'pinia'
import { ref, watchEffect } from 'vue'

const STORAGE_KEY = 'treeforge_dashboard_settings'

interface EnvironmentItem {
  value: number
  unit: string
  status: 'normal' | 'warning' | 'critical'
}

interface EnvironmentData {
  airTemp: EnvironmentItem
  soilMoisture: EnvironmentItem
  humidity: EnvironmentItem
  lightLevel: EnvironmentItem
}

interface GrowthMetric {
  label: string
  value: number
  unit: string
  color: string
  max: number
}

interface GreenhouseMeta {
  sectorId: string
  cropSpecies: string
  plantingDate: string
  location: string
  area: string
}

const defaultEnv: EnvironmentData = {
  airTemp: { value: 23.6, unit: '°C', status: 'normal' },
  soilMoisture: { value: 65.2, unit: '%', status: 'normal' },
  humidity: { value: 78.5, unit: '%', status: 'warning' },
  lightLevel: { value: 1230, unit: 'lux', status: 'normal' },
}

const defaultGrowth: GrowthMetric[] = [
  { label: 'CO₂', value: 420, unit: 'ppm', color: '#4ADE80', max: 600 },
  { label: '土壤 pH', value: 6.8, unit: '', color: '#FFB300', max: 14 },
  { label: 'EC', value: 1.2, unit: 'mS/cm', color: '#4ADE80', max: 3 },
  { label: '温度', value: 23.6, unit: '°C', color: '#FF6A00', max: 40 },
  { label: 'N', value: 45, unit: 'mg/kg', color: '#4ADE80', max: 300 },
  { label: 'P', value: 32, unit: 'mg/kg', color: '#FFB300', max: 300 },
  { label: 'K', value: 180, unit: 'mg/kg', color: '#4ADE80', max: 300 },
]

const defaultMeta: GreenhouseMeta = {
  sectorId: 'GH-A1',
  cropSpecies: 'Solanum lycopersicum',
  plantingDate: '2026-03-15',
  location: '34.2614N, 108.9423E',
  area: '2400 m²',
}

function deepClone<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj))
}

function loadFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        env: parsed.env ?? deepClone(defaultEnv),
        growth: parsed.growth ?? deepClone(defaultGrowth),
        meta: parsed.meta ?? deepClone(defaultMeta),
      }
    }
  } catch (e) {
    console.warn('[DashboardSettings] Failed to load from localStorage:', e)
  }
  return {
    env: deepClone(defaultEnv),
    growth: deepClone(defaultGrowth),
    meta: deepClone(defaultMeta),
  }
}

export const useDashboardSettingsStore = defineStore('dashboardSettings', () => {
  const initial = loadFromStorage()
  const env = ref<EnvironmentData>(initial.env)
  const growth = ref<GrowthMetric[]>(initial.growth)
  const meta = ref<GreenhouseMeta>(initial.meta)

  // Persist every change using watchEffect (runs immediately + tracks all deps)
  watchEffect(() => {
    const data = JSON.stringify({
      env: env.value,
      growth: growth.value,
      meta: meta.value,
    })
    localStorage.setItem(STORAGE_KEY, data)
  })

  function resetToDefaults() {
    env.value = deepClone(defaultEnv)
    growth.value = deepClone(defaultGrowth)
    meta.value = deepClone(defaultMeta)
  }

  return { env, growth, meta, resetToDefaults }
})
