// 실서버 빌드(dist, VITE_API_MODE=real)에 가짜 데이터·가짜 API가 섞이지 않았는지 확인한다.
// 사용: npm run build && node scripts/check-real-build.mjs
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import process from 'node:process'

// 가짜 데이터에만 있는 말 (화면 안내문의 예시 글자는 제외)
const MARKERS = ['fakeResponse', 'fakeError', '윤가람', '최유나', 'GN01-2026', '오세진 ****']
const files = []
;(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p)
    else if (/\.(js|css|html)$/.test(name)) files.push(p)
  }
})('dist')

const found = []
for (const f of files) {
  const text = readFileSync(f, 'utf8')
  for (const m of MARKERS) if (text.includes(m)) found.push(`${f}: ${m}`)
  if (/(^|\/)(data|accounts|customerDetail|notifications)-[\w-]+\.js$/.test(f)) {
    found.push(`${f}: 가짜 데이터 파일이 들어 있음`)
  }
}
if (found.length) {
  console.error('실서버 빌드에 가짜 데이터가 들어 있습니다:\n' + found.join('\n'))
  process.exit(1)
}
console.log(`실서버 빌드 확인 완료: 가짜 데이터 없음 (${files.length}개 파일)`)
