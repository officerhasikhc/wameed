content:
# 🌿 وميض — تحديث الواجهة (Design System موحّد)

## 📦 محتويات هذه الحزمة

```
wameed_redesign/
├── README.md                         ← هذا الملف
│
├── android/
│   ├── theme/
│   │   ├── Color.kt                  ← لوحة ألوان وميض الأخضر الموحّدة
│   │   ├── Theme.kt                  ← Material You 3 light/dark
│   │   ├── Shape.kt                  ← زوايا 8/12/20/24/32dp
│   │   └── Type.kt                   ← خط Cairo بكامل أوزانه
│   ├── WameedComponents.kt           ← مكونات مشتركة (Card, Button, Dot, Progress)
│   ├── MainActivity_NewSections.kt   ← Composables المحدّثة
│   └── strings_additions.xml         ← نصوص جديدة للترجمة
│
└── desktop/
    └── receiver_modern.py            ← receiver.py بـ CustomTkinter كامل
```

---

## 🎨 الهوية البصرية الموحّدة

| الاستخدام | اللون | الكود |
|---|---|---|
| Primary (أخضر وميض) | 🟢 | `#2E7D32` |
| Primary Light | 🟢 | `#43A047` |
| Container | 🍃 | `#E8F5E9` |
| Background (نعناعي) | 🌿 | `#F1F8F4` |
| Surface (بطاقات) | ⚪ | `#FFFFFF` |
| Text Primary | ⚫ | `#1E293B` |
| Text Secondary | 🌫️ | `#64748B` |

**الفلسفة:**
- 🪶 **بساطة قصوى** — مساحات بيضاء سخية
- 🚫 **بدون أيقونات إلا الضروري** (النقطة الخضراء + سهم الإرسال فقط)
- 📝 **نصوص واضحة** بدلاً من الأيقونات الزخرفية
- 🟢 **لون أخضر واحد** هو البطل عبر التطبيقين

---

## 📱 تطبيق الأندرويد — خطوات الدمج

### 1) ضع ملفات الـ theme:
انسخ ملفات مجلد `android/theme/` إلى:
```
app/src/main/java/com/wameed/ui/theme/
```
(استبدل `Color.kt`, `Theme.kt`, `Type.kt` القديمة، وأضف `Shape.kt` جديداً)

### 2) أضف مكتبة المكونات:
انسخ `WameedComponents.kt` إلى:
```
app/src/main/java/com/wameed/ui/components/WameedComponents.kt
```

### 3) حدّث `MainActivity.kt`:
استبدل الـ Composables التالية في ملفك الأصلي بنظيراتها من `MainActivity_NewSections.kt`:
- `MainScreen` (الـ Scaffold الرئيسي)
- `HomeTab`
- `StatusCard`
- `DeviceItem`
- `BatchProgressOverlay`
- `ReceivedFileItem`

**⚠️ مهم:** احتفظ بكل منطق الـ state والـ connection والـ WebSocket كما هو — فقط استبدل أجزاء الـ UI.

### 4) أضف الـ Strings الجديدة:
ادمج محتوى `strings_additions.xml` مع `res/values-ar/strings.xml`.

### 5) تأكد من تبعية Cairo Font:
في `build.gradle`:
```kotlin
implementation "androidx.compose.ui:ui-text-google-fonts:1.7.0"
```

---

## 💻 تطبيق الكمبيوتر — التشغيل

### 1) تثبيت التبعيات:
```bash
pip install customtkinter==5.2.2 Pillow tkinterdnd2 pystray websockets requests
```

