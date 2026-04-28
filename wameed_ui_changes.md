# Wameed — تعديلات الواجهة

> انسخ كل قسم واستبدله بالكود الموجود في `receiver.py`.  
> كل قسم يحتوي على **السطر المرجعي** ليسهل الإيجاد.

---

## التعديل ١ — الخطوط والألوان العامة (دالة `_build_ui`)

**ابحث عن:** `def _build_ui(self):` — السطر ~998  
**استبدل كامل دالة `_build_ui`** بالكود التالي:

```python
def _build_ui(self):
    FONT_TITLE  = ("Segoe UI", 13, "bold")
    FONT_BODY   = ("Segoe UI", 10)
    FONT_SMALL  = ("Segoe UI", 9)
    FONT_LABEL  = ("Segoe UI", 10)

    # ── Header ──────────────────────────────────────────────
    hdr = tk.Frame(self.root, bg="#2E7D32", height=56)
    hdr.pack(fill="x"); hdr.pack_propagate(False)
    inner = tk.Frame(hdr, bg="#2E7D32"); inner.pack(expand=True)
    tk.Label(inner, text=t("app_header"), bg="#2E7D32", fg="white",
             font=("Segoe UI", 17, "bold")).pack(side="left", padx=6)
    tk.Label(inner, text=f"v{APP_VERSION}", bg="#2E7D32", fg="#A5D6A7",
             font=("Segoe UI", 9)).pack(side="left")

    # ── Bottom action bar (pack BEFORE notebook) ─────────────
    bf = tk.Frame(self.root, bg="#F1F5F9",
                  highlightthickness=1, highlightbackground="#E2E8F0")
    bf.pack(side="bottom", fill="x")

    # اليمين: زران رئيسيان
    btn_primary = dict(bd=0, padx=18, pady=9,
                       font=("Segoe UI", 10, "bold"), cursor="hand2")
    btn_secondary = dict(bd=0, padx=14, pady=9,
                         font=("Segoe UI", 10), cursor="hand2")

    tk.Button(bf, text=t("btn_open_folder"), command=self._open_folder,
              bg="#2E7D32", fg="white",
              activebackground="#1B5E20", activeforeground="white",
              **btn_primary).pack(side="right", padx=(6, 10), pady=7)

    tk.Button(bf, text="تصغير", command=self._minimize_to_tray,
              bg="#E2E8F0", fg="#374151",
              activebackground="#CBD5E1",
              **btn_secondary).pack(side="right", padx=3, pady=7)

    # اليسار: زران ثانويان (أيقونة + نص خافت)
    btn_icon = dict(bd=0, padx=10, pady=7,
                    font=("Segoe UI", 10), cursor="hand2")
    tk.Button(bf, text="تشخيص", command=self._show_diagnostics,
              bg="#F1F5F9", fg="#94A3B8",
              activebackground="#E2E8F0",
              **btn_icon).pack(side="left", padx=(10, 3), pady=7)
    tk.Button(bf, text="إنهاء", command=self._confirm_quit,
              bg="#F1F5F9", fg="#FCA5A5",
              activebackground="#FEE2E2",
              **btn_icon).pack(side="left", padx=3, pady=7)

    # ── Notebook ──────────────────────────────────────────────
    style = ttk.Style()
    style.configure("TNotebook", background="#F8FAFC")
    style.configure("TNotebook.Tab",
                    font=("Segoe UI", 10), padding=[16, 5])

    self.nb = ttk.Notebook(self.root)
    self.nb.pack(fill="both", expand=True, padx=12, pady=(8, 4))

    home = tk.Frame(self.nb, bg="white")
    self.nb.add(home, text=t("tab_home"))
    self._build_home(home)

    ht = tk.Frame(self.nb, bg="white")
    self.nb.add(ht, text=t("tab_history"))
    self._build_history(ht)

    st = tk.Frame(self.nb, bg="white")
    self.nb.add(st, text=t("tab_settings"))
    self._build_settings(st)
```

---

## التعديل ٢ — الرئيسية (دالة `_build_home`)

