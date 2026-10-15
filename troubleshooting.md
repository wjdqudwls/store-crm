# Troubleshooting

## 2026-10-15 · 첫 푸시가 `rejected (fetch first)`로 거부됨

- **원인**: GitHub에서 레포를 만들 때 README(.gitignore, LICENSE)가 함께 생성돼 원격에 이미 커밋이 있었다. 로컬 커밋과 시작점이 달라 푸시가 거부됨.
- **해결**: `git pull origin main --allow-unrelated-histories --no-rebase --no-edit`로 합친 뒤, README.md 충돌 시 `git checkout --ours README.md && git add README.md && git commit --no-edit`, 마지막으로 `git push -u origin main`.
- **예방**: 로컬에 파일이 있는 상태로 연결할 레포는 GitHub에서 README/.gitignore/LICENSE를 체크하지 않고 빈 레포로 만든다.

## 2026-10-15 · pull 후 docs 파일 5개가 add/add 충돌

- **원인**: 원격에 웹으로 먼저 올려둔 이전 버전 문서가 있었고, 로컬에는 같은 경로에 최신 버전이 있었다. 시작점이 다른 기록을 합치면서 같은 파일이 양쪽에서 '추가'로 잡혀 충돌.
- **해결**: 로컬이 최신이므로 `git checkout --ours <충돌 파일들>` → `git add` → `git commit --no-edit` → `git push`. (merge에서 `--ours` = 로컬, `--theirs` = 원격)
- **예방**: 한 곳(로컬 폴더)만 원본으로 정하고, 웹 업로드와 로컬 커밋을 섞지 않는다.
