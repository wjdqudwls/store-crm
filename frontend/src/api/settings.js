import { notConnected } from './http'

/**
 * 등급 기준·후속 연락 설정 API 겉껍데기. 화면은 이 파일만 부른다.
 * 가짜 데이터(데모·개발)는 ./fake/settings.js, 실서버는 http. 서버에 연결한 함수부터 real 쪽을 채운다 (12단계).
 * 아래 식은 real 빌드에서 상수로 풀려 가짜 코드가 빠진다 (api/mode.js).
 */
const fake = import.meta.env.VITE_API_MODE !== 'real' ? () => import('./fake/settings') : null

export const getGradePolicy = (...args) =>
  fake ? fake().then((m) => m.getGradePolicy(...args)) : notConnected('getGradePolicy')

export const createGradePolicy = (...args) =>
  fake ? fake().then((m) => m.createGradePolicy(...args)) : notConnected('createGradePolicy')

export const saveDiscounts = (...args) =>
  fake ? fake().then((m) => m.saveDiscounts(...args)) : notConnected('saveDiscounts')

export const runPeriodic = (...args) =>
  fake ? fake().then((m) => m.runPeriodic(...args)) : notConnected('runPeriodic')

export const getFollowUpRules = (...args) =>
  fake ? fake().then((m) => m.getFollowUpRules(...args)) : notConnected('getFollowUpRules')

export const saveFollowUpRules = (...args) =>
  fake ? fake().then((m) => m.saveFollowUpRules(...args)) : notConnected('saveFollowUpRules')