**ابحث عن:** `def _build_home(self, parent):` — السطر ~1046  
**استبدل كامل دالة `_build_home`** بالكود التالي:

```python
def _build_home(self, parent):
    """Home tab: status card → recent files → send button."""

    # ── بطاقة الحالة ───────────────────────────────────────
    card_wrap = tk.Frame(parent, bg="white")
    card_wrap.pack(fill="x", padx=14, pady=(14, 8))

    self.status_frame = tk.Frame(card_wrap, bg="#F8FAFC",
                                 highlightthickness=1,
                                 highlightbackground="#E2E8F0")
    self.status_frame.pack(fill="x")

    # شريط لوني على اليمين يعبر عن الحالة
    self.status_accent = tk.Frame(self.status_frame, bg="#94A3B8", width=4)
    self.status_accent.pack(side="right", fill="y")

    inner = tk.Frame(self.status_frame, bg="#F8FAFC")
    inner.pack(side="right", fill="both", expand=True, padx=16, pady=14)

    row = tk.Frame(inner, bg="#F8FAFC"); row.pack(fill="x")
    self.status_dot = tk.Label(row, text="●", fg="#94A3B8", bg="#F8FAFC",
                               font=("Segoe UI", 18))
    self.status_dot.pack(side="left", padx=(0, 10))

    title_col = tk.Frame(row, bg="#F8FAFC")
    title_col.pack(side="left", fill="x", expand=True)

    self.status_label = tk.Label(
        title_col, text=t("status_starting"), bg="#F8FAFC",
        font=("Segoe UI", 13, "bold"), anchor="w", fg="#1E293B")
    self.status_label.pack(fill="x")

    self.status_sub_label = tk.Label(
        title_col, text="", bg="#F8FAFC",
        font=("Segoe UI", 10), fg="#64748B", anchor="w")
    self.status_sub_label.pack(fill="x")

    # آخر استلام — سطر منفصل خافت
    info = tk.Frame(inner, bg="#F8FAFC"); info.pack(fill="x", pady=(8, 0))
    self.ip_label = tk.Label(info, text="", bg="#F8FAFC",
                             font=("Segoe UI", 9), fg="#94A3B8", anchor="w")
    self.last_recv_label = tk.Label(info, text="", bg="#F8FAFC",
                                    font=("Segoe UI", 9), fg="#16A34A", anchor="w")
    self.last_recv_label.pack(fill="x")

    self._install_status_tooltip()

    # ── آخر الملفات ────────────────────────────────────────
    rec_head = tk.Frame(parent, bg="white")
    rec_head.pack(fill="x", padx=16, pady=(6, 4))
    tk.Label(rec_head, text=t("recent_files"), bg="white",
             font=("Segoe UI", 10, "bold"), fg="#1E293B", anchor="w").pack(side="right")
    view_all = tk.Label(rec_head, text=t("view_all"), bg="white",
                        font=("Segoe UI", 9), fg="#2E7D32",
                        cursor="hand2", anchor="e")
    view_all.pack(side="left")
    view_all.bind("<Button-1>", lambda _e: self.nb.select(1))

    rec_frame = tk.Frame(parent, bg="white",
                         highlightthickness=1, highlightbackground="#E2E8F0")
    rec_frame.pack(fill="both", expand=True, padx=14, pady=(0, 6))
    self.mini_list = tk.Frame(rec_frame, bg="white")
    self.mini_list.pack(fill="both", expand=True)

    # ── زر الإرسال (أسفل القائمة) ─────────────────────────
    send_frame = tk.Frame(parent, bg="white")
    send_frame.pack(fill="x", padx=14, pady=(4, 10))

    self.btn_send = tk.Button(
        send_frame, text=t("btn_send"),
        command=self._show_unified_send_dialog,
        bg="#2E7D32", fg="white",
        activebackground="#1B5E20", activeforeground="white",
        bd=0, pady=11, font=("Segoe UI", 11, "bold"), cursor="hand2")
    self.btn_send.pack(fill="x")

    # إحصائية اليوم
    self.stats_label = tk.Label(parent, text="", bg="white",
                                font=("Segoe UI", 8), fg="#94A3B8", anchor="e")
    self.stats_label.pack(fill="x", padx=16, pady=(0, 2))

    self._refresh_mini_list()
```

