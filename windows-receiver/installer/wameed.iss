; ==========================================================
;  Wameed — Inno Setup Script
;  Build via: scripts\build.bat  (or ISCC installer\wameed.iss)
; ==========================================================

#define AppName        "Wameed"
#define AppNameAr      "وميض"
#define AppVersion     "1.8.2"
#define AppPublisher   "Wameed Project"
#define AppExe         "Wameed.exe"
#define AppURL         "https://github.com/"

[Setup]
AppId={{A1C3F7D2-2E7D-4E32-9C11-WAMEED00001}}
AppName={#AppName} — {#AppNameAr}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir=Output
OutputBaseFilename=WameedSetup-{#AppVersion}
SetupIconFile=..\src\wameed.ico
UninstallDisplayIcon={app}\{#AppExe}
Compression=lzma2/ultra
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64
; Bilingual-friendly wizard
ShowLanguageDialog=auto

[Languages]
Name: "english";  MessagesFile: "compiler:Default.isl"
Name: "arabic";   MessagesFile: "compiler:Languages\Arabic.isl"

[CustomMessages]
english.AutostartDesc=Start Wameed automatically with Windows
english.AutostartGroup=Startup:
english.FirewallDesc=Add Windows Firewall exception for ports 7788/7789
english.FirewallGroup=Network:
english.LaunchNow=Launch Wameed now
arabic.AutostartDesc=تشغيل وميض تلقائياً عند بدء ويندوز
arabic.AutostartGroup=التشغيل:
arabic.FirewallDesc=إضافة استثناء Windows Firewall للمنفذين 7788/7789
arabic.FirewallGroup=الشبكة:
arabic.LaunchNow=تشغيل وميض الآن

[Tasks]
Name: "desktopicon";  Description: "{cm:CreateDesktopIcon}";   GroupDescription: "{cm:AdditionalIcons}"
Name: "autostart";    Description: "{cm:AutostartDesc}"; GroupDescription: "{cm:AutostartGroup}"
Name: "firewall";     Description: "{cm:FirewallDesc}"; GroupDescription: "{cm:FirewallGroup}"

[Files]
Source: "..\dist\Wameed.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\src\wameed.ico";  DestDir: "{app}"; Flags: ignoreversion
Source: "README-install.md";  DestDir: "{app}"; Flags: ignoreversion isreadme
Source: "LICENSE.txt";        DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#AppName} — {#AppNameAr}"; Filename: "{app}\{#AppExe}"; IconFilename: "{app}\wameed.ico"
Name: "{autodesktop}\{#AppName} — {#AppNameAr}";  Filename: "{app}\{#AppExe}"; IconFilename: "{app}\wameed.ico"; Tasks: desktopicon
Name: "{userstartup}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: autostart

[Run]
; Firewall exceptions (TCP 7788 WebSocket, UDP 7789 discovery)
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""Wameed WS"" dir=in action=allow protocol=TCP localport=7788";  Flags: runhidden; Tasks: firewall
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""Wameed Discovery"" dir=in action=allow protocol=UDP localport=7789"; Flags: runhidden; Tasks: firewall
; Optionally launch after install
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchNow}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""Wameed WS""";        Flags: runhidden
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""Wameed Discovery"""; Flags: runhidden

[UninstallDelete]
Type: filesandordirs; Name: "{userappdata}\..\.wameed"
