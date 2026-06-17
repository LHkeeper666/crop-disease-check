import { onUnmounted } from 'vue'
import type { PageContext } from '../types/pageContext'

type ExtractFn = () => PageContext | null

/** 模块级单例：当前活跃页面的上下文提取函数 */
let activeExtractor: ExtractFn | null = null

/** 页面组件调用：注册上下文提取函数，卸载时自动清除 */
export function usePageContextProvider(extract: ExtractFn) {
  activeExtractor = extract
  onUnmounted(() => {
    if (activeExtractor === extract) {
      activeExtractor = null
    }
  })
}

/** DaveChatDialog 调用：获取当前页面上下文（每次调用实时读取） */
export function usePageContextInjector(): () => PageContext | null {
  return () => activeExtractor?.() ?? null
}