---

## التعديل ٣ — صفوف الملفات في الرئيسية (دالة `_build_recent_row`)

**ابحث عن:** `def _build_recent_row(self, parent, entry, idx):` — السطر ~1501  
**استبدل كامل دالة `_build_recent_row`** بالكود التالي:

```python
def _build_recent_row(self, parent, entry, idx):
    """صف ملف واحد في قائمة الرئيسية — تصميم محسّن بدون أيقونات زائدة."""
    BG   = "#FAFAFA" if idx % 2 else "white"
    HOVR = "#F0FDF4"

    row = tk.Frame(parent, bg=BG, cursor="hand2")
    row.pack(fill="x")

    # فاصل خفيف بين الصفوف
    sep = tk.Frame(parent, bg="#F1F5F9", height=1)
    sep.pack(fill="x")

    ok       = entry.get("status") == "success"
    dot_clr  = "#22C55E" if ok else "#F87171"
    tm       = (entry.get("time", "") or "")[11:16]
    name     = self._smart_truncate(entry.get("filename", "?"), 36)
    sz       = self._fmt_size(entry.get("size", 0) or 0)
    path     = entry.get("path", "") or ""
    has_file = bool(path) and os.path.exists(path)

    # نقطة الحالة — يمين
    dot = tk.Label(row, text="●", bg=BG, fg=dot_clr,
                   font=("Segoe UI", 9))
    dot.pack(side="right", padx=(10, 8), pady=6)

    # اسم الملف
    name_lbl = tk.Label(row, text=name, bg=BG,
                        fg="#1E293B" if has_file else "#94A3B8",
                        font=("Segoe UI", 10), anchor="e")
    name_lbl.pack(side="right", fill="x", expand=True, padx=(0, 4), pady=6)

    # الحجم والوقت — يسار
    meta_lbl = tk.Label(row, text=f"{sz}  ·  {tm}", bg=BG,
                        fg="#94A3B8", font=("Segoe UI", 9), anchor="w")
    meta_lbl.pack(side="left", padx=(10, 0), pady=6)

    # زر القائمة السياقية — يسار بعد الميتا
    more_btn = tk.Label(row, text="···", bg=BG, fg="#CBD5E1",
                        font=("Segoe UI", 11, "bold"),
                        padx=8, cursor="hand2")
    more_btn.pack(side="left")

    # ── التفاعل ────────────────────────────────────────────
    def on_click(_e=None):
        if not path:
            self._toast(t("app_header"), t("old_entry")); return
        if not os.path.exists(path):
            self._toast(t("app_header"), t("file_gone", path=path)); return
        self.root.after(0, lambda: self._open_file_safe(path))

    def on_more(_e=None):
        self._show_row_menu(entry, _e)

    def on_enter(_e=None):
        for w in (row, name_lbl, dot, meta_lbl, more_btn, sep):
            try: w.config(bg=HOVR)
            except Exception: pass

    def on_leave(_e=None):
        for w in (row, name_lbl, dot, meta_lbl, more_btn):
            try: w.config(bg=BG)
            except Exception: pass
        try: sep.config(bg="#F1F5F9")
        except Exception: pass

    for w in (row, name_lbl, dot, meta_lbl):
        w.bind("<Button-1>", on_click)
        w.bind("<Enter>", on_enter)
        w.bind("<Leave>", on_leave)
    more_btn.bind("<Button-1>", on_more)
    more_btn.bind("<Enter>", on_enter)
    more_btn.bind("<Leave>", on_leave)
    row.bind("<Button-3>", on_more)
    name_lbl.bind("<Button-3>", on_more)
```

---

## التعديل ٤ — نافذة الإرسال (دالة `_show_unified_send_dialog`)

**ابحث عن:** `def _show_unified_send_dialog(self):` — السطر ~2246  
**استبدل من السطر الأول للدالة حتى نهاية `win.bind("<Escape>"...)`** بالكود التالي:

