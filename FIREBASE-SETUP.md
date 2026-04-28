# إعداد Firebase Crashlytics لتطبيق وميض

## الخطوات المطلوبة:

### 1. إنشاء مشروع Firebase
1. اذهب إلى [Firebase Console](https://console.firebase.google.com/)
2. انقر على "Add project"
3. أدخل اسم المشروع: "Wameed"
4. اختر حساب Google للتحليلات (اختياري)
5. انتظر حتى يتم إنشاء المشروع

### 2. إضافة تطبيق Android
1. في Firebase Console، انقر على "Add app"
2. اختر أيقونة Android
3. أدخل معلومات التطبيق:
   - **Package name**: `com.wameed`
   - **App nickname**: `وميض`
   - **Debug signing certificate**: اتركه فارغاً الآن
4. انقر على "Register app"

### 3. تنزيل ملف التكوين
1. بعد التسجيل، انقر على "Download google-services.json"
2. ضع الملف في: `app/google-services.json`

### 4. التحقق من الإعدادات
تأكد من أن الملفات التالية تحتوي على الإعدادات الصحيحة:

#### `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")  // ✅ موجود
    id("com.google.firebase.crashlytics") // ✅ موجود
}

dependencies {
    // ... dependencies أخرى
    
    // Firebase Crashlytics for error tracking
    implementation("com.google.firebase:firebase-crashlytics-ktx:18.6.2") // ✅ موجود
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.1") // ✅ موجود
    
    // Google Play In-App Update
    implementation("com.google.android.play:app-update:2.1.0") // ✅ موجود
    implementation("com.google.android.play:app-update-ktx:2.1.0") // ✅ موجود
}
```

#### `build.gradle.kts` (المشروع الرئيسي):
```kotlin
plugins {
    // ... plugins أخرى
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}
```

### 5. تفعيل Crashlytics
1. في Firebase Console، اذهب إلى قسم "Crashlytics"
2. انقر على "Enable Crashlytics"

### 6. اختبار التتبع
للتأكد من أن Crashlytics يعمل بشكل صحيح:

```kotlin
// في أي مكان في الكود (للاختبار فقط)
WameedCrashReporter.getInstance().logError("Test crash", RuntimeException("This is a test"))
```

## الميزات المضافة:

### ✅ نظام تتبع الأخطاء (Crashlytics)
- تتبع تلقائي للأعطال
- معلومات الجهاز الكاملة
- سجلات مخصصة
- تقارير المستخدمين

### ✅ نظام التحديث التلقائي
- تحقق من التحديثات عند بدء التطبيق
- واجهة عربية احترافية
- شريط تقدم للتحميل
- تحديث اختياري للمستخدمين

### ✅ تحسين شاشة البداية
- تأثير وميض أكثر دراماتيكية
- حركات سلسة واحترافية
- تدرجات لونية محسّنة
- الحفاظ على الهوية البصرية

### ✅ نظام الإبلاغ عن المشاكل
- واجهة سهلة الاستخدام
- إرسال تلقائي لمعلومات الجهاز
- ربط مع تقارير Crashlytics

## ملاحظات هامة:

1. **وضع التصحيح**: في وضع DEBUG، لا يتم إرسال التقارير تلقائياً
2. **التحديثات**: تعمل فقط مع التطبيقات المنشورة على Google Play Store
3. **الأذونات**: جميع الأذونات المطلوبة مضافة في AndroidManifest.xml
4. **الواجهة**: جميع النصوص باللغة العربية مدعومة

## الخطوات التالية:

1. أكمل إعداد Firebase كما هو موضح أعلاه
2. اختبر التطبيق على جهاز حقيقي
3. انشر نسخة تجريبية على Google Play Console
4. راصد التقارير في Firebase Console

## دعم فني:

إذا واجهت أي مشاكل:
- تحقق من وجود `google-services.json` في المكان الصحيح
- تأكد من أن package name يتطابق مع Firebase
- راقب سجل الأخطاء في Logcat لأي رسائل من Firebase
