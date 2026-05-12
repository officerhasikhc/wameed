# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules

hiddenimports = ['websockets.legacy.server', 'websockets.legacy.protocol']
hiddenimports += collect_submodules('websockets')


a = Analysis(
    ['src\\receiver.py'],
    pathex=[],
    binaries=[],
    datas=[('src\\wameed.ico', '.'), ('..\\version.properties', '.')],
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
    upx=True,
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