```python
def _show_unified_send_dialog(self):
    """نافذة الإرسال — تصميم محسّن."""
    win = tk.Toplevel(self.root)
    win.title("وميض — إرسال")
    win.configure(bg="white")
    win.transient(self.root)
    win.resizable(False, False)
    try:
        ico = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wameed.ico")
        if os.path.exists(ico): win.iconbitmap(ico)
    except Exception:
        pass
    w, h = 480, 560
    try:
        sw = self.root.winfo_screenwidth(); sh = self.root.winfo_screenheight()
        win.geometry(f"{w}x{h}+{(sw-w)//2}+{(sh-h)//2}")
    except Exception:
        win.geometry(f"{w}x{h}")

    # ── Header ───────────────────────────────────────────────
    hdr = tk.Frame(win, bg="#2E7D32", height=50)
    hdr.pack(fill="x"); hdr.pack_propagate(False)
    tk.Label(hdr, text="إرسال للهاتف", bg="#2E7D32", fg="white",
             font=("Segoe UI", 13, "bold")).pack(expand=True)

    main_body = tk.Frame(win, bg="white")
    main_body.pack(fill="both", expand=True)

    # ── اختيار الجهاز ────────────────────────────────────────
    tk.Label(main_body, text="الجهاز المستقبل", bg="white",
             font=("Segoe UI", 10, "bold"), fg="#1E293B",
             anchor="e").pack(fill="x", padx=20, pady=(16, 6))

    device_container = tk.Frame(main_body, bg="white",
                                highlightthickness=1,
                                highlightbackground="#E2E8F0")
    device_container.pack(fill="x", padx=20)

    selected_ip   = tk.StringVar(value="")
    selected_port = tk.IntVar(value=7789)

    def _build_device_list():
        for w_child in device_container.winfo_children():
            w_child.destroy()

        device_entries = []
        seen_ips = set()

        for phone in self.phone_discovery.get_phones():
            ip = phone["ip"]
            device_entries.append((phone["name"], ip, phone["port"], True))
            seen_ips.add(ip)

        for did, info in self.trusted.deduplicated_for_send():
            ip = info.get("last_ip", "")
            if ip and ip in seen_ips:
                continue
            device_entries.append(
                (info.get("name", t("device_generic")), ip, 7789, False))
            if ip: seen_ips.add(ip)

        if not device_entries:
            tk.Label(device_container,
                     text="لا توجد أجهزة متاحة.\nفعّل وضع الاستقبال على الهاتف أولاً.",
                     bg="white", fg="#94A3B8", font=("Segoe UI", 9),
                     justify="center").pack(pady=16)
            return

        for name, ip, port, is_live in device_entries:
            card = tk.Frame(device_container, bg="#FAFAFA", cursor="hand2",
                            highlightthickness=1, highlightbackground="#E2E8F0")
            card.pack(fill="x", padx=6, pady=3)

            top_row = tk.Frame(card, bg="#FAFAFA")
            top_row.pack(fill="x", padx=12, pady=(8, 2))

            # اسم الجهاز — يمين
            tk.Label(top_row, text=name, bg="#FAFAFA",
                     font=("Segoe UI", 10, "bold"), fg="#1E293B",
                     anchor="e").pack(side="right")

            # حالة الجهاز — يسار
            status_text = "متاح" if is_live else ("موثوق" if ip else "غير متاح")
            status_clr  = "#16A34A" if is_live else ("#64748B" if ip else "#94A3B8")
            tk.Label(top_row, text=status_text, bg="#FAFAFA",
                     font=("Segoe UI", 9), fg=status_clr,
                     anchor="w").pack(side="left")

            bottom_row = tk.Frame(card, bg="#FAFAFA")
            bottom_row.pack(fill="x", padx=12, pady=(0, 8))
            ip_text = ip if ip else "—"
            tk.Label(bottom_row, text=ip_text, bg="#FAFAFA",
                     font=("Segoe UI", 9), fg="#94A3B8",
                     anchor="e").pack(side="right")

            def _select(ip_=ip, port_=port, card_=card):
                selected_ip.set(ip_)
                selected_port.set(port_)
                for c in device_container.winfo_children():
                    try:
                        c.config(highlightbackground="#E2E8F0", bg="#FAFAFA")
                        for ch in c.winfo_children():
                            ch.config(bg="#FAFAFA")
                            for gch in ch.winfo_children():
                                gch.config(bg="#FAFAFA")
                    except Exception:
                        pass
                try:
                    card_.config(highlightbackground="#2E7D32", bg="#F0FDF4")
                    for ch in card_.winfo_children():
                        ch.config(bg="#F0FDF4")
                        for gch in ch.winfo_children():
                            gch.config(bg="#F0FDF4")
                except Exception:
                    pass

            card.bind("<Button-1>", lambda e, f=_select: f())
            for child in card.winfo_children():
                child.bind("<Button-1>", lambda e, f=_select: f())
                for grandchild in child.winfo_children():
                    grandchild.bind("<Button-1>", lambda e, f=_select: f())

            if ip and not selected_ip.get():
                _select()

    _build_device_list()

    # تحديث + IP يدوي
    ctrl_row = tk.Frame(main_body, bg="white")
    ctrl_row.pack(fill="x", padx=20, pady=(6, 0))
    tk.Button(ctrl_row, text="تحديث", command=_build_device_list,
              bg="#F1F5F9", fg="#475569", bd=0, padx=12, pady=4,
              font=("Segoe UI", 9), cursor="hand2").pack(side="right")

    manual_row = tk.Frame(main_body, bg="white")
    manual_row.pack(fill="x", padx=20, pady=(6, 4))
    tk.Label(manual_row, text="أو أدخل IP يدوياً:", bg="white",
             font=("Segoe UI", 9), fg="#94A3B8").pack(side="right")
    manual_ip_var = tk.StringVar()
    tk.Entry(manual_row, textvariable=manual_ip_var, width=18,
             font=("Segoe UI", 9), bd=1, relief="solid").pack(side="right", padx=(6, 10))

    # ── نوع المحتوى (Tabs) ────────────────────────────────────
    ttk.Separator(main_body).pack(fill="x", padx=20, pady=(8, 0))

    tabs_row = tk.Frame(main_body, bg="white")
    tabs_row.pack(fill="x", padx=20, pady=(10, 6))
    mode_var = tk.StringVar(value="file")

    def _make_tab(parent, text, val):
        def _activate():
            mode_var.set(val)
            _switch_mode(val)
            file_tab_btn.config(
                bg="#2E7D32" if mode_var.get() == "file" else "#F1F5F9",
                fg="white" if mode_var.get() == "file" else "#475569")
            text_tab_btn.config(
                bg="#2E7D32" if mode_var.get() == "text" else "#F1F5F9",
                fg="white" if mode_var.get() == "text" else "#475569")
        return tk.Button(parent, text=text, command=_activate,
                         bd=0, padx=20, pady=6,
                         font=("Segoe UI", 10), cursor="hand2")

    file_tab_btn = tk.Button(tabs_row, text="ملف",
                              command=lambda: None,
                              bg="#2E7D32", fg="white",
                              bd=0, padx=20, pady=6,
                              font=("Segoe UI", 10), cursor="hand2")
    file_tab_btn.pack(side="right")

    text_tab_btn = tk.Button(tabs_row, text="نص",
                              command=lambda: None,
                              bg="#F1F5F9", fg="#475569",
                              bd=0, padx=20, pady=6,
                              font=("Segoe UI", 10), cursor="hand2")
    text_tab_btn.pack(side="right", padx=(0, 4))

    def _switch_tabs(val):
        mode_var.set(val)
        _switch_mode(val)
        file_tab_btn.config(
            bg="#2E7D32" if val == "file" else "#F1F5F9",
            fg="white" if val == "file" else "#475569")
        text_tab_btn.config(
            bg="#2E7D32" if val == "text" else "#F1F5F9",
            fg="white" if val == "text" else "#475569")

    file_tab_btn.config(command=lambda: _switch_tabs("file"))
    text_tab_btn.config(command=lambda: _switch_tabs("text"))

    # ── منطقة المحتوى ────────────────────────────────────────
    content_area = tk.Frame(main_body, bg="white")
    content_area.pack(fill="both", expand=True, padx=20, pady=(0, 8))

    # — ملف —
    file_frame = tk.Frame(content_area, bg="white")
    file_path_var = tk.StringVar(value="")

    file_name_label = tk.Label(
        file_frame, text="لم يتم اختيار ملف بعد",
        bg="#F8FAFC", font=("Segoe UI", 9), fg="#94A3B8",
        anchor="center", relief="solid", bd=1)
    file_name_label.pack(fill="x", pady=(4, 8), ipady=10)

    def _pick_file():
        fpath = filedialog.askopenfilename()
        if fpath:
            file_path_var.set(fpath)
            fname = os.path.basename(fpath)
            fsize = os.path.getsize(fpath)
            sz = (f"{fsize/1048576:.1f} MB"
                  if fsize > 1048576 else f"{fsize/1024:.0f} KB")
            file_name_label.config(text=f"{fname}  ({sz})", fg="#1E293B")

    tk.Button(file_frame, text="اختيار ملف", command=_pick_file,
              bg="#F1F5F9", fg="#374151", bd=0, padx=14, pady=7,
              font=("Segoe UI", 10), cursor="hand2").pack()

    # — نص —
    text_frame = tk.Frame(content_area, bg="white")
    text_input  = tk.Text(text_frame, font=("Segoe UI", 10),
                          bd=1, relief="solid", height=4, wrap="word")
    text_input.pack(fill="both", expand=True, pady=4)

    def _switch_mode(mode):
        if mode == "file":
            text_frame.pack_forget()
            file_frame.pack(fill="both", expand=True)
        else:
            file_frame.pack_forget()
            text_frame.pack(fill="both", expand=True)

    file_frame.pack(fill="both", expand=True)

    # ── شريط التقدم ──────────────────────────────────────────
    progress_var   = tk.IntVar(value=0)
    progress_bar   = ttk.Progressbar(main_body, variable=progress_var, maximum=100)
    progress_label = tk.Label(main_body, text="", bg="white",
                              font=("Segoe UI", 9), fg="#64748B")

    # ── أزرار الإرسال / الإلغاء ──────────────────────────────
    ttk.Separator(main_body).pack(fill="x", padx=20, pady=(4, 0))
    btn_frame = tk.Frame(main_body, bg="white")
    btn_frame.pack(fill="x", padx=20, pady=(10, 14))

    def _do_send():
        ip   = manual_ip_var.get().strip() or selected_ip.get()
        port = selected_port.get() or 7789
        if not ip:
            messagebox.showwarning("وميض",
                t("send_no_device_warn"), parent=win)
            return
        mode = mode_var.get()
        if mode == "file":
            fpath = file_path_var.get()
            if not fpath:
                messagebox.showwarning("وميض",
                    t("send_no_file_warn"), parent=win)
                return
            send_btn.config(state="disabled", bg="#94A3B8")
            progress_bar.pack(fill="x", padx=20, pady=(0, 4))
            progress_label.pack(fill="x", padx=20)

            def _thread():
                def progress(sent, total):
                    pct = int(sent * 100 / total)
                    try:
                        win.after(0, lambda: progress_var.set(pct))
                        win.after(0, lambda: progress_label.config(
                            text=t("send_progress", pct=pct)))
                    except Exception:
                        pass
                loop = asyncio.new_event_loop()
                asyncio.set_event_loop(loop)
                success, msg = loop.run_until_complete(
                    self.sender.send_file(ip, port, fpath, progress))
                try:
                    if success:
                        win.after(0, lambda: progress_label.config(
                            text=t("send_file_ok")))
                        win.after(1500, win.destroy)
                        self.root.after(0, lambda: self._toast(
                            "وميض", t("toast_file_ok")))
                    else:
                        win.after(0, lambda: (
                            progress_label.config(
                                text=t("send_fail", msg=msg)),
                            send_btn.config(state="normal",
                                            bg="#2E7D32")))
                except Exception:
                    pass
            Thread(target=_thread, daemon=True).start()
        else:
            content = text_input.get("1.0", "end-1c").strip()
            if not content:
                messagebox.showwarning("وميض",
                    t("send_no_text_warn"), parent=win)
                return
            send_btn.config(state="disabled", bg="#94A3B8")
            progress_label.pack(fill="x", padx=20)
            progress_label.config(text=t("send_text_progress"))

            def _thread():
                loop = asyncio.new_event_loop()
                asyncio.set_event_loop(loop)
                success, msg = loop.run_until_complete(
                    self.sender.send_text(ip, port, content))
                try:
                    if success:
                        win.after(0, lambda: progress_label.config(
                            text=t("send_text_ok")))
                        win.after(1500, win.destroy)
                        self.root.after(0, lambda: self._toast(
                            "وميض", t("toast_text_ok")))
                    else:
                        win.after(0, lambda: (
                            progress_label.config(
                                text=t("send_fail", msg=msg)),
                            send_btn.config(state="normal",
                                            bg="#2E7D32")))
                except Exception:
                    pass
            Thread(target=_thread, daemon=True).start()

    send_btn = tk.Button(
        btn_frame, text="إرسال", command=_do_send,
        bg="#2E7D32", fg="white", bd=0, padx=30, pady=10,
        font=("Segoe UI", 11, "bold"), cursor="hand2",
        activebackground="#1B5E20", activeforeground="white")
    send_btn.pack(side="right")

    tk.Button(btn_frame, text="إلغاء", command=win.destroy,
              bg="#F1F5F9", fg="#64748B", bd=0, padx=20, pady=10,
              font=("Segoe UI", 10), cursor="hand2").pack(side="right", padx=(0, 8))

    win.bind("<Return>", lambda _e: _do_send())
    win.bind("<Escape>", lambda _e: win.destroy())
```

