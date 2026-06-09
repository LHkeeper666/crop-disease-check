// Mock data for all modules

export const mockEnvironmentData = {
  airTemp: { value: 23.6, unit: 'C', status: 'normal' as const },
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

export const mockAlerts = [
  { id: 1, level: 'critical' as const, message: 'Grid-B3 红蜘蛛置信度 0.95', time: '2分钟前' },
  { id: 2, level: 'warning' as const, message: 'A区湿度超过阈值 78.5%', time: '15分钟前' },
  { id: 3, level: 'critical' as const, message: 'Grid-A1 番茄晚疫病 置信度 0.92', time: '32分钟前' },
  { id: 4, level: 'warning' as const, message: '3号摄像头离线超过10分钟', time: '1小时前' },
]

export const mockGrowthMetrics = [
  { label: 'CO2', value: 420, unit: 'ppm', color: '#4ADE80' },
  { label: 'Soil pH', value: 6.8, unit: '', color: '#FFB300' },
  { label: 'EC', value: 1.2, unit: 'mS/cm', color: '#4ADE80' },
  { label: 'Temp', value: 23.6, unit: 'C', color: '#FF6A00' },
  { label: 'N', value: 45, unit: 'mg/kg', color: '#4ADE80' },
  { label: 'P', value: 32, unit: 'mg/kg', color: '#FFB300' },
  { label: 'K', value: 180, unit: 'mg/kg', color: '#4ADE80' },
]

export const mockPowerStream = Array.from({ length: 64 }, (_, i) =>
  Math.sin(i * 0.3) * 30 + Math.random() * 15 + 50
)

export const mockGreenhouseMeta = {
  sectorId: 'GH-A1',
  cropSpecies: 'Solanum lycopersicum',
  plantingDate: '2026-03-15',
  location: '34.2614N, 108.9423E',
  area: '2400 m2',
  status: 'ACTIVE',
}

// Grid heatmap data
export const mockGridHeatmap = [
  { label: 'A1', score: 0.75, pest: '番茄晚疫病', status: 'warning' },
  { label: 'A2', score: 0.22, pest: '', status: 'safe' },
  { label: 'A3', score: 0.15, pest: '', status: 'safe' },
  { label: 'B1', score: 0.38, pest: '蚜虫', status: 'warning' },
  { label: 'B2', score: 0.10, pest: '', status: 'safe' },
  { label: 'B3', score: 0.92, pest: '红蜘蛛', status: 'critical' },
  { label: 'C1', score: 0.45, pest: '白粉病', status: 'warning' },
  { label: 'C2', score: 0.08, pest: '', status: 'safe' },
  { label: 'C3', score: 0.62, pest: '蚜虫', status: 'warning' },
]

// Work orders
export const mockWorkOrders = [
  {
    id: 'wo-001',
    title: '【紧急】Grid-B3 发现高置信度红蜘蛛',
    severity: 'CRITICAL',
    status: 'PENDING',
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
  { id: 'cam-001', name: 'A区-1号摄像头', status: 'ONLINE', grid: 'A1,A2', rtspUrl: 'rtsp://192.168.1.101:554/stream1' },
  { id: 'cam-002', name: 'A区-2号摄像头', status: 'ONLINE', grid: 'A2,A3', rtspUrl: 'rtsp://192.168.1.102:554/stream1' },
  { id: 'cam-003', name: 'B区-1号摄像头', status: 'OFFLINE', grid: 'B1,B2', rtspUrl: 'rtsp://192.168.1.103:554/stream1' },
  { id: 'cam-004', name: 'B区-2号摄像头', status: 'ONLINE', grid: 'B2,B3', rtspUrl: 'rtsp://192.168.1.104:554/stream1' },
  { id: 'cam-005', name: 'C区-1号摄像头', status: 'ONLINE', grid: 'C1,C2', rtspUrl: 'rtsp://192.168.1.105:554/stream1' },
  { id: 'cam-006', name: 'C区-2号摄像头', status: 'FAULT', grid: 'C2,C3', rtspUrl: 'rtsp://192.168.1.106:554/stream1' },
]

// Statistics overview
export const mockStatsOverview = {
  totalReports: 150,
  todayReports: 12,
  pendingAudit: 8,
  processed: 135,
  highRiskAlerts: 3,
  typeDistribution: [
    { name: '番茄晚疫病', value: 45 },
    { name: '红蜘蛛', value: 30 },
    { name: '蚜虫', value: 25 },
    { name: '白粉病', value: 20 },
    { name: '螟虫', value: 15 },
    { name: '其他', value: 15 },
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
  top5Pests: [
    { name: '番茄晚疫病', count: 45 },
    { name: '红蜘蛛', count: 30 },
    { name: '蚜虫', count: 25 },
    { name: '白粉病', count: 20 },
    { name: '螟虫', count: 15 },
  ],
}

// Daily reports
export const mockDailyReports = [
  { id: 'rpt-001', date: '2026-06-08', inspections: 120, detections: 45, disease: 28, pest: 17, handledRate: 0.85, emailSent: true },
  { id: 'rpt-002', date: '2026-06-07', inspections: 115, detections: 52, disease: 35, pest: 17, handledRate: 0.90, emailSent: true },
  { id: 'rpt-003', date: '2026-06-06', inspections: 108, detections: 38, disease: 22, pest: 16, handledRate: 0.78, emailSent: true },
  { id: 'rpt-004', date: '2026-06-05', inspections: 95, detections: 28, disease: 15, pest: 13, handledRate: 0.82, emailSent: true },
  { id: 'rpt-005', date: '2026-06-04', inspections: 110, detections: 42, disease: 30, pest: 12, handledRate: 0.88, emailSent: true },
]

// Users
export const mockUsers = [
  { id: 'u-001', username: 'admin', name: '系统管理员', role: 'ADMIN', phone: '13800138000', status: 'ACTIVE', lastLoginAt: '2026-06-09T08:00:00' },
  { id: 'u-002', username: 'expert_li', name: '李专家', role: 'EXPERT', phone: '13900139000', status: 'ACTIVE', lastLoginAt: '2026-06-09T09:30:00' },
  { id: 'u-003', username: 'expert_wang', name: '王专家', role: 'EXPERT', phone: '13700137000', status: 'ACTIVE', lastLoginAt: '2026-06-09T10:00:00' },
  { id: 'u-004', username: 'manager_zhang', name: '张三', role: 'MANAGER', phone: '13600136000', status: 'ACTIVE', lastLoginAt: '2026-06-09T07:30:00' },
  { id: 'u-005', username: 'visitor', name: '访客用户', role: 'VISITOR', phone: '13500135000', status: 'DISABLED', lastLoginAt: '2026-06-08T14:00:00' },
]
