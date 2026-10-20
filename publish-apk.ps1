# ============================================================
#  إنشاء مستودع GitHub وبناء APK تلقائياً — تشغيل بأمر واحد
#  الاستخدام:  .\publish-apk.ps1                 (بناء release)
#              .\publish-apk.ps1 -Build debug    (بناء debug)
#              .\publish-apk.ps1 -Private        (مستودع خاص)
# ============================================================
param(
  [string]$RepoName = "gold-daily-app",
  [ValidateSet("release","debug")]
  [string]$Build = "release",
  [switch]$Private
)

$ErrorActionPreference = "Stop"
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

function Step($t) { Write-Host ""; Write-Host "==> $t" -ForegroundColor Cyan }
function Ok($t)   { Write-Host "    $t" -ForegroundColor Green }
function Warn($t) { Write-Host "    $t" -ForegroundColor Yellow }

# أدوات gh/git تكتب تحذيرات على stderr حتى عند النجاح، ومع
# $ErrorActionPreference="Stop" يحوّلها PowerShell 5.1 إلى خطأ قاتل.
# لذلك نغلّف كل نداء خارجي بهذه الدالة التي تعيد رمز الخروج فقط.
function Invoke-Native {
  param([scriptblock]$Block)
  $old = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    & $Block 2>&1 | Out-Null
    return $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $old
  }
}

# نسخة تُظهر المخرجات (للأوامر التفاعلية/الطويلة)
function Invoke-NativeVisible {
  param([scriptblock]$Block)
  $old = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    & $Block 2>&1 | ForEach-Object { Write-Host $_ }
    return $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $old
  }
}

Set-Location $PSScriptRoot

# ---------- 1) التحقق من gh ----------
Step "التحقق من GitHub CLI"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
  throw "gh غير مثبّت. ثبّته بالأمر:  winget install GitHub.cli"
}
$ghVersion = (gh --version | Select-Object -First 1)
Ok "gh موجود: $ghVersion"

# ---------- 2) تسجيل الدخول ----------
Step "التحقق من تسجيل الدخول إلى GitHub"
$authed = ((Invoke-Native { gh auth status }) -eq 0)

if (-not $authed) {
  Warn "غير مسجّل الدخول. سيفتح المتصفح لتسجيل الدخول (مرة واحدة فقط)."
  $rc = Invoke-NativeVisible { gh auth login --hostname github.com --git-protocol https --web }
  if ($rc -ne 0) { throw "تعذّر تسجيل الدخول." }
}
$user = (gh api user --jq .login 2>$null)
Ok "مرحباً $user"

# ---------- 3) تهيئة git إن لزم ----------
Step "تهيئة المستودع المحلي"
if (-not (Test-Path ".git")) { Invoke-Native { git init -q } | Out-Null; Ok "أُنشئ git" }
Invoke-Native { git branch -M main } | Out-Null
if (-not (git config user.name))  { git config user.name  "Gold Daily" }
if (-not (git config user.email)) { git config user.email "bawazir528@gmail.com" }

Invoke-Native { git add -A } | Out-Null
$clean = ((Invoke-Native { git diff --cached --quiet }) -eq 0)
if ($clean) {
  Ok "لا تعديلات جديدة"
} else {
  Invoke-Native { git -c core.safecrlf=false commit -q -m "تحديث قبل البناء" } | Out-Null
  Ok "حُفظت التعديلات"
}

# ---------- 4) إنشاء المستودع ورفعه ----------
Step "إنشاء مستودع GitHub والرفع"
$full = "$user/$RepoName"
$exists = ((Invoke-Native { gh repo view $full }) -eq 0)

if ($exists) {
  Warn "المستودع $full موجود مسبقاً — سأرفع عليه."
  Invoke-Native { git remote remove origin } | Out-Null
  Invoke-Native { git remote add origin "https://github.com/$full.git" } | Out-Null
  $rc = Invoke-NativeVisible { git push -u origin main --force }
} else {
  $vis = "--public"
  if ($Private) { $vis = "--private" }
  $rc = Invoke-NativeVisible { gh repo create $RepoName $vis --source=. --remote=origin --push }
}
if ($rc -ne 0) { throw "تعذّر الرفع إلى GitHub." }
Ok "تم الرفع: https://github.com/$full"

# ---------- 5) تشغيل البناء ----------
Step "تشغيل البناء على GitHub Actions ($Build)"
$rc = Invoke-Native { gh workflow run build-apk.yml -f build_type=$Build }
if ($rc -ne 0) { throw "تعذّر تشغيل البناء." }
Start-Sleep -Seconds 5

$runId = (gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null)
if (-not $runId) {
  Warn "لم أجد التشغيل. افتح تبويب Actions يدوياً."
  Warn "https://github.com/$full/actions"
  exit 1
}

Ok "رقم التشغيل: $runId"
Step "متابعة البناء (قد يستغرق 3-6 دقائق)"
$rc = Invoke-NativeVisible { gh run watch $runId --exit-status }
if ($rc -ne 0) { throw "فشل البناء. راجع السجل: https://github.com/$full/actions/runs/$runId" }

# ---------- 6) تنزيل APK ----------
Step "تنزيل ملف APK"
$outDir = Join-Path $PSScriptRoot "apk-output"
if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$rc = Invoke-Native { gh run download $runId -n gold-daily-apk -D $outDir }
if ($rc -ne 0) { throw "تعذّر تنزيل الـ APK." }
$apk = Get-ChildItem $outDir -Filter *.apk -Recurse | Select-Object -First 1
if (-not $apk) { throw "لم يُعثر على APK في المخرجات." }

$finalApk = Join-Path $PSScriptRoot "gold-daily.apk"
Move-Item $apk.FullName $finalApk -Force
Remove-Item $outDir -Recurse -Force

$size = [math]::Round((Get-Item $finalApk).Length / 1MB, 2)
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host " تم إنشاء: $finalApk  ($size MB)" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "انقل الملف إلى الجوال وثبّته (فعّل: تثبيت من مصادر غير معروفة)."
Write-Host "ملاحظة: النسخة موقّعة بمفتاح debug. لتوقيع دائم راجع README."
Write-Host ""

Start-Process explorer.exe -ArgumentList ("/select,`"$finalApk`"")
