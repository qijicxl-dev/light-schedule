<template>
  <div class="app-shell">
    <header class="app-shell__header">
      <div class="app-shell__inner">
        <div class="app-shell__brand">
          <span class="app-shell__brand-title">轻排程 MVP 演示</span>
          <span class="app-shell__brand-subtitle">排程工作台、老板驾驶舱与能力分析的最小闭环</span>
        </div>
        <nav class="app-shell__nav" :class="{ 'app-shell__nav--open': mobileMenuOpen }" aria-label="主导航">
          <RouterLink
            v-for="link in navLinks"
            :key="link.to"
            class="app-shell__nav-link"
            :to="link.to"
            @click="mobileMenuOpen = false"
          >
            {{ link.label }}
          </RouterLink>
        </nav>
        <button
          class="app-shell__menu-toggle"
          type="button"
          aria-label="切换导航菜单"
          aria-expanded="false"
          @click="mobileMenuOpen = !mobileMenuOpen"
        >
          <span class="app-shell__menu-bar" :class="{ 'app-shell__menu-bar--open': mobileMenuOpen }" />
        </button>
      </div>
    </header>

    <main class="app-shell__content">
      <div class="app-shell__inner">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const mobileMenuOpen = ref(false)

const navLinks = [
  { to: '/planner', label: '排程工作台' },
  { to: '/dashboard', label: '老板驾驶舱' },
  { to: '/capacity-analysis', label: '能力分析' },
  { to: '/routes', label: '工艺路线' }
]
</script>
