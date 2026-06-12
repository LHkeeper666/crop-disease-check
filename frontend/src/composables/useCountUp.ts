import { ref, watch, onMounted, type Ref } from 'vue'

/**
 * Animates a numeric value from 0 (or previous) to target.
 * Uses easeOutExpo for a satisfying deceleration curve.
 */
export function useCountUp(
  target: Ref<number | string>,
  duration = 600,
  decimals = 0
) {
  const display = ref(0)
  let animFrame: number | null = null

  function animate(from: number, to: number) {
    if (animFrame) cancelAnimationFrame(animFrame)
    const startTime = performance.now()

    const tick = (now: number) => {
      const elapsed = now - startTime
      const progress = Math.min(elapsed / duration, 1)
      // easeOutExpo
      const eased = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress)
      display.value = Number((from + (to - from) * eased).toFixed(decimals))

      if (progress < 1) {
        animFrame = requestAnimationFrame(tick)
      }
    }
    animFrame = requestAnimationFrame(tick)
  }

  onMounted(() => {
    const num = typeof target.value === 'string' ? parseFloat(target.value) : target.value
    if (!isNaN(num)) animate(0, num)
  })

  watch(target, (newVal, oldVal) => {
    const to = typeof newVal === 'string' ? parseFloat(newVal) : newVal
    const from = typeof oldVal === 'string' ? parseFloat(oldVal) : oldVal
    if (isNaN(to)) return
    animate(isNaN(from) ? 0 : from, to)
  })

  return display
}
