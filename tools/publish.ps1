# Orchard 배포: 모노레포 Orchard/ 폴더의 '지금 파일'만 공개 저장소 FriedGrapes/orchard 에 새 커밋 하나로 올리고 v<버전> 태그를 붙인다.
# 모노레포의 커밋 기록은 올리지 않는다(예전 커밋에 있던 재배포 불가 파일이 기록으로 새지 않게).
# 태그가 올라가면 공개 저장소의 GitHub Actions(release.yml)가 윈도우·안드로이드를 빌드해 Releases에 올린다.
#   사용: powershell -File Orchard/tools/publish.ps1 -Version 0.1.0
#   태그 없이 소스만 고쳐 올리기(배포 설정만 고칠 때): -NoTag
param([Parameter(Mandatory)] [string] $Version, [switch] $NoTag)
$ErrorActionPreference = 'Stop'
$repo = (git rev-parse --show-toplevel).Trim()
$work = Join-Path $env:TEMP "orchard-publish-$Version"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }

git clone --quiet https://github.com/FriedGrapes/orchard.git $work
# 공개 저장소의 파일을 비우고(.git 제외) 모노레포 Orchard/ 의 현재 커밋 내용으로 채운다
Get-ChildItem $work -Force | Where-Object Name -ne '.git' | Remove-Item -Recurse -Force
$tar = Join-Path $env:TEMP "orchard-$Version.tar"
git -C $repo archive --format=tar -o $tar "HEAD:Orchard"
tar -xf $tar -C $work
Remove-Item $tar

git -C $work add -A
$msg = if ($NoTag) { "Orchard v$Version (build config fix)" } else { "Orchard v$Version" }
git -C $work commit --quiet -m $msg
git -C $work push --quiet origin HEAD:main
if (-not $NoTag) {
    git -C $work tag "v$Version"
    git -C $work push --quiet origin "v$Version"
}
Write-Host "공개 저장소에 v$Version 을 올렸어요. 빌드: https://github.com/FriedGrapes/orchard/actions"
