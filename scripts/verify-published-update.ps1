param(
    [switch]$SkipArtifactUrls
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root "version.properties"

function Read-VersionProperties {
    param([string]$Path)
    $props = @{}
    foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#")) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { continue }
        $props[$line.Substring(0, $idx).Trim()] = $line.Substring($idx + 1).Trim()
    }
    return $props
}

function Add-Error {
    param([string]$Message)
    $script:errors.Add($Message)
}

function Test-PublishedUrl {
    param(
        [string]$Label,
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -Method Head -MaximumRedirection 5 -UseBasicParsing -TimeoutSec 30
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
            Add-Error "$Label URL returned HTTP $($response.StatusCode): $Url"
        }
    } catch {
        Add-Error "$Label URL could not be reached: $($_.Exception.Message)"
    }
}

$props = Read-VersionProperties $versionFile
$versionName = [string]$props["versionName"]
$versionCode = [int]$props["versionCode"]
$owner = [string]$props["githubOwner"]
$repo = [string]$props["githubRepo"]
$errors = New-Object System.Collections.Generic.List[string]

if ([string]::IsNullOrWhiteSpace($versionName) -or $versionCode -le 0) {
    Add-Error "version.properties must contain versionName and a positive versionCode."
}
if ([string]::IsNullOrWhiteSpace($owner) -or [string]::IsNullOrWhiteSpace($repo)) {
    Add-Error "version.properties must contain githubOwner and githubRepo."
}

if ($errors.Count -eq 0) {
    $cacheBust = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $remoteUrl = "https://raw.githubusercontent.com/$owner/$repo/main/update.json?t=$cacheBust"
    Write-Host "Fetching published update metadata:"
    Write-Host $remoteUrl

    try {
        $remoteText = (Invoke-WebRequest -Uri $remoteUrl -UseBasicParsing -Headers @{
            "Cache-Control" = "no-cache"
            "Pragma" = "no-cache"
        } -TimeoutSec 30).Content
        $remote = $remoteText | ConvertFrom-Json
    } catch {
        Add-Error "Could not read published update.json: $($_.Exception.Message)"
    }

    if ($remote) {
        if ([int]$remote.android.versionCode -ne $versionCode) {
            Add-Error "Published android.versionCode is $($remote.android.versionCode), expected $versionCode."
        }
        if ([string]$remote.android.versionName -ne $versionName) {
            Add-Error "Published android.versionName is $($remote.android.versionName), expected $versionName."
        }
        if ([string]$remote.windows.version -ne $versionName) {
            Add-Error "Published windows.version is $($remote.windows.version), expected $versionName."
        }
        if ([string]$remote.android.updateUrl -notmatch "/$([regex]::Escape($versionName))/") {
            Add-Error "Published Android updateUrl does not point at release tag $versionName."
        }
        if ([string]$remote.windows.updateUrl -notmatch "/$([regex]::Escape($versionName))/") {
            Add-Error "Published Windows updateUrl does not point at release tag $versionName."
        }

        if (-not $SkipArtifactUrls) {
            Test-PublishedUrl "Android APK" ([string]$remote.android.updateUrl)
            Test-PublishedUrl "Windows installer" ([string]$remote.windows.updateUrl)
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    throw "Published update verification failed."
}

Write-Host "Published update verification passed: $versionName ($versionCode)"