---

## التعديل ٥ — نافذة الاقتران (دالة `_ask_pairing_approval`)

هذا تعديل **جزئي فقط** — استبدل الجزء الداخلي من `_show()` من بعد تهيئة النافذة:

**ابحث عن هذا الكود** (السطر ~2187):
```python
hdr = tk.Frame(win, bg="#FB923C", height=54); hdr.pack(fill="x"); hdr.pack_propagate(False)
tk.Label(hdr, text="🔐 طلب اقتران جديد", bg="#FB923C", fg="white",
         font=("Segoe UI", 13, "bold")).pack(pady=14)

body = tk.Frame(win, bg="white"); body.pack(fill="both", expand=True, padx=22, pady=14)
tk.Label(body, text=f"📱 {name or 'جهاز غير معروف'}", bg="white",
         font=("Segoe UI", 13, "bold"), fg="#111827",
         anchor="e").pack(fill="x")
tk.Label(body, text=f"العنوان: {ip}", bg="white",
         font=("Segoe UI", 9), fg="#6B7280",
         anchor="e").pack(fill="x", pady=(2, 0))
tk.Label(body,
         text="يريد هذا الهاتف إرسال ملفات إلى حاسوبك للمرة الأولى.",
         bg="white", font=("Segoe UI", 10), fg="#374151",
         anchor="e", wraplength=380, justify="right").pack(fill="x", pady=(12, 0))
tk.Label(body,
         text="اسمح فقط إذا كنت تعرف مَن يرسل. بعد الموافقة لن تُسأل مجدداً.",
         bg="white", font=("Segoe UI", 9, "italic"), fg="#9CA3AF",
         anchor="e", wraplength=380, justify="right").pack(fill="x", pady=(4, 0))

btns = tk.Frame(win, bg="white"); btns.pack(fill="x", padx=22, pady=(0, 18))
```