### 2) تثبيت خط Cairo (اختياري لكنه موصى به):
- **Windows:** نزّل [Cairo من Google Fonts](https://fonts.google.com/specimen/Cairo) → انقر "Install"
- **macOS:** نفس الشيء عبر Font Book
- **Linux:** `sudo apt install fonts-cairo` أو نسخ ملفات `.ttf` إلى `~/.fonts/`

### 3) دمج المنطق:
الملف `receiver_modern.py` هو **هيكل الواجهة فقط**. لربطه بالـ WebSocket والـ Discovery الأصليين:

```python
# داخل receiver.py الأصلي:
from receiver_modern import WameedApp

class YourBackend:
    def __init__(self):
        self.ui = WameedApp()
        # شغّل الـ WebSocket في thread منفصل
        threading.Thread(target=self.start_server, daemon=True).start()
    
    def on_file_received(self, name, size, sender, path):
        # عند استقبال ملف من الـ WebSocket
        self.ui.on_file_received(name, size, sender, path)
    
    def on_progress(self, file_name, percent, speed_mbps, current, total):
        self.ui.show_progress(file_name, percent, speed_mbps, current, total)
    
    def on_connected(self, device_name, ip):
        self.ui._set_connection(True, device_name, ip)

# تشغيل
backend = YourBackend()
backend.ui.run()
```

### 4) تشغيل تجريبي للمعاينة:
```bash
python receiver_modern.py
```

---

## ✨ ما الذي تحسّن مقارنة بالنسخة القديمة؟

| العنصر | قبل | بعد |
|---|---|---|
| **Tkinter** | tk.Button بنمط Win95 | CTkButton عصري بزوايا دائرية |
| **البطاقات** | إطارات مسطّحة بلا ظلال | بطاقات بظل ناعم وزوايا 18px |
| **النقطة الخضراء** | ثابتة | نابضة بحركة Pulse |
| **شريط التقدم** | LinearProgressIndicator عادي | متحرك مع shimmer وألوان موحّدة |
| **التبويبات** | ttk.Notebook (مظهر Windows) | أزرار نصية بـ Pills نشطة |
| **الأيقونات** | كثيرة ومبعثرة | محذوفة (الفلسفة الجديدة) |
| **الألوان** | متعددة (أزرق، أحمر، برتقالي، أخضر) | أخضر واحد + لمسات حالة |
| **المسافات** | مزدحمة | سخية ومريحة |
| **التناسق** | Android ≠ Desktop | هوية واحدة عبر التطبيقين |

---

## 🎬 الحركات المضافة

| الحركة | الموضع |
|---|---|
| Pulse Animation | النقطة الخضراء (Status Dot) |
| Scale Press | الأزرار والبطاقات عند الضغط |
| Fade Transition | تبديل التبويبات (220ms) |
| Slide-up | ظهور شريط التقدم من الأسفل |
| Progress Tween | شريط التقدم بحركة سلسة |

---

## ⚠️ ملاحظات مهمة قبل الاستخدام

1. **لم أُغيّر منطق الـ Backend** — كل الـ WebSocket / Discovery / Pairing يبقى كما هو.
2. **بعض الـ Composables الفرعية** في `MainActivity.kt` (مثل `SettingsTab` التفصيلي، `HistoryItem` التفصيلي) لم تُعَد كتابتها بالكامل — أبقيت قوالبها المبسّطة. أعطني إشارة وسأكملها بنفس النمط.
3. **`ReceiveActivity.kt` و `ShareActivity.kt`** لم أحدّثهما هذه الجولة — أخبرني إذا أردت تحديثهما بنفس الهوية.
4. **خط Cairo في Tkinter** سيعمل تلقائياً إذا كان مثبّتاً في النظام — وإلا سيرجع إلى Segoe UI تلقائياً.

---

## 🚀 الخطوة التالية الموصى بها

1. شغّل `receiver_modern.py` لتعاينه بصرياً
2. ادمج Theme الأندرويد وابنِ التطبيق للمعاينة
3. أعطني ملاحظاتك → سأحسّن أي تفصيل
4. عندما تكون راضياً، سأكمل باقي الشاشات (ReceiveActivity, ShareActivity, Settings التفصيلي)

**صنع بحب لمشروع وميض 🌿**