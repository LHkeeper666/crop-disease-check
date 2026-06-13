<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'

const props = defineProps<{
  content: string | null | undefined
}>()

// Configure marked with highlight.js
marked.setOptions({
  highlight(code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch {
        // Fall through to auto-detection
      }
    }
    try {
      return hljs.highlightAuto(code).value
    } catch {
      return code
    }
  },
  breaks: true,
  gfm: true,
})

// Custom renderer for code blocks with language label
const renderer = new marked.Renderer()

renderer.code = function ({ text, lang }: { text: string; lang?: string }) {
  const language = lang || ''
  const highlighted = language && hljs.getLanguage(language)
    ? hljs.highlight(text, { language }).value
    : hljs.highlightAuto(text).value

  const langLabel = language
    ? `<div class="code-lang">${language}</div>`
    : ''

  return `<div class="code-block">${langLabel}<pre><code class="hljs ${language ? `language-${language}` : ''}">${highlighted}</code></pre></div>`
}

marked.use({ renderer })

const renderedContent = computed(() => {
  if (!props.content) return ''
  try {
    return marked.parse(props.content) as string
  } catch {
    return props.content
  }
})
</script>

<template>
  <div
    v-if="content"
    class="markdown-body"
    v-html="renderedContent"
  />
</template>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.6;
  color: #e2e8f0;
  word-break: break-word;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  color: #f8fafc;
}

.markdown-body :deep(h1) {
  font-size: 1.5em;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.markdown-body :deep(h2) {
  font-size: 1.3em;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.markdown-body :deep(h3) {
  font-size: 1.1em;
}

.markdown-body :deep(h4) {
  font-size: 1em;
}

.markdown-body :deep(p) {
  margin-bottom: 12px;
}

.markdown-body :deep(a) {
  color: #4ade80;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(strong) {
  color: #f8fafc;
  font-weight: 600;
}

.markdown-body :deep(em) {
  font-style: italic;
}

/* Lists */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin-bottom: 12px;
  padding-left: 24px;
}

.markdown-body :deep(ul) {
  list-style-type: disc;
}

.markdown-body :deep(ol) {
  list-style-type: decimal;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(li > ul),
.markdown-body :deep(li > ol) {
  margin-top: 4px;
  margin-bottom: 0;
}

/* Blockquote */
.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 4px solid #4ade80;
  background: rgba(74, 222, 128, 0.05);
  border-radius: 0 8px 8px 0;
  color: #94a3b8;
}

.markdown-body :deep(blockquote p) {
  margin-bottom: 0;
}

/* Code blocks */
.markdown-body :deep(.code-block) {
  position: relative;
  margin: 12px 0;
  border-radius: 8px;
  overflow: hidden;
  background: #1e293b;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.markdown-body :deep(.code-lang) {
  position: absolute;
  top: 0;
  right: 0;
  padding: 4px 12px;
  font-size: 11px;
  font-family: monospace;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 0 8px 0 8px;
}

.markdown-body :deep(pre) {
  margin: 0;
  padding: 16px;
  overflow-x: auto;
}

.markdown-body :deep(code) {
  font-family: 'Fira Code', 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
}

.markdown-body :deep(pre code) {
  display: block;
  line-height: 1.5;
}

.markdown-body :deep(:not(pre) > code) {
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  font-size: 0.9em;
}

/* Tables */
.markdown-body :deep(table) {
  width: 100%;
  margin: 12px 0;
  border-collapse: collapse;
  overflow-x: auto;
  display: block;
}

.markdown-body :deep(thead) {
  background: rgba(255, 255, 255, 0.05);
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  text-align: left;
}

.markdown-body :deep(th) {
  font-weight: 600;
  color: #f8fafc;
}

.markdown-body :deep(tbody tr:nth-child(even)) {
  background: rgba(255, 255, 255, 0.02);
}

/* Horizontal rule */
.markdown-body :deep(hr) {
  margin: 16px 0;
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

/* Images */
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}
</style>
