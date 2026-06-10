// Mock data for all modules

export const mockEnvironmentData = {
  airTemp: { value: 23.6, unit: '°C', status: 'normal' as const },
  soilMoisture: { value: 65.2, unit: '%', status: 'normal' as const },
  humidity: { value: 78.5, unit: '%', status: 'warning' as const },
  lightLevel: { value: 1230, unit: 'lux', status: 'normal' as const },
}

export const mockEnergyData = {
  current: 55.44,
  max: 100,
  unit: 'Kw',
  trend: 'stable' as const,
}

// Alerts are now dynamically generated from work orders via useWorkOrderStore

export const mockGrowthMetrics = [
  { label: 'CO₂', value: 420, unit: 'ppm', color: '#4ADE80' },
  { label: '土壤 pH', value: 6.8, unit: '', color: '#FFB300' },
  { label: 'EC', value: 1.2, unit: 'mS/cm', color: '#4ADE80' },
  { label: '温度', value: 23.6, unit: '°C', color: '#FF6A00' },
  { label: 'N', value: 45, unit: 'mg/kg', color: '#4ADE80' },
  { label: 'P', value: 32, unit: 'mg/kg', color: '#FFB300' },
  { label: 'K', value: 180, unit: 'mg/kg', color: '#4ADE80' },
]


export const mockGreenhouseMeta = {
  sectorId: 'GH-A1',
  cropSpecies: 'Solanum lycopersicum',
  plantingDate: '2026-03-15',
  location: '34.2614N, 108.9423E',
  area: '2400 m²',
  status: 'ACTIVE',
}

// Grid heatmap data — base risk scores per zone
export const mockGridHeatmap = [
  { label: 'A1', score: 0.75, pest: '番茄晚疫病', type: 'disease' as const },
  { label: 'A2', score: 0.22, pest: '', type: null },
  { label: 'A3', score: 0.15, pest: '', type: null },
  { label: 'B1', score: 0.38, pest: '蚜虫', type: 'pest' as const },
  { label: 'B2', score: 0.10, pest: '', type: null },
  { label: 'B3', score: 0.92, pest: '红蜘蛛', type: 'pest' as const },
  { label: 'C1', score: 0.45, pest: '白粉病', type: 'disease' as const },
  { label: 'C2', score: 0.08, pest: '', type: null },
  { label: 'C3', score: 0.62, pest: '蚜虫', type: 'pest' as const },
]

// Work orders
export const mockWorkOrders = [
  {
    id: 'wo-001',
    title: '【紧急】Grid-B3 发现高置信度红蜘蛛',
    severity: 'CRITICAL',
    status: 'PENDING',
    type: 'pest',
    gridLabel: 'B3',
    pestName: '红蜘蛛',
    confidence: 0.95,
    assignedToName: '李专家',
    createdAt: '2026-06-09T10:30:00',
    updatedAt: '2026-06-09T10:30:00',
  },
  {
    id: 'wo-002',
    title: '【高危】Grid-A1 番茄晚疫病扩散',
    severity: 'HIGH',
    status: 'PROCESSING',
    type: 'disease',
    gridLabel: 'A1',
    pestName: '番茄晚疫病',
    confidence: 0.92,
    assignedToName: '王专家',
    createdAt: '2026-06-09T09:15:00',
    updatedAt: '2026-06-09T09:45:00',
  },
  {
    id: 'wo-003',
    title: 'Grid-C1 白粉病中等威胁',
    severity: 'MEDIUM',
    status: 'DONE',
    type: 'disease',
    gridLabel: 'C1',
    pestName: '白粉病',
    confidence: 0.78,
    assignedToName: '李专家',
    createdAt: '2026-06-09T08:00:00',
    updatedAt: '2026-06-09T08:30:00',
  },
  {
    id: 'wo-004',
    title: 'Grid-B1 蚜虫低风险预警',
    severity: 'LOW',
    status: 'IGNORED',
    type: 'pest',
    gridLabel: 'B1',
    pestName: '蚜虫',
    confidence: 0.65,
    assignedToName: '王专家',
    createdAt: '2026-06-08T16:00:00',
    updatedAt: '2026-06-08T17:00:00',
  },
]

