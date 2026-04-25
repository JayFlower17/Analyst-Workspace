<template>
  <div class="app-shell">
    <aside class="sidebar" @mouseenter="sidebarExpanded = true" @mouseleave="sidebarExpanded = false">
      <div class="brand">
        <div class="brand-mark">A</div>
        <span v-show="sidebarExpanded" class="brand-text">Agent Analytics</span>
      </div>

      <nav class="nav-list">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: isRouteActive(item.path) }"
          @click="go(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span v-show="sidebarExpanded">{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-foot">
        <button class="nav-item subtle" @click="showHelp = true">
          <el-icon><QuestionFilled /></el-icon>
          <span v-show="sidebarExpanded">Help</span>
        </button>
      </div>
    </aside>

    <div class="main-area">
      <header class="top-nav">
        <div class="top-left">
          <h1 class="page-title">{{ currentPageTitle }}</h1>
        </div>

        <button class="command-bar" @click="commandOpen = true">
          <el-icon><Search /></el-icon>
          <span>Search datasets, queries, metrics...</span>
          <kbd>⌘K</kbd>
        </button>

        <div class="top-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="avatar-btn">
              <el-avatar :size="26">U</el-avatar>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">Profile</el-dropdown-item>
                <el-dropdown-item command="logout" divided>Logout</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content-wrap">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <el-dialog v-model="commandOpen" title="Command Palette" width="640px" class="command-dialog">
      <el-input
        v-model="commandText"
        placeholder="Try: Open workplace, Go to chat, Show top products"
        size="large"
        @keyup.enter="runCommand"
      />
      <div class="quick-actions">
        <button class="quick" @click="runShortcut('/chat')">Go Chat</button>
        <button class="quick" @click="runShortcut('/analysis')">Go Workplace</button>
        <button class="quick" @click="runShortcut('/datasets')">Go Datasets</button>
      </div>
    </el-dialog>

    <el-dialog v-model="showHelp" title="AI-Native Workspace Guide" width="560px" class="command-dialog">
      <div class="help-list">
        <div class="help-item">
          <strong>Chat:</strong> upload a file and ask quick questions.
        </div>
        <div class="help-item">
          <strong>Workplace:</strong> multi-table analysis with reusable artifacts.
        </div>
        <div class="help-item">
          <strong>Cmd/Ctrl + K:</strong> open global command palette.
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChatDotRound,
  DataAnalysis,
  Folder,
  Search,
  QuestionFilled,
  ArrowDown
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const sidebarExpanded = ref(false)
const commandOpen = ref(false)
const commandText = ref('')
const showHelp = ref(false)

const navItems = [
  { path: '/chat', label: 'Chat', icon: ChatDotRound },
  { path: '/analysis', label: 'Workplace', icon: DataAnalysis },
  { path: '/datasets', label: 'Datasets', icon: Folder }
]

const isRouteActive = (path) => {
  if (path === '/analysis' && route.path === '/workplace') return true
  return route.path.startsWith(path)
}

const go = (path) => {
  if (path === '/analysis') {
    router.push('/workplace')
    return
  }
  router.push(path)
}

const currentPageTitle = computed(() => {
  if (route.path.startsWith('/chat')) return 'AI Command Center'
  if (route.path.startsWith('/analysis') || route.path.startsWith('/workplace')) return 'Workplace Dashboard'
  if (route.path.startsWith('/datasets')) return 'Dataset Manager'
  return 'Agent Analytics'
})

const runShortcut = (path) => () => {
  commandOpen.value = false
  if (path === '/analysis') {
    router.push('/workplace')
  } else {
    router.push(path)
  }
}

const runCommand = () => {
  const cmd = commandText.value.trim().toLowerCase()
  if (!cmd) return
  if (cmd.includes('chat')) {
    router.push('/chat')
  } else if (cmd.includes('work') || cmd.includes('analysis')) {
    router.push('/workplace')
  } else if (cmd.includes('data')) {
    router.push('/datasets')
  } else {
    ElMessage.info('Command not matched yet')
  }
  commandOpen.value = false
  commandText.value = ''
}

const handleCommand = (command) => {
  if (command === 'logout') {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    router.push('/login')
    return
  }
  if (command === 'profile') {
    ElMessage.info('Profile panel is coming soon')
  }
}