**استبدله بـ:**
```python
hdr = tk.Frame(win, bg="#D97706", height=50)
hdr.pack(fill="x"); hdr.pack_propagate(False)
tk.Label(hdr, text="طلب اقتران جديد", bg="#D97706", fg="white",
         font=("Segoe UI", 13, "bold")).pack(expand=True)

body = tk.Frame(win, bg="white")
body.pack(fill="both", expand=True, padx=24, pady=16)

tk.Label(body, text=name or "جهاز غير معروف", bg="white",
         font=("Segoe UI", 13, "bold"), fg="#1E293B",
         anchor="e").pack(fill="x")
tk.Label(body, text=ip, bg="white",
         font=("Segoe UI", 9), fg="#94A3B8",
         anchor="e").pack(fill="x", pady=(2, 0))
tk.Frame(body, bg="#E2E8F0", height=1).pack(fill="x", pady=12)
tk.Label(body,
         text="يريد هذا الهاتف إرسال ملفات إلى حاسوبك للمرة الأولى.",
         bg="white", font=("Segoe UI", 10), fg="#374151",
         anchor="e", wraplength=380, justify="right").pack(fill="x")
tk.Label(body,
         text="اسمح فقط إذا كنت تعرف من يرسل. بعد الموافقة لن تُسأل مجدداً.",
         bg="white", font=("Segoe UI", 9), fg="#94A3B8",
         anchor="e", wraplength=380, justify="right").pack(fill="x", pady=(6, 0))

btns = tk.Frame(win, bg="white"); btns.pack(fill="x", padx=24, pady=(0, 18))
```

