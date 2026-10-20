تطبيق أندرويد — الحركة اليومية لمبيعات الذهب
=============================================

تطبيق يغلّف نموذج الويب (Google Apps Script) داخل WebView، ويدعم:
• طلب رابط `/exec` عند أول فتح وحفظه على الجهاز.
• زر الإغلاق داخل النموذج: ينهي التطبيق بالكامل ويزيله من التطبيقات الأخيرة.
• زر الطباعة: عبر نظام الطباعة في أندرويد (طابعة أو حفظ PDF).

---

## 1) البناء السحابي (لا يحتاج أي برنامج على جهازك)

### أ) ارفع المشروع إلى GitHub

المشروع محفوظ كـ Git repository مع أول commit. أنشئ مستودعاً فارغاً (بدون README) على GitHub
ثم نفّذ من داخل مجلد `gold-android`:

```
git remote add origin https://github.com/<حسابك>/gold-android.git
git branch -M main
git push -u origin main
```

> أو ارفع مجلد `gold-android` كملف ZIP عبر واجهة GitHub (Add file ← Upload files).

### ب) شغّل البناء

1. افتح المستودع ← تبويب **Actions**.
2. اختر **Build APK** ← زر **Run workflow**.
3. اختر `release` (أو `debug` لنسخة تجريبية) ثم شغّله.

عند انتهائه (٢–٤ دقائق) نزّل الـ APK:
**Actions ← آخر تشغيل ← قسم Artifacts ← `gold-daily-apk`**

داخله `gold-daily.apk` — انسخه للجوال وثبّته (فعّل «تثبيت من مصادر غير معروفة»).

---

## 2) توقيع النسخة (مهم للاستخدام الحقيقي)

بدون توقيع، ينتج البناء APK غير موقّع قد يرفضه أندرويد. لتوقيع دائم:

### أ) أنشئ keystore (مرة واحدة)

على أي جهاز فيه Java:

```
keytool -genkeypair -v -keystore release.keystore -alias golddaily \
  -keyalg RSA -keysize 2048 -validity 10000
```

### ب) حوّله إلى Base64

Windows (PowerShell):
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Content keystore.txt
```

Linux/Mac:
```
base64 -w0 release.keystore > keystore.txt
```

### ج) أضف Secrets في GitHub

المستودع ← Settings ← Secrets and variables ← Actions ← New repository secret:

| الاسم | القيمة |
|---|---|
| `KEYSTORE_BASE64` | محتوى `keystore.txt` |
| `KEYSTORE_PASSWORD` | كلمة مرور الـ keystore |
| `KEY_ALIAS` | `golddaily` |
| `KEY_PASSWORD` | كلمة مرور المفتاح |

بعدها كل بناء `release` يكون **موقّعاً** تلقائياً.

> ⚠️ احتفظ بـ `release.keystore` في مكان آمن. فقدانه يعني عدم القدرة على تحديث التطبيق
> فوق نسخة مثبّتة (يجب إلغاء التثبيت أولاً).

---

## 3) البناء المحلي (اختياري)

يتطلب: JDK 17 + Android SDK (أو Android Studio).

```
gradle assembleDebug      # APK تجريبي
gradle assembleRelease    # APK للاستخدام الحقيقي
```

الناتج في: `app/build/outputs/apk/<النوع>/`

للتوقيع محلياً أنشئ `keystore.properties` في جذر المشروع:

```
storeFile=C:\\path\\to\\release.keystore
storePassword=...
keyAlias=golddaily
keyPassword=...
```

---

## 4) قبل التثبيت: تأكد من نشر النموذج

- انشر Web app بصلاحية **«أي شخص» (Anyone)** وإلا لن يفتح التطبيق.
- رابط النموذج هو نفسه المستخدم في `gold-app/index.html` (ينتهي بـ `/exec`).
- أدخل الرابط في شاشة الإعداد عند أول فتح للتطبيق.

---

## 5) بنية المشروع

```
gold-android/
├─ .github/workflows/build-apk.yml   ← البناء السحابي
├─ settings.gradle                   ← اسم المشروع والموديول
├─ build.gradle                      ← إعدادات Gradle الجذرية
├─ gradle.properties
└─ app/
   ├─ build.gradle                   ← إعدادات الموديول + التوقيع
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/bawazir/golddaily/
      │  ├─ SetupActivity.java       ← شاشة رابط /exec
      │  ├─ MainActivity.java        ← WebView + جسور الطباعة/الإغلاق
      │  └─ Prefs.java               ← تخزين الرابط
      └─ res/
         ├─ layout/                  ← activity_setup.xml, activity_main.xml
         ├─ values/                  ← strings.xml, themes.xml
         ├─ xml/file_paths.xml
         └─ mipmap-*/                ← أيقونات التطبيق
```

---

## 6) الجسور بين النموذج والتطبيق

| في الصفحة | النتيجة في التطبيق |
|---|---|
| `window.AndroidApp.printHtml(html)` | يفتح نظام الطباعة (طابعة / حفظ PDF) |
| `window.AndroidApp.printPdf(base64)` | يحفظ PDF ويشاركه |
| `window.AndroidApp.closeApp()` | ينهي التطبيق ويزيله من التطبيقات الأخيرة |
| `window.parent.postMessage("gold-close")` | يُستخدم في نسخة PWA |

هذه الجسور مطبّقة في `Code.gs` (إصدار 31 وما بعده) — لا تحتاج تعديلاً.

---

## 7) ملاحظات

- تطبيق أندرويد **لا يمكنه إغلاق نفسه** إلا عبر `finishAndRemoveTask()`، وهذا ما يفعله الزر.
- إن ظهرت شاشة «تعذّر تحميل النموذج»، تحقق من: الاتصال، صلاحية النشر «أي شخص»، وصحة الرابط.
- لآيفون: لا يمكن بناء تطبيق أصلي دون Mac وحساب مطوّر من Apple — البديل هو PWA في `gold-app`.