const handleKeyDown = (event) => {
  const isCmdK = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k'
  if (isCmdK) {
    event.preventDefault()
    commandOpen.value = true
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&display=swap');

:root {
  --bg: #09090b;
  --surface: #121215;
  --surface-2: #17171c;
  --surface-3: #1e1e25;
  --text: #f4f4f5;
  --muted: #a1a1aa;
  --border: #27272a;
  --accent: #4f46e5;
  --accent-soft: rgba(79, 70, 229, 0.25);
  --glow: 0 0 0 1px rgba(79, 70, 229, 0.26), 0 0 30px rgba(79, 70, 229, 0.18);
}

* {
  box-sizing: border-box;
}

html, body, #app {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: radial-gradient(1200px 700px at 80% -10%, rgba(79, 70, 229, 0.12), transparent 60%), var(--bg);
  color: var(--text);
}

.app-shell {
  height: 100vh;
  display: grid;
  grid-template-columns: 68px 1fr;
  background: transparent;
}

.sidebar {
  border-right: 1px solid var(--border);
  background: rgba(9, 9, 11, 0.92);
  backdrop-filter: blur(8px);
  transition: width 0.24s ease;
  overflow: hidden;
  width: 68px;
  display: flex;
  flex-direction: column;
  padding: 14px 10px;
  gap: 10px;
}

.sidebar:hover {
  width: 220px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(145deg, #4f46e5, #312e81);
  display: grid;
  place-items: center;
  font-weight: 700;
  box-shadow: var(--glow);
}

.brand-text {
  font-size: 14px;
  font-weight: 600;
  color: #fafafa;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.nav-item {
  width: 100%;
  border: 1px solid transparent;
  background: transparent;
  color: var(--muted);
  border-radius: 10px;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  cursor: pointer;
  transition: all 0.22s ease;
  font-size: 13px;
  font-weight: 500;
}

.nav-item:hover {
  background: var(--surface-2);
  color: #fff;
  border-color: var(--border);
}

.nav-item.active {
  background: linear-gradient(135deg, rgba(79, 70, 229, 0.25), rgba(79, 70, 229, 0.08));
  border-color: rgba(79, 70, 229, 0.45);
  color: #fff;
  box-shadow: var(--glow);
}

.nav-item.subtle {
  opacity: 0.9;
}

.sidebar-foot {
  margin-top: auto;
}

.main-area {
  min-width: 0;
  display: grid;
  grid-template-rows: 64px 1fr;
}

.top-nav {
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid var(--border);
  display: grid;
  grid-template-columns: 220px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 10px 20px;
  background: rgba(18, 18, 21, 0.65);
  backdrop-filter: blur(14px);
}

.page-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #fafafa;
}

.command-bar {
  border: 1px solid var(--border);
  background: rgba(9, 9, 11, 0.6);
  color: var(--muted);
  border-radius: 12px;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.command-bar:hover {
  border-color: #3f3f46;
  color: #e4e4e7;
}

.command-bar span {
  flex: 1;
}

.command-bar kbd {
  border: 1px solid #3f3f46;
  border-bottom-width: 2px;
  border-radius: 6px;
  padding: 2px 6px;
  font-size: 11px;
  color: #d4d4d8;
  background: #18181b;
}

.top-right {
  display: flex;
  align-items: center;
}

.avatar-btn {
  border: 1px solid var(--border);
  background: #101014;
  color: #d4d4d8;
  border-radius: 999px;
  height: 36px;
  padding: 0 8px 0 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.content-wrap {
  min-height: 0;
  overflow: auto;
  padding: 18px;
}

.help-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.help-item {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px 12px;
  color: #d4d4d8;
  background: #101014;
}

.quick-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.quick {
  border: 1px solid var(--border);
  background: #101014;
  color: #d4d4d8;
  border-radius: 10px;
  padding: 7px 10px;
  cursor: pointer;
}

.command-dialog .el-dialog {
  background: #101014;
  border: 1px solid var(--border);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.65);
  border-radius: 14px;
}

.command-dialog .el-dialog__title {
  color: #f4f4f5;
}

.command-dialog .el-input__wrapper {
  background: #09090b;
  border: 1px solid #27272a;
  box-shadow: none;
}

.command-dialog .el-input__inner {
  color: #f4f4f5;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.el-dropdown-menu {
  background: #111114 !important;
  border: 1px solid #27272a !important;
}

.el-dropdown-menu__item {
  color: #d4d4d8 !important;
}

.el-dropdown-menu__item:hover {
  background: #1c1c22 !important;
  color: #fff !important;
}

.el-card {
  background: #121215 !important;
  border-color: #27272a !important;
  color: #f4f4f5 !important;
}

.el-card__header {
  border-bottom-color: #27272a !important;
  color: #e4e4e7 !important;
}

.el-input__wrapper,
.el-textarea__inner,
.el-select__wrapper {
  background: #09090b !important;
  border-color: #27272a !important;
  box-shadow: none !important;
  color: #f4f4f5 !important;
}

.el-input__inner,
.el-textarea__inner,
.el-select__placeholder,
.el-select__selected-item {
  color: #f4f4f5 !important;
}

.el-table {
  --el-table-bg-color: #121215;
  --el-table-tr-bg-color: #121215;
  --el-table-row-hover-bg-color: #17171c;
  --el-table-border-color: #27272a;
  --el-table-header-bg-color: #0f0f13;
  --el-table-text-color: #d4d4d8;
  --el-table-header-text-color: #a1a1aa;
}

.el-dialog {
  background: #121215 !important;
  border: 1px solid #27272a !important;
}

.el-dialog__title {
  color: #fafafa !important;
}

.el-upload-dragger {
  background: #0f0f13 !important;
  border-color: #27272a !important;
}

.el-upload__text,
.el-upload__tip {
  color: #a1a1aa !important;
}
</style>
