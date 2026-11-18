/**
 * 가짜/실제 API 고르기. 기본은 가짜(fake)라서 데모·테스트는 그대로 돈다.
 * 실서버 빌드는 `.env.production`의 VITE_API_MODE=real.
 * api/*.js 겉껍데기는 같은 식을 파일 안에서 직접 써서, real 빌드에서 가짜 코드가 빠지게 한다.
 */
export const isFake = import.meta.env.VITE_API_MODE !== 'real'
