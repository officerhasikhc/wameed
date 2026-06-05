# خريطة واجهات وتصاميم مشروع وميض

هذا الملف مرجع سريع لمعرفة أين توجد كل شاشة أو ميزة في المشروع. الهدف منه أن تستطيع لاحقاً أن تقول: "عدّل قسم كذا في الملف كذا" بدون الحاجة للبحث من جديد.

> ملاحظة: أرقام الأسطر تقريبية وقت كتابة هذا الملف، وقد تتغير بعد أي تعديل. اعتمد على اسم الملف واسم الدالة أكثر من رقم السطر.

## الصورة العامة

المشروع يحتوي على جزأين رئيسيين:

| الجزء | المسار | ماذا يحتوي؟ |
|---|---|---|
| تطبيق أندرويد | `app/src/main` | واجهات الهاتف، الإرسال، الاستقبال، الإعدادات، التشخيص، التحديثات، الخدمات |
| برنامج ويندوز المستقبل | `windows-receiver/src/receiver.py` | واجهة الكمبيوتر، تبويباته، إرسال ملف/نص للهاتف، استقبال الملفات من الهاتف، التحديثات، التشخيص |

الواجهات في أندرويد ليست مبنية بملفات `res/layout/*.xml`. أغلبها مكتوب بـ Jetpack Compose داخل ملفات Kotlin. الاستثناء المهم هو `ReceiveActivity.kt`؛ هذه شاشة منبثقة مبنية برمجياً بـ `LinearLayout` و`TextView` و`Button`.

## مداخل تطبيق أندرويد

| الملف | الدور |
|---|---|
| `app/src/main/AndroidManifest.xml` | يحدد الشاشات والخدمات والصلاحيات والثيمات. هنا تعرف أي Activity هي شاشة البداية، الرئيسية، المشاركة، الاستلام، البلاغات. |
| `app/src/main/java/com/wameed/SplashActivity.kt` | شاشة البداية المتحركة. |
| `app/src/main/java/com/wameed/MainActivity.kt` | الشاشة الرئيسية وتبويبات التطبيق. هذا أكبر ملف واجهات في أندرويد. |
| `app/src/main/java/com/wameed/ShareActivity.kt` | شاشة المشاركة التي تظهر عندما تشارك ملفاً/نصاً من تطبيق آخر إلى وميض. |
| `app/src/main/java/com/wameed/ReceiveActivity.kt` | نافذة الاستلام من الكمبيوتر إلى الهاتف، ونافذة الموافقة على الاقتران. |
| `app/src/main/java/com/wameed/WameedBugReportActivity.kt` | شاشة إرسال بلاغ أو وصف مشكلة. |
| `app/src/main/java/com/wameed/WameedConnectionService.kt` | خدمة الخلفية التي تحافظ على الاتصال وتشغل استقبال الهاتف. ليست واجهة كاملة، لكنها تتحكم بإشعارات وحالات تظهر في الواجهة. |

## الشاشة الرئيسية في أندرويد

الملف المركزي: `app/src/main/java/com/wameed/MainActivity.kt`

