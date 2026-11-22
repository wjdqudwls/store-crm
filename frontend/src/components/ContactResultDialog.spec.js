import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'

const completeFollowUp = vi.fn()
vi.mock('@/api/customers', () => ({ completeFollowUp, createNote: vi.fn() }))
vi.mock('@/api/sales', () => ({ getStaff: vi.fn().mockResolvedValue([]) }))

const task = { id: 7, stage: '다시 연락', dueDate: '2026-11-23', customerName: '김하늘' }

async function mountDialog() {
  setActivePinia(createPinia())
  const { default: ContactResultDialog } = await import('./ContactResultDialog.vue')
  const { useSessionStore } = await import('@/stores/session')
  useSessionStore().me = { id: 4, role: 'MANAGER', name: '강남 매니저', storeId: 1 }
  const w = mount(ContactResultDialog, {
    props: { modelValue: true, task },
    global: { plugins: [ElementPlus] },
    attachTo: document.body,
  })
  await flushPromises()
  return w
}

const button = (label) =>
  [...document.querySelectorAll('.choices button')].find((b) => b.textContent.trim() === label)

describe('연락 결과 창: 할 일 완료의 다시 연락할 날 (03 complete reminder, CAL-11)', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_API_MODE', 'real')
    completeFollowUp.mockReset().mockResolvedValue({})
    document.body.innerHTML = ''
    // jsdom에는 matchMedia가 없다 (폰 레이아웃 판단용)
    window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} })
  })

  it('should_다시_연락할_날_칸을_보임_when_할_일_완료에서_재연락을_고름(실서버)', async () => {
    const w = await mountDialog()
    expect(document.body.textContent).not.toContain('다시 연락할 날')
    button('재연락').click()
    await flushPromises()
    expect(document.body.textContent).toContain('다시 연락할 날')
    w.unmount()
  })

  it('should_숨김_when_재연락이_아님(통화_완료)', async () => {
    const w = await mountDialog()
    button('통화 완료').click()
    await flushPromises()
    expect(document.body.textContent).not.toContain('다시 연락할 날')
    w.unmount()
  })
})
