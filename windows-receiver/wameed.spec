# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_data_files, collect_submodules

hiddenimports = ['websockets.legacy.server', 'websockets.legacy.protocol']
hiddenimports += collect_submodules('websockets')
hiddenimports += collect_submodules('customtkinter')
try:
    hiddenimports += collect_submodules('winotify')
except Exception:
    pass
datas = [('src\\wameed.ico', '.'), ('..\\version.properties', '.')]
datas += collect_data_files('customtkinter')


a = Analysis(
    ['src\\receiver.py'],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='Wameed',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=['vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140.dll', 'msvcp140_1.dll', 'ucrtbase.dll', 'python312.dll', 'python3.dll'],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    version='version_info.txt',
    codesign_identity=None,
    entitlements_file=None,
    icon=['src\\wameed.ico'],
)