| الدالة/القسم | يبدأ تقريباً | ماذا تعدل هنا؟ |
|---|---:|---|
| `MainActivity.onCreate` | 136 | تهيئة المرسل، البحث، التحديثات، السجل، خدمة الاستقبال، ثم `setContent`. |
| `MainScreen` | 178 | الحاوية الكبرى للتطبيق: التبويبات، الحالة العامة، اختيار الملفات، البحث عن الأجهزة، حوارات IP، التحديثات، شاشة البلاغ بعد crash. |
| خريطة التبويبات داخل `MainScreen` | 627 | تربط رقم التبويب بالشاشة: اتصال، سجل، مستلمات، إعدادات، أجهزة موثوقة، سجل تشخيص، تشخيص شبكة. |
| `ConnectionTab` | 761 | تبويب الاتصال: حالة الاتصال، اختيار الملفات، إرسال الكل، بحث الأجهزة، اتصال يدوي، زر التشخيص. |
| `HistoryTab` | 930 | تبويب سجل النقل. |
| `HistoryItem` | 976 | شكل عنصر واحد داخل سجل النقل. |
| `ReceivedTab` | 1014 | تبويب الملفات المستلمة على الهاتف. |
| `ReceivedFileItem` | 1074 | شكل عنصر ملف مستلم مع أزرار الفتح والمشاركة. |
| `SettingsTab` | 1289 | تبويب الإعدادات: اللغة، وضع العرض على الكمبيوتر، إبقاء الاتصال، الأجهزة الموثوقة، التشخيص، التحديثات، عن التطبيق. |
| `TrustedDevicesTab` | 1627 | شاشة الأجهزة الموثوقة وحذف جهاز موثوق. |
| `StatusCard` | 1680 | بطاقة حالة الاتصال في تبويب الاتصال. |
| `BatchProgressOverlay` | 1779 | نافذة/طبقة تقدم الإرسال أو الاستقبال الجماعي. |
| `DeviceItem` | 1883 | شكل جهاز مكتشف في قائمة الأجهزة. |
| `QuickAction` | 1949 | أزرار الإجراءات السريعة مثل إرسال وتحديث. |

إذا أردت تعديل شيء في الصفحة الرئيسية غالباً ستبدأ من `MainActivity.kt`. أما النصوص التي تظهر داخلها فغالباً في `strings.xml` وليس مكتوبة كلها مباشرة في Kotlin.

## الشاشات المنفصلة في أندرويد

| الشاشة/الميزة | الملف | ماذا تعدل هنا؟ |
|---|---|---|
| شاشة البداية | `app/src/main/java/com/wameed/SplashActivity.kt` | الحركة، الخلفية المتدرجة، مدة الانتظار، النص الظاهر في البداية. |
| ثيم شاشة البداية | `app/src/main/res/values/themes.xml` | لون خلفية splash وثيم النافذة. |
| Bottom Sheet المشاركة | `app/src/main/java/com/wameed/ShareActivity.kt` | شكل نافذة المشاركة، حالات الإرسال، التقدم، النجاح، الخطأ، زر الإعدادات. |
| استخراج محتوى المشاركة | `ShareActivity.extractShareData` | استقبال نص، ملف واحد، أو عدة ملفات من Android share intent. |
| شاشة الاستلام المنبثقة | `app/src/main/java/com/wameed/ReceiveActivity.kt` | كرت الاستلام، الموافقة/الرفض، تقدم الاستقبال، النصوص والروابط المستلمة، العد التنازلي. |
| شاشة البلاغات | `app/src/main/java/com/wameed/WameedBugReportActivity.kt` | واجهة وصف المشكلة، البريد، معلومات الجهاز، زر الإرسال. |
| سجل التشخيص | `app/src/main/java/com/wameed/DiagnosticsScreen.kt` | شاشة عرض السجل، الفلاتر، مشاركة السجل، مسح السجل. |
| تشخيص الشبكة | `app/src/main/java/com/wameed/DiagnosticsScreen.kt` | فحوصات UDP وTCP وPing وWebSocket، النتائج، زر مشاركة النتائج. |
| حوار التحديث | `app/src/main/java/com/wameed/WameedUpdateDialog.kt` | شكل نافذة التحديث وشريط التقدم وأزرار "لاحقاً" و"تحديث الآن". |
| إشعار تحديث صغير | `app/src/main/java/com/wameed/WameedUpdateDialog.kt` | البطاقة الصغيرة التي تظهر عند توفر تحديث. |
| ربط التحديث بالواجهة | `app/src/main/java/com/wameed/WameedUpdateIntegration.kt` | متى يتم البحث عن تحديث ومتى يظهر الحوار أو الإشعار. |

## التصميم العام في أندرويد

