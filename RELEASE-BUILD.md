# بناء نسخة Release موقعة

## الخطوة 1: إنشاء ملف Keystore (للمرة الأولى فقط)

### الطريقة 1: عبر Android Studio (أسهل)
1. افتح Android Studio
2. اذهب إلى: `Build` → `Generate Signed Bundle / APK...`
3. اختر `APK` ثم `Next`
4. انقر على `Create new...` تحت Key store path
5. املأ البيانات التالية:
   - **Key store path**: اختر مكان حفظ الملف (مثلاً: `C:\Users\super\wameed-keystore.jks`)
   - **Password**: أدخل كلمة مرور قوية
   - **Key alias**: اسم مفتاح (مثلاً: `wameed`)
   - **Key password**: نفس كلمة المرور السابقة أو جديدة
   - **Validity**: 25 سنة
   - **Certificate**: أدخل اسمك والمعلومات المطلوبة
6. انقر `OK` ثم `Next` → `Finish`

### الطريقة 2: عبر سطر الأوامر
```bash
keytool -genkey -v -keystore wameed-keystore.jks -keyalg RSA -keysize 2048 -validity 9125 -alias wameed
```

## الخطوة 2: إعداد ملف keystore.properties

1. انسخ الملف القالب:
   ```bash
   copy keystore.properties.template keystore.properties
   ```

2. عدّل ملف `keystore.properties` وأدخل بياناتك:
   ```properties
   storeFile=C:\Users\super\wameed-keystore.jks
   storePassword=your_keystore_password
   keyAlias=wameed
   keyPassword=your_key_password
   ```

## الخطوة 3: بناء نسخة Release

### عبر Android Studio:
1. `Build` → `Generate Signed Bundle / APK...`
2. اختر `APK` وحدد ملف Keystore
3. اختر `release` من Build variants
4. انقر `Finish`

### عبر سطر الأوامر:
```bash
./gradlew assembleRelease
```

سيتم إنشاء الـ APK في:
```
app/build/outputs/apk/release/app-release.apk
```

## اختبار Firebase Crashlytics

1. ثبّت الـ APK على جهاز حقيقي
2. افتح التطبيق واضغط على "اختبار العطل"
3. سيغلق التطبيق فوراً
4. انتظر من 2-10 دقائق
5. ستظهر الكراش في [Firebase Console](https://console.firebase.google.com)

**ملاحظة:** في نسخة Release، Crashlytics مفعّل تلقائياً ويرسل الكراشات فوراً.

## تحديث التطبيق (Incremental Updates)

عند تحديث التطبيق:
1. غيّر `versionCode` و `versionName` في `build.gradle.kts`
2. استخدم نفس Keystore
3. عدّل `keystore.properties` إذا تغيّر المسار
4. ابنِ نسخة جديدة

**مهم:** احتفظ بملف Keystore في مكان آمن - بدونه لا يمكنك تحديث التطبيق على Google Play!
