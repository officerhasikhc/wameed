# دليل التطوير والتشخيص — وميض 🛠️

> دليل شامل للمطور المبتدئ. احفظه وارجع له متى احتجت.

---

## 🧭 المحتويات

1. [البيئة: Dev vs Production](#1-البيئة-dev-vs-production)
2. [تشغيل برنامج PC في وضع التطوير والتشخيص](#2-تشغيل-برنامج-pc-في-وضع-التطوير-والتشخيص)
3. [قراءة ملف السجل `wameed.log`](#3-قراءة-ملف-السجل-wameedlog)
4. [تطوير تطبيق الأندرويد](#4-تطوير-تطبيق-الأندرويد)
5. [Logcat — كيف تراقب الأندرويد](#5-logcat--كيف-تراقب-الأندرويد)
6. [بعد كل تعديل — ما الذي يجب فعله؟](#6-بعد-كل-تعديل--ما-الذي-يجب-فعله)
7. [توزيع التطبيق للأصدقاء](#7-توزيع-التطبيق-للأصدقاء)
8. [استكشاف الأعطال عند المستخدم](#8-استكشاف-الأعطال-عند-المستخدم)

---

## 1. البيئة: Dev vs Production

### ما الفرق؟
| البيئة | أين تعمل؟ | السرعة في رؤية التعديل | لمن؟ |
|--------|-----------|------------------------|------|
| **Development** (تطوير) | مباشرة من الكود المصدري (Python/Kotlin) | **فورية** — تعدّل وتشغل مباشرة | **أنت فقط** |
| **Production** (نسخة نهائية) | `.exe` أو `.apk` مبنيّة ومُجمّعة | بطيئة — تحتاج بناء كامل | **الأصدقاء والمستخدمون** |

### القاعدة الذهبية
- **أثناء كتابة الكود**: دائماً Dev (أسرع ×100).
- **عند الانتهاء من ميزة/إصلاح**: ابنِ النسخة النهائية واختبرها مرة واحدة.
- **قبل إرسال الأصدقاء**: ابنِ + اختبر على جهازك الخاص من المثبّت/APK.

---

## 2. تشغيل برنامج PC في وضع التطوير والتشخيص

### ✅ الطريقة 1: تطوير عادي (الأسرع)
افتح PowerShell في مجلد المشروع:

```powershell
cd C:\Users\super\AndroidStudioProjects\wameed\windows-receiver
.\scripts\dev_run.bat
```

**ما يحدث**:
- ينفتح terminal أسود (console).
- تظهر رسائل Python بشكل مباشر: `2026-04-18 13:24:26 [INFO] wameed: ...`.
- واجهة وميض تظهر كنافذة عادية.
- **أي `print()` أو `logger.info()` يطبع في الـ terminal فوراً** — هذا هو التشخيص الحي.

**للإيقاف**: اضغط `Ctrl+C` في الـ terminal أو أغلق نافذة وميض.

---

### ✅ الطريقة 2: تشغيل النسخة المبنيّة مع console للتشخيص
هذه لو المستخدم اشتكى من مشكلة ولا تظهر لك في Dev:

```powershell
cd C:\Users\super\AndroidStudioProjects\wameed\windows-receiver
.\scripts\debug_run.bat
```

**الفرق عن `build.bat`**:
- `build.bat` → يبني `Wameed.exe` **بدون console** (نسخة نهائية).
- `debug_run.bat` → يبني `Wameed.exe` **مع console مرئي** ثم يشغله.

**النتيجة**: نافذة CMD سوداء تبقى مفتوحة بجانب وميض، وتُظهر كل خطأ Python يحدث.

---

### ✅ الطريقة 3: تشغيل ملف Python مباشرة (بدون batch)
لو أردت التحكم الكامل:

```powershell
cd C:\Users\super\AndroidStudioProjects\wameed\windows-receiver\src
python receiver.py
```

يعمل مثل الطريقة 1 تماماً.

---

### 🔧 متطلبات Dev (مرة واحدة فقط)
```powershell
# من مجلد windows-receiver:
pip install -r src\requirements.txt
```

لو ظهرت رسالة `pip not found`: ثبّت Python من [python.org](https://www.python.org/downloads/) وفعّل `Add to PATH` أثناء التثبيت.

---

## 3. قراءة ملف السجل `wameed.log`

### أين هو؟
```
C:\Users\<اسمك>\.wameed\wameed.log
```
في جهازك: `C:\Users\super\.wameed\wameed.log`

### كيف تفتحه؟

**طريقة سريعة** — في PowerShell:
```powershell
# عرض آخر 50 سطر
Get-Content $env:USERPROFILE\.wameed\wameed.log -Tail 50

# متابعة حية (مثل tail -f على Linux)
Get-Content $env:USERPROFILE\.wameed\wameed.log -Wait -Tail 20
```

**طريقة يدوية**:
1. اضغط `Win + R`
2. اكتب: `%USERPROFILE%\.wameed`
3. Enter → مجلد يفتح، افتح `wameed.log` بـ Notepad أو VS Code.

### ماذا تقرأ؟
```
2026-04-18 13:24:26 [INFO] wameed: Wameed starting (pid=17252, frozen=True)
2026-04-18 13:24:26 [INFO] wameed: asyncio policy set to WindowsSelectorEventLoopPolicy
2026-04-18 13:24:27 [INFO] wameed: Starting WS server on 0.0.0.0:7788
2026-04-18 13:25:01 [INFO] wameed: Phone announced: Samsung Galaxy S22
2026-04-18 13:25:45 [ERROR] wameed: UDP sendto failed
Traceback (most recent call last): ...
```

**دلالات**:
- `[INFO]` = أحداث طبيعية
- `[WARNING]` = مشاكل بسيطة لا تمنع التشغيل
- `[ERROR]` = خطأ — ابحث عن `Traceback` بعده لمعرفة السطر الفعلي
- `[CRITICAL]` = تعطّل كامل

### 💡 نصيحة: زر "تشخيص" داخل وميض
في نافذة وميض → زر **🔍 تشخيص** → يعرض مربع حوار فيه:
- حالة الخادم
- عنوان IP والمنفذ
- مسار السجل
- آخر 30 سطر من السجل

هذا هو أول ما تطلبه من مستخدم لديه مشكلة.

---

## 4. تطوير تطبيق الأندرويد

### السؤال الكبير: هل Android Studio Run يغني عن تثبيت APK؟

**نعم تماماً** — `Run ▶` في Android Studio:
1. يبني APK.
2. يثبته على الجهاز المتصل (USB أو Wi-Fi).
3. يفتحه فوراً.

**بالمقارنة**:
| الإجراء | بناء APK يدوي | Android Studio Run |
|--------|----------------|---------------------|
| الوقت | 30-60 ثانية | 10-20 ثانية |
| يتطلب USB Debugging | نعم | نعم |
| يُظهر Logcat | لا | ✅ نعم، تلقائياً |
| يدعم Debugger (breakpoints) | لا | ✅ نعم |
| مناسب لـ | اختبار نهائي + توزيع | التطوير اليومي |

### 🔄 هل كل تعديل ينطبق تلقائياً؟
**لا**. Android لا يعمل مثل موقع ويب. كل تعديل Kotlin يتطلب:
1. حفظ الملف (`Ctrl+S`).
2. ضغط `Run ▶` (أو `Shift+F10`) في Android Studio.
3. Gradle يبني → يُعيد تثبيت → يشغّل.

**لكن** Android Studio يوفّر:
- **Apply Changes** (`Ctrl+Alt+F10` — أيقونة البرق ⚡): يحقن التعديلات بدون إعادة تشغيل — **أسرع بكثير** لتعديلات UI بسيطة.
- **Apply Code Changes** (`Ctrl+F10`): للتعديلات في الدوال فقط.

### الفرق بين Debug APK و Release APK
| | Debug APK | Release APK |
|---|----------|-------------|
| التوقيع | مفتاح تطوير تلقائي | يحتاج keystore خاص بك |
| التحذير عند التثبيت | "تطبيق تجريبي" | لا تحذير |
| الحجم | أكبر | أصغر (بفضل ProGuard) |
| الأداء | أبطأ قليلاً | أسرع |
| مناسب لـ | اختبار + أصدقاء مقربين | النشر على Google Play |

**للأصدقاء**: Debug APK كافٍ تماماً. يظهر تحذير صغير فقط.

---

## 5. Logcat — كيف تراقب الأندرويد

### من Android Studio
1. افتح نافذة **Logcat** (أسفل الشاشة، أو `View > Tool Windows > Logcat`).
2. اختر جهازك من dropdown أعلى النافذة.
3. اختر التطبيق: `com.wameed`.
4. اكتب في مربع الفلتر: `WameedKeepAlive` أو `WameedSender` لرؤية tags محددة.

**مثال على ما ستراه**:
```
D  WameedKeepAlive       refresh() — idle timer reset
I  WameedKeepAlive       WS open — sending hello
I  WameedKeepAlive       Idle 300000ms >= 300000ms — auto-stop
W  WameedSender          Preflight TCP failed for 192.168.1.5:7788
```

### من CMD/PowerShell (بدون Android Studio)
لو أردت متابعة Logcat من terminal:

```powershell
# مسار adb (مع Android Studio):
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

# عرض Logcat حي مع فلترة tags الخاصة بوميض:
& $adb logcat -s WameedKeepAlive:* WameedSender:* WameedDiscovery:*

# حفظ Logcat لملف (لإرساله لك من صديق):
& $adb logcat -d > wameed-logcat.txt
```

---

## 6. بعد كل تعديل — ما الذي يجب فعله؟

### سيناريوهات شائعة

#### عدّلت `receiver.py` (PC)
```
1. احفظ الملف
2. أغلق وميض القديم (من الـ tray أو "⏻ إنهاء البرنامج")
3. .\scripts\dev_run.bat
4. اختبر
5. عند الرضى → .\scripts\build.bat (يُنتج المثبّت)
```

#### عدّلت `MainActivity.kt` (Android)
```
1. احفظ الملف
2. اضغط Run ▶ (أو Apply Changes ⚡ لتعديل UI بسيط)
3. اختبر على الجهاز
4. عند الرضى → Build > Build APK(s) لإنشاء APK للتوزيع
```

#### عدّلت في الطرفين
```
1. Android أولاً (أبطأ) — Run ▶
2. PC ثانياً — dev_run.bat (أسرع لإعادة التشغيل)
3. اختبر التفاعل بين الاثنين
```

### 📝 دورة العمل النموذجية
```
[تعديل كود] ─┐
             ├─→ [اختبار Dev] ─→ [نجح؟] ──لا──┐
             │                    │           │
             │                  نعم           └──→ [راقب السجل/Logcat]
             │                    ↓                      │
             │              [بناء Release]               │
             │                    ↓                      │
             │              [اختبار من المثبّت]           │
             │                    ↓                      │
             │              [توزيع للأصدقاء]             │
             └────────────────────────────────────────────┘
```

---

## 7. توزيع التطبيق للأصدقاء

### المكونات التي ترسلها
| الملف | الحجم | الغرض |
|------|------|------|
| `WameedSetup-1.0.0.exe` | ~33 MB | مثبّت Windows (يثبّت كل شيء) |
| `app-debug.apk` | ~12 MB | تطبيق Android |
| `README-للصديق.txt` | صغير | تعليمات التثبيت بالعربية |

### خطوات التجميع (من المشروع)

افتح PowerShell في مجلد المشروع وشغّل:

```powershell
# 1. تأكد من وجود آخر نسخ مبنيّة
cd C:\Users\super\AndroidStudioProjects\wameed

# 2. جمع الملفات في مجلد release\
Remove-Item -Recurse -Force release -ErrorAction SilentlyContinue
New-Item -ItemType Directory release | Out-Null
Copy-Item windows-receiver\installer\Output\WameedSetup-1.0.0.exe release\
Copy-Item app\build\outputs\apk\debug\app-debug.apk release\Wameed-Android.apk

# 3. (اختياري) إضافة تعليمات
Copy-Item INSTALL-للصديق.txt release\ -ErrorAction SilentlyContinue

# 4. ضغط المجلد في zip
Compress-Archive -Path release\* -DestinationPath Wameed-v1.0.0.zip -Force
```

النتيجة: `Wameed-v1.0.0.zip` جاهز للإرسال عبر واتساب / Drive / أي شيء.

### 🔒 ضمان عدم فشل التثبيت عند الأصدقاء

#### Windows
| مشكلة محتملة | الحل |
|---------------|-----|
| "لم يتم التعرف على الناشر" من SmartScreen | يضغط "More info" → "Run anyway" (طبيعي لبرامج غير مُوقّعة) |
| جدار الحماية يمنع الاتصال | المثبّت يضيف قاعدة firewall تلقائياً (موجودة في `wameed.iss`) |
| منفذ 7788 مستخدم | ستظهر رسالة في الواجهة. الحل: أغلق النسخة السابقة أو غيّر المنفذ من الإعدادات |
| Windows Defender يحذف الملف | يضيفونه في الاستثناءات (نادر مع المثبّت من Inno Setup) |

#### Android
| مشكلة محتملة | الحل |
|---------------|-----|
| "التثبيت محظور" | إعدادات → الأمان → السماح بتثبيت من مصدر غير معروف لـ (المتصفح/واتساب) |
| "التطبيق لم يُثبّت" | ربما نسخة قديمة موجودة بتوقيع مختلف — يحذفها أولاً |
| لا يجد الكمبيوتر | **PC والهاتف على نفس Wi-Fi** (ليس Wi-Fi للضيوف المعزول) |
| الإرسال يفشل | يفتح وميض على PC أولاً ويضغط "تشخيص" |

### تعليمات جاهزة للأصدقاء

سأنشئها لك في `release\INSTALL-للصديق.txt` في الخطوة التالية.

---

## 8. استكشاف الأعطال عند المستخدم

### سيناريو: "وميض لا يعمل عندي"

اطلب منه:

1. **لقطة شاشة من مربع "🔍 تشخيص"** في وميض على PC.
2. **نسخة من `wameed.log`**:
   ```
   Win+R → %USERPROFILE%\.wameed → أرسل wameed.log
   ```
3. **لو المشكلة في الأندرويد** — Logcat:
   - لو الصديق مبرمج: `adb logcat > log.txt` وأرسله.
   - لو مستخدم عادي: اطلب منه تطبيق [Logcat Reader](https://play.google.com/store/apps/details?id=com.dp.logcatapp) → يلتقط السجل ويرسله.

### المشاكل الأكثر شيوعاً

| العرض | السبب المحتمل | الحل |
|------|---------------|-----|
| PC يعرض "في انتظار الاتصال" دائماً رغم أن الهاتف يحاول | شبكتان مختلفتان، أو Wi-Fi للضيوف معزول | نفس الشبكة الفعلية |
| PC يعرض "مشكلة في الاتصال — يبدو أن وميض يعمل بالفعل" | نسختان مفتوحتان | أنهِ الكل من Task Manager |
| الهاتف يقول "الكمبيوتر غير متاح" | PC مغلق أو غير موصول | شغّل PC |
| الهاتف يقول "الكمبيوتر يستجيب لكن وميض لا يعمل" | وميض على PC متوقف/تعطّل | شغّل وميض |
| الإرسال يبدأ ثم ينقطع | شبكة ضعيفة جداً أو PC دخل sleep | فعّل keep-alive في الإعدادات |

---

## 🎓 ملخص سريع — ما تحتاج حفظه

```
Dev PC:      .\scripts\dev_run.bat          (terminal مع logs حية)
Dev Android: Run ▶ في Android Studio        (تثبيت + Logcat تلقائي)
بناء PC:     .\scripts\build.bat            (ينتج المثبّت)
بناء APK:    Build > Build APK(s)            (ينتج app-debug.apk)
السجل:       $env:USERPROFILE\.wameed\wameed.log
Logcat:      نافذة Logcat في Android Studio أو adb logcat
```

**عند الشك** → شغّل `dev_run.bat` + راقب الـ terminal → الأخطاء ستظهر فوراً.