| الملف | ماذا يحتوي؟ |
|---|---|
| `app/src/main/java/com/wameed/ui/theme/Color.kt` | ألوان Compose العامة مثل الأخضر الأساسي. |
| `app/src/main/java/com/wameed/ui/theme/Theme.kt` | `WameedTheme` ونظام الألوان الفاتح/الداكن. |
| `app/src/main/java/com/wameed/ui/theme/Type.kt` | خط Cairo وTypography المستخدم في Compose. |
| `app/src/main/res/values/colors.xml` | ألوان Android التقليدية المستخدمة في الثيمات وSplash وبعض الشاشات غير Compose. |
| `app/src/main/res/values/themes.xml` | ثيمات Android: Splash، نافذة شفافة للاستلام، Bottom Sheet شفاف للمشاركة. |
| `app/src/main/res/values/strings.xml` | النصوص العربية. |
| `app/src/main/res/values-en/strings.xml` | النصوص الإنجليزية. |
| `app/src/main/res/drawable/wameed_logo.png` | شعار وميض داخل موارد التطبيق. |
| `app/src/main/res/mipmap-*` | أيقونات التطبيق بأحجام مختلفة. |
| `app/src/main/res/font/cairo.xml` و`preloaded_fonts.xml` | إعدادات تحميل خط Cairo. |
| `app/src/main/res/xml/file_paths.xml` | مسارات FileProvider لفتح أو تثبيت ملفات من داخل التطبيق. |

قاعدة عملية: إذا أردت تغيير لون عام في Compose فابدأ من `ui/theme`. إذا أردت تغيير لون نافذة نظام أو Splash أو شاشة غير Compose فابدأ من `res/values/colors.xml` و`themes.xml`.

## منطق أندرويد المرتبط بالواجهات

هذه الملفات ليست تصميماً مباشراً، لكنها تغذي الشاشات بالبيانات والحالات:

| الملف | الدور |
|---|---|
| `app/src/main/java/com/wameed/WameedSender.kt` | إرسال من الهاتف إلى الكمبيوتر عبر WebSocket: ping، نص، ملف، عدة ملفات. |
| `app/src/main/java/com/wameed/WameedServer.kt` | استقبال على الهاتف من الكمبيوتر، طلب الاقتران، استقبال ملفات/نصوص/روابط. |
| `app/src/main/java/com/wameed/WameedConnectionService.kt` | خدمة الخلفية: تشغيل السيرفر، إبقاء الاتصال، إشعارات Android، بث الأحداث للواجهات. |
| `app/src/main/java/com/wameed/DeviceDiscovery.kt` | البحث عن الكمبيوتر عبر UDP. |
| `app/src/main/java/com/wameed/WameedDiscoveryResponder.kt` | ردود الاكتشاف عندما يحتاج الجهاز للإعلان عن نفسه. |
| `app/src/main/java/com/wameed/WameedPrefs.kt` | الإعدادات: IP الكمبيوتر، اللغة، الأجهزة الموثوقة، سجل النقل، وضع العرض. |
| `app/src/main/java/com/wameed/WameedEvents.kt` | قناة أحداث داخل التطبيق، مثل تقدم الاستلام أو اكتماله. |
| `app/src/main/java/com/wameed/WameedLogger.kt` | سجل التشخيص الذي تعرضه `DiagnosticsScreen.kt`. |
| `app/src/main/java/com/wameed/FileSaver.kt` | حفظ الملفات المستلمة في Downloads/Wameed. |
| `app/src/main/java/com/wameed/LocaleHelper.kt` | تبديل اللغة العربية/الإنجليزية. |
| `app/src/main/java/com/wameed/WameedCrashReporter.kt` | Crashlytics وبلاغات الأعطال. |
| `app/src/main/java/com/wameed/WameedUpdateManager.kt` | فحص وتنزيل وتثبيت تحديثات أندرويد. |
| `app/src/main/java/com/wameed/WameedBroadcastReceiver.kt` | استقبال أحداث النظام مثل التشغيل وفتح القفل. |