// Cameras
export const mockCameras = [
  { id: 'cam-001', name: 'A区监控', status: 'ONLINE', grid: 'A1,A2,A3', rtspUrl: 'rtsp://192.168.1.101:554/stream1' },
  { id: 'cam-002', name: 'B区监控', status: 'ONLINE', grid: 'B1,B2,B3', rtspUrl: 'rtsp://192.168.1.102:554/stream1' },
  { id: 'cam-003', name: 'C区监控', status: 'OFFLINE', grid: 'C1,C2,C3', rtspUrl: 'rtsp://192.168.1.103:554/stream1' },
]

// Statistics overview
export const mockStatsOverview = {
  totalReports: 150,
  todayReports: 12,
  pendingAudit: 8,
  processed: 135,
  highRiskAlerts: 3,
  // 病害分布
  diseaseDistribution: [
    { name: '番茄晚疫病', value: 45 },
    { name: '白粉病', value: 20 },
    { name: '灰霉病', value: 12 },
    { name: '霜霉病', value: 8 },
  ],
  // 虫害分布
  pestDistribution: [
    { name: '红蜘蛛', value: 30 },
    { name: '蚜虫', value: 25 },
    { name: '螟虫', value: 15 },
    { name: '白粉虱', value: 10 },
  ],
  dailyTrend: [
    { date: '06-03', count: 18 },
    { date: '06-04', count: 22 },
    { date: '06-05', count: 15 },
    { date: '06-06', count: 28 },
    { date: '06-07', count: 32 },
    { date: '06-08', count: 25 },
    { date: '06-09', count: 12 },
  ],
  top5Diseases: [
    { name: '番茄晚疫病', count: 45 },
    { name: '白粉病', count: 20 },
    { name: '灰霉病', count: 12 },
    { name: '霜霉病', count: 8 },
  ],
  top5Pests: [
    { name: '红蜘蛛', count: 30 },
    { name: '蚜虫', count: 25 },
    { name: '螟虫', count: 15 },
    { name: '白粉虱', count: 10 },
  ],
}

// Daily reports
export const mockDailyReports = [
  { id: 'rpt-001', date: '2026-06-08', detections: 45, disease: 28, pest: 17, handledRate: 0.85 },
  { id: 'rpt-002', date: '2026-06-07', detections: 52, disease: 35, pest: 17, handledRate: 0.90 },
  { id: 'rpt-003', date: '2026-06-06', detections: 38, disease: 22, pest: 16, handledRate: 0.78 },
  { id: 'rpt-004', date: '2026-06-05', detections: 28, disease: 15, pest: 13, handledRate: 0.82 },
  { id: 'rpt-005', date: '2026-06-04', detections: 42, disease: 30, pest: 12, handledRate: 0.88 },
]

// Users
export const mockUsers = [
  { id: 'u-001', username: 'admin', name: '系统管理员', role: 'ADMIN', phone: '13800138000', status: 'ACTIVE', lastLoginAt: '2026-06-09T08:00:00' },
  { id: 'u-002', username: 'expert_li', name: '李专家', role: 'EXPERT', phone: '13900139000', status: 'ACTIVE', lastLoginAt: '2026-06-09T09:30:00' },
  { id: 'u-003', username: 'expert_wang', name: '王专家', role: 'EXPERT', phone: '13700137000', status: 'ACTIVE', lastLoginAt: '2026-06-09T10:00:00' },
  { id: 'u-004', username: 'manager_zhang', name: '张三', role: 'MANAGER', phone: '13600136000', status: 'ACTIVE', lastLoginAt: '2026-06-09T07:30:00' },
  { id: 'u-005', username: 'visitor', name: '访客用户', role: 'VISITOR', phone: '13500135000', status: 'DISABLED', lastLoginAt: '2026-06-08T14:00:00' },
]
