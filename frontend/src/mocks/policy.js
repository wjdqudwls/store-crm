/** 등급 기준·할인율·후속 연락 설정 가짜 데이터 (01 BR-04·14 데모 초기값, 03 4.12 등급 기준) */
import { gradeDiscounts } from './data'

export const gradePolicies = [
  {
    version: 2,
    appliesFrom: '2026-06-01',
    periodMonths: 12,
    amountRules: [
      { minValue: 0, score: 0 },
      { minValue: 1000000, score: 10 },
      { minValue: 2000000, score: 20 },
      { minValue: 3000000, score: 40 },
      { minValue: 4000000, score: 60 },
    ],
    ticketRules: [
      { minValue: 0, score: 0 },
      { minValue: 1, score: 10 },
      { minValue: 2, score: 20 },
      { minValue: 3, score: 30 },
      { minValue: 4, score: 40 },
    ],
    gradeCuts: [
      { grade: 'NORMAL', minScore: 0 },
      { grade: 'D', minScore: 20 },
      { grade: 'C', minScore: 40 },
      { grade: 'B', minScore: 70 },
      { grade: 'A', minScore: 100 },
    ],
  },
  {
    version: 1,
    appliesFrom: '2025-06-01',
    periodMonths: 12,
    amountRules: [],
    ticketRules: [],
    gradeCuts: [],
  },
]

export { gradeDiscounts }

export const followUpRules = [
  { id: 1, name: '감사 인사', amount: 2, unit: 'DAY', promotional: false, enabled: true },
  { id: 2, name: '착용감 확인', amount: 2, unit: 'WEEK', promotional: false, enabled: true },
  { id: 3, name: '신상·재방문 안내', amount: 2, unit: 'MONTH', promotional: true, enabled: true },
]