## واجهة برنامج ويندوز

الملف المركزي لبرنامج الكمبيوتر هو:

`windows-receiver/src/receiver.py`

هذا الملف كبير جداً ويجمع الواجهة والمنطق في مكان واحد. أهم أقسام الواجهة فيه:

| الدالة/القسم | يبدأ تقريباً | ماذا تعدل هنا؟ |
|---|---:|---|
| `translations` | 83 | النصوص العربية والإنجليزية لبرنامج ويندوز. |
| `WameedApp.__init__` | 628 | إنشاء نافذة Tkinter، الحجم، الأيقونة، Tray. |
| `setup_ui` | 650 | الهيكل العام: الهيدر الأخضر، التبويبات، الشريط السفلي. |
| `_build_home` | 716 | تبويب الرئيسية: حالة الاتصال، أزرار إرسال/بحث/اتصال يدوي/فتح مجلد، آخر الملفات. |
| `_refresh_recent` | 804 | شكل قائمة آخر الملفات المستلمة في الرئيسية. |
| `_update_status_display` | 856 | نص وألوان حالة الاتصال في الرئيسية. |
| `_show_manual_ip_dialog` | 1037 | نافذة إدخال IP الهاتف يدوياً. |
| `_show_discovery_dialog` | 1079 | نافذة البحث عن أجهزة واختيار هاتف. |
| `_build_devices` | 1357 | تبويب الأجهزة. |
| `_build_history` | 1531 | تبويب السجل. |
| `_build_settings` | 1630 | تبويب الإعدادات: اللغة، مجلد الحفظ، التحديثات، الأجهزة الموثوقة، التشخيص، Firewall. |
| `_show_update_dialog` | 1851 | نافذة تحديث برنامج ويندوز. |
| `_show_log_viewer` | 2202 | نافذة سجل التشخيص في ويندوز. |
| `_show_network_diagnostics` | 2264 | نافذة تشخيص الشبكة في ويندوز. |
| `_show_send_dialog` | 2517 | نافذة إرسال ملف أو نص من الكمبيوتر إلى الهاتف. |
| `_execute_multi_send` | 2853 | منطق إرسال عدة ملفات من الكمبيوتر إلى الهاتف وتحديث شريط التقدم. |
| `_execute_send_text` | 3031 | منطق إرسال نص من الكمبيوتر إلى الهاتف. |
| `setup_tray` | 3096 | قائمة أيقونة النظام Tray. |
| `show_pairing_dialog` | 3257 | نافذة الموافقة على اقتران هاتف مع الكمبيوتر. |

ملفات مرتبطة ببرنامج ويندوز:

| الملف | الدور |
|---|---|
| `windows-receiver/src/wameed_version.py` | نسخة برنامج ويندوز وروابط التحديث وبيانات الإصدار. |
| `windows-receiver/src/wameed.ico` | أيقونة البرنامج. |
| `windows-receiver/src/requirements.txt` | مكتبات Python المطلوبة. |
| `windows-receiver/installer/wameed.iss` | إعداد مثبت Inno Setup. |
| `windows-receiver/scripts/build.bat` | بناء نسخة ويندوز. |

## ملفات البناء والإصدار

| الملف | الدور |
|---|---|
| `settings.gradle.kts` | تعريف المشروع والموديولات. |
| `build.gradle.kts` | إعدادات Gradle العامة. |
| `app/build.gradle.kts` | إعدادات تطبيق أندرويد: Compose، Firebase، Ktor، التوقيع، الاعتمادات. |
| `gradle/libs.versions.toml` | نسخ المكتبات وplugins. |
| `version.properties` | رقم واسم إصدار أندرويد. |
| `update.json` | بيانات التحديث المنشورة للتطبيق وبرنامج ويندوز. |
| `package-release.ps1` | تجهيز حزمة الإصدار. |
| `scripts/sync-version.ps1` | مزامنة رقم الإصدار. |
| `scripts/verify-version.ps1` | التحقق من رقم الإصدار. |
| `RELEASE-BUILD.md` | تعليمات بناء الإصدار. |
| `FIREBASE-SETUP.md` | إعداد Firebase. |
| `DEV-GUIDE.md` | دليل تطوير عام. |

