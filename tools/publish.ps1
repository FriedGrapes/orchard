# Orchard 배포: 모노레포 Orchard/ 폴더의 '지금 커밋된 파일'만 공개 저장소 FriedGrapes/orchard 에 새 커밋 하나로 올리고 v<버전> 태그를 붙인다.
# 모노레포의 커밋 기록은 올리지 않는다(예전 커밋에 있던 재배포 불가 파일이 기록으로 새지 않게).
# 태그가 올라가면 공개 저장소의 GitHub Actions(release.yml)가 윈도우·안드로이드를 빌드해 Releases에 올린다.
#   사용: powershell -File Orchard/tools/publish.ps1 -Version 0.1.0
#   태그 없이 소스만 고쳐 올리기(배포 설정만 고칠 때): -NoTag
# (PowerShell 5.1 은 이 파일의 한글을 잘못 읽을 수 있어 화면 출력은 영어로 둔다)
param([Parameter(Mandatory)] [string] $Version, [switch] $NoTag)
$ErrorActionPreference = 'Stop'

function Run([string] $what) {
    if ($LASTEXITCODE -ne 0) { throw "failed: $what (exit $LASTEXITCODE)" }
}

# 저장소는 '지금 폴더'가 아니라 이 스크립트가 들어 있는 곳을 기준으로 찾는다
$repo = (git -C $PSScriptRoot rev-parse --show-toplevel).Trim(); Run "find repo"
$head = (git -C $repo rev-parse --short HEAD).Trim(); Run "read HEAD"
Write-Host "source: $repo @ $head"

$work = Join-Path $env:TEMP "orchard-publish-$Version"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
git clone --quiet https://github.com/FriedGrapes/orchard.git $work; Run "clone"

# 공개 저장소의 파일을 비우고(.git 제외) 모노레포 Orchard/ 의 현재 커밋 내용으로 채운다
Get-ChildItem $work -Force | Where-Object Name -ne '.git' | Remove-Item -Recurse -Force
$tar = Join-Path $env:TEMP "orchard-$Version.tar"
git -C $repo archive --format=tar -o $tar "HEAD:Orchard"; Run "archive"
tar -xf $tar -C $work; Run "extract"
Remove-Item $tar

# 윈도우에서 풀면 실행 권한이 빠지므로 git 에 직접 표시한다
git -C $work add -A; Run "add"
git -C $work update-index --chmod=+x android/gradlew; Run "chmod gradlew"

$msg = if ($NoTag) { "Orchard v$Version (build config fix, $head)" } else { "Orchard v$Version ($head)" }
git -C $work diff --cached --quiet
if ($LASTEXITCODE -eq 0) { Write-Host "nothing changed since the last publish" }
else { git -C $work commit --quiet -m $msg; Run "commit" }
git -C $work push --quiet origin HEAD:main; Run "push main"
if (-not $NoTag) {
    git -C $work tag "v$Version"; Run "tag"
    git -C $work push --quiet origin "v$Version"; Run "push tag"
}
Write-Host "published v$Version ($head). builds: https://github.com/FriedGrapes/orchard/actions"
