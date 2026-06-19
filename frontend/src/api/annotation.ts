import { request } from './request'

const BASE = '/api/annotation'

export interface AnnotationBoxDTO {
  classId: number
  className: string
  nameCn: string
  x: number
  y: number
  width: number
  height: number
}

export interface AnnotationSaveDTO {
  workOrderId: number
  imageUrl: string
  pipeline: string
  boxes: AnnotationBoxDTO[]
}

export interface AnnotationBoxVO extends AnnotationBoxDTO {
  id: number
}

export interface AnnotationVO {
  id: number
  workOrderId: number
  imageUrl: string
  pipeline: string
  createdBy: string
  createdByName: string
  createdAt: string
  boxes: AnnotationBoxVO[]
}

export interface ClassOptionVO {
  id: number
  className: string
  nameCn: string
}

/** 保存/更新标注 */
export async function saveAnnotation(dto: AnnotationSaveDTO): Promise<number> {
  return request<number>(`${BASE}/save`, {
    method: 'POST',
    body: JSON.stringify(dto),
  })
}

/** 获取工单的标注详情 */
export async function getAnnotationByWorkOrder(workOrderId: number): Promise<AnnotationVO | null> {
  return request<AnnotationVO | null>(`${BASE}/work-order/${workOrderId}`)
}

/** 获取类别列表 */
export async function getClassOptions(pipeline: string): Promise<ClassOptionVO[]> {
  return request<ClassOptionVO[]>(`${BASE}/classes?pipeline=${encodeURIComponent(pipeline)}`)
}

/** 导出 YOLO 格式 txt */
export async function exportYoloTxt(annotationId: number): Promise<string> {
  return request<string>(`${BASE}/${annotationId}/export-yolo`)
}