## دليل سريع حسب الطلب

| إذا أردت تعديل... | ابدأ من... |
|---|---|
| ألوان التطبيق العامة | `app/src/main/java/com/wameed/ui/theme/Color.kt` ثم `Theme.kt` |
| خط التطبيق وحجم النصوص العامة | `app/src/main/java/com/wameed/ui/theme/Type.kt` |
| النصوص العربية أو الإنجليزية | `app/src/main/res/values/strings.xml` و`app/src/main/res/values-en/strings.xml` |
| تبويب الاتصال في الهاتف | `MainActivity.kt` -> `ConnectionTab` و`StatusCard` و`DeviceItem` |
| أزرار إرسال/تحديث السريعة | `MainActivity.kt` -> `QuickAction` |
| نافذة تقدم الإرسال/الاستلام | `MainActivity.kt` -> `BatchProgressOverlay` |
| تبويب السجل | `MainActivity.kt` -> `HistoryTab` و`HistoryItem` |
| تبويب المستلمات | `MainActivity.kt` -> `ReceivedTab` و`ReceivedFileItem` |
| تبويب الإعدادات | `MainActivity.kt` -> `SettingsTab` |
| الأجهزة الموثوقة | `MainActivity.kt` -> `TrustedDevicesTab` و`WameedPrefs.kt` |
| شاشة المشاركة من تطبيق آخر | `ShareActivity.kt` |
| شاشة الاستلام من الكمبيوتر | `ReceiveActivity.kt` |
| شاشة البداية | `SplashActivity.kt` و`res/values/themes.xml` |
| شاشة البلاغات | `WameedBugReportActivity.kt` |
| سجل التشخيص في الهاتف | `DiagnosticsScreen.kt` -> `DiagLogScreen` |
| تشخيص الشبكة في الهاتف | `DiagnosticsScreen.kt` -> `NetworkDiagScreen` |
| حوار تحديث أندرويد | `WameedUpdateDialog.kt` و`WameedUpdateIntegration.kt` و`WameedUpdateManager.kt` |
| واجهة برنامج ويندوز الرئيسية | `windows-receiver/src/receiver.py` -> `_build_home` |
| نافذة إرسال من ويندوز إلى الهاتف | `windows-receiver/src/receiver.py` -> `_show_send_dialog` |
| تبويبات ويندوز | `windows-receiver/src/receiver.py` -> `_build_home`, `_build_devices`, `_build_history`, `_build_settings` |
| نصوص ويندوز | `windows-receiver/src/receiver.py` -> `translations` |
| تحديثات ويندوز | `windows-receiver/src/receiver.py` -> `_show_update_dialog` و`_download_windows_update` |
| مثبت ويندوز | `windows-receiver/installer/wameed.iss` |

## ملاحظات مهمة

- مجلدات `build`, `.gradle`, `dist`, `release`, و`windows-receiver/dist` نواتج بناء وليست مكاناً لتعديل الواجهات.
- يوجد ملف قديم داخل `تعليمات/ShareActivity.kt` يبدو كمرجع أو نسخة تعليمات، لكنه ليس الملف النشط للتطبيق. الملف النشط هو `app/src/main/java/com/wameed/ShareActivity.kt`.
- لا يوجد حالياً مجلد `app/src/main/res/layout` للواجهات التقليدية، لذلك البحث عن "ملف XML للشاشة" لن يفيد غالباً.
- عند طلب تعديل واجهة، أفضل صيغة تكون: "عدّل شاشة كذا في `اسم_الملف`، الدالة `اسم_الدالة`، وأريد كذا".
