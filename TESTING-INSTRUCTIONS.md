# تعليمات اختبار Firebase Crashlytics

## ✅ تم تحديث الإعدادات بنجاح

### 1. التحديثات التي تم تنفيذها:
- **Google Services**: 4.4.2 → 4.4.4 ✅
- **Crashlytics**: 3.0.2 → 3.0.7 ✅
- **Firebase BoM**: 34.12.0 ✅
- **المكتبات**: تم تحديثها لاستخدام BoM ✅

### 2. الميزات المضافة:
- **زر اختبار العطل** (فقط في وضع DEBUG) ✅
- **زر الإبلاغ عن المشاكل** ✅
- **نظام تتبع الأخطاء المدمج** ✅

## 🧪 خطوات الاختبار:

### الخطوة 1: إعداد Firebase
1. اذهب إلى [Firebase Console](https://console.firebase.google.com/)
2. أنشئ مشروع جديد أو استخدم مشروع موجود
3. أضف تطبيق Android باسم الحزمة: `com.wameed`
4. حمّل ملف `google-services.json` إلى مجلد `app/`

### الخطوة 2: اختبار Crashlytics
1. شغّل التطبيق على جهاز حقيقي (لا المحاكي)
2. اذهب إلى تبويب "الإعدادات"
3. ستجد زر "اختبار العطل" (أحمر اللون)
4. اضغط على الزر - سيتعطل التطبيق
5. أعد تشغيل التطبيق
6. انتظر 5-10 دقائق
7. تحقق من Firebase Console → Crashlytics

### الخطوة 3: اختبار الإبلاغ عن المشاكل
1. في تبويب "الإعدادات"، اضغط على "إبلاغ عن مشكلة"
2. اكتب وصفاً للمشكلة
3. أرسل التقرير
4. تحقق من Firebase Console → Crashlytics → Custom Events

## 📋 التحقق من الإعدادات:

### التأكد من build.gradle.kts:
```kotlin
plugins {
    alias(libs.plugins.google.services)      // ✅ 4.4.4
    alias(libs.plugins.crashlytics)         // ✅ 3.0.7
}

dependencies {
    implementation(platform(libs.firebase.bom))  // ✅ 34.12.0
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
}
```

### التأكد من libs.versions.toml:
```toml
[versions]
googleServices = "4.4.4"    // ✅
crashlytics = "3.0.7"       // ✅
firebaseBom = "34.12.0"     // ✅
```

## 🔍 استكشاف الأخطاء:

### إذا لم تظهر تقارير الأعطال:
1. تأكد من وجود `google-services.json`
2. تحقق من اتصال الإنترنت
3. تأكد من أن التطبيق يعمل على جهاز حقيقي
4. في Logcat، ابحث عن رسائل Firebase
5. انتظر 10-15 دقيقة

### إذا لم يعمل التطبيق:
1. نظّف المشروع: `./gradlew clean`
2. أعد البناء: `./gradlew build`
3. تأكد من جميع الـ imports

## 📱 ملاحظات هامة:
- زر الاختبار يظهر فقط في وضع DEBUG
- في الإصدار النهائي، يتم إرسال التقارير تلقائياً
- جميع البيانات مشفرة وآمنة
- يمكن تعطيل Crashlytics في الإعدادات إذا لزم الأمر

## 🎯 النتائج المتوقعة:
- تقرير العطل يظهر في Firebase Console خلال 5-10 دقائق
- معلومات الجهاز الكاملة مع كل تقرير
- إمكانية تتبع الأخطاء وإصلاحها بسهولة