**وابحث عن:**
```python
allow_btn = tk.Button(btns, text="✓ السماح دائماً", bg="#2E7D32", fg="white", bd=0,
```
**استبدله بـ:**
```python
allow_btn = tk.Button(btns, text="السماح دائماً", bg="#2E7D32", fg="white", bd=0,
```
**وابحث عن:**
```python
tk.Button(btns, text="✗ رفض", bg="#FEE2E2", fg="#991B1B", bd=0,
```
**استبدله بـ:**
```python
tk.Button(btns, text="رفض", bg="#FEE2E2", fg="#991B1B", bd=0,
```

---

## التعديل ٦ — نافذة التشخيص (دالة `_show_diagnostics`)

هذا تعديل جزئي. **ابحث عن** (السطر ~2130):
```python
tk.Button(bf, text="📋 نسخ التقرير", command=_copy, bg="#E2E8F0",
          bd=0, padx=14, pady=6, cursor="hand2").pack(side="left")
tk.Button(bf, text="📄 فتح ملف اللوج", command=_open_log, bg="#DBEAFE", fg="#1E40AF",
          bd=0, padx=14, pady=6, cursor="hand2").pack(side="left", padx=6)
tk.Button(bf, text="إغلاق", command=win.destroy, bg="#F3F4F6",
          bd=0, padx=14, pady=6, cursor="hand2").pack(side="right")
```
**استبدله بـ:**
```python
tk.Button(bf, text="نسخ التقرير", command=_copy,
          bg="#F1F5F9", fg="#374151",
          bd=0, padx=14, pady=7, cursor="hand2",
          font=("Segoe UI", 9)).pack(side="left")
tk.Button(bf, text="فتح ملف اللوج", command=_open_log,
          bg="#EFF6FF", fg="#1E40AF",
          bd=0, padx=14, pady=7, cursor="hand2",
          font=("Segoe UI", 9)).pack(side="left", padx=6)
tk.Button(bf, text="إغلاق", command=win.destroy,
          bg="#F1F5F9", fg="#64748B",
          bd=0, padx=14, pady=7, cursor="hand2",
          font=("Segoe UI", 9)).pack(side="right")
```

---

## ملاحظات التطبيق

| # | التعديل | الأثر |
|---|---|---|
| ١ | `_build_ui` كامل | الأزرار السفلية + الخطوط |
| ٢ | `_build_home` كامل | ترتيب: حالة → ملفات → إرسال |
| ٣ | `_build_recent_row` كامل | صفوف أنظف بدون أيقونات |
| ٤ | `_show_unified_send_dialog` كامل | نافذة إرسال محسّنة |
| ٥ | جزء من `_ask_pairing_approval` | نافذة الاقتران |
| ٦ | جزء من `_show_diagnostics` | أزرار التشخيص |

> التعديلات ١-٤ مستقلة تماماً — يمكن تطبيق كل منها بدون الأخرى.  
> التعديلات ٥-٦ تجميلية ثانوية.
