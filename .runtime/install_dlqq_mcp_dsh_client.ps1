# dlqq-mcp-client automatic installer for DeepSeek Harness.
# Downloads the plugin source from Gitee (preferred) or GitHub (fallback),
# installs it into the DSH profile and wires it to this deployment.
# All console output is English/ASCII only.

param(
    [string]$ProfileDir = "",
    [switch]$Ai
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Text) { Write-Host "[*] $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text)   { Write-Host "[OK] $Text" -ForegroundColor Green }
function Write-Warn([string]$Text) { Write-Host "[!] $Text" -ForegroundColor Yellow }
function Write-Fail([string]$Text) { Write-Host "[X] $Text" -ForegroundColor Red }

function Invoke-Download([string]$Url, [string]$TargetFile) {
    if (Test-Path -LiteralPath $TargetFile) {
        Remove-Item -LiteralPath $TargetFile -Force
    }
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        & $curl.Source -L --fail --silent --show-error --retry 2 --connect-timeout 20 -o $TargetFile $Url
        if ($LASTEXITCODE -ne 0) { return $false }
    } else {
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $Url -OutFile $TargetFile -UseBasicParsing
        } catch {
            return $false
        }
    }
    if (-not (Test-Path -LiteralPath $TargetFile)) { return $false }
    $size = (Get-Item -LiteralPath $TargetFile).Length
    if ($size -lt 20000) {
        Write-Warn ("Downloaded file is too small ({0} bytes)" -f $size)
        return $false
    }
    return $true
}

function Invoke-Npm([string[]]$NpmArguments, [string]$WorkDir) {
    $npm = Get-Command npm -ErrorAction SilentlyContinue
    if (-not $npm) {
        throw "npm was not found in PATH. Install Node.js 20+ first."
    }
    Push-Location -LiteralPath $WorkDir
    try {
        & $npm.Source @NpmArguments
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

function Remove-DirectorySafe([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        cmd /c rmdir "$Path" | Out-Null
    } else {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
}

$DeployDir = $PSScriptRoot
if (-not $DeployDir) {
    $DeployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
}
$JarPath = Join-Path $DeployDir "dlqq_mcp_server.jar"

if (-not (Test-Path -LiteralPath $JarPath)) {
    Write-Fail "dlqq_mcp_server.jar was not found next to this script: $DeployDir"
    Write-Fail "Put this installer in the deployment directory (same folder as dlqq_mcp_server.jar)."
    exit 1
}

if (-not $ProfileDir) {
    $ProfileDir = Join-Path $env:USERPROFILE ".dsh\profiles\desktop"
}
$ProfileDir = [IO.Path]::GetFullPath($ProfileDir)
$ProfilePackage = Join-Path $ProfileDir "package.json"
if (-not (Test-Path -LiteralPath $ProfilePackage)) {
    Write-Fail "DSH profile was not found: $ProfileDir"
    Write-Fail "Start DeepSeek Harness once, or pass the profile directory as the first argument."
    exit 1
}

$PluginRoot = Join-Path $env:USERPROFILE ".dsh\plugins"
$PluginDir = Join-Path $PluginRoot "dlqq-mcp-client"
$TempDir = Join-Path $env:TEMP ("dlqq_mcp_dsh_client_" + [Guid]::NewGuid().ToString("N"))

$DownloadSources = @(
    @{ Name = "Gitee";  Url = "https://gitee.com/donglouqiaoque/dlqq-mcp-server-dsh-client/repository/archive/master.zip" },
    @{ Name = "GitHub"; Url = "https://github.com/donglouqiaoque/dlqq-mcp-server-dsh-client/archive/refs/heads/master.zip" }
)

Write-Step "Deployment directory: $DeployDir"
Write-Step "DSH profile directory: $ProfileDir"
Write-Step "Plugin install directory: $PluginDir"
Write-Host ""

try {
    New-Item -ItemType Directory -Force -Path $PluginRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $TempDir | Out-Null

    # 1) Download from Gitee first, fall back to GitHub.
    $ZipFile = Join-Path $TempDir "plugin.zip"
    $UsedSource = $null
    foreach ($source in $DownloadSources) {
        Write-Step ("Downloading from {0} ..." -f $source.Name)
        if (Invoke-Download -Url $source.Url -TargetFile $ZipFile) {
            $UsedSource = $source.Name
            Write-Ok ("Downloaded from {0}" -f $source.Name)
            break
        }
        Write-Warn ("Download from {0} failed" -f $source.Name)
    }
    if (-not $UsedSource) {
        throw "Could not download the plugin from Gitee or GitHub. Check the network connection."
    }

    # 2) Extract the archive.
    $ExtractDir = Join-Path $TempDir "extract"
    New-Item -ItemType Directory -Force -Path $ExtractDir | Out-Null
    Write-Step "Extracting archive ..."
    & tar -xf $ZipFile -C $ExtractDir
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract the downloaded archive."
    }
    $SourceDir = Get-ChildItem -LiteralPath $ExtractDir -Directory | Select-Object -First 1
    if (-not $SourceDir) {
        throw "The downloaded archive has an unexpected layout."
    }
    $SourcePackageJson = Join-Path $SourceDir.FullName "package.json"
    if (-not (Test-Path -LiteralPath $SourcePackageJson)) {
        throw "package.json is missing in the extracted archive."
    }
    if ((Get-Content -LiteralPath $SourcePackageJson -Raw) -notmatch '"name"\s*:\s*"dlqq-mcp-client"') {
        throw "The downloaded archive is not the dlqq-mcp-client plugin."
    }
    Write-Ok "Archive extracted"

    # 3) Replace the installed plugin copy.
    New-Item -ItemType Directory -Force -Path $PluginDir | Out-Null
    Write-Step "Installing plugin files ..."
    Get-ChildItem -LiteralPath $PluginDir -Force | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Recurse -Force
    }
    Copy-Item -Path (Join-Path $SourceDir.FullName "*") -Destination $PluginDir -Recurse -Force
    Write-Ok "Plugin files copied"

    # 4) Install plugin dependencies (Dsh peer packages + MCP client).
    Write-Step "Installing plugin dependencies with npm (this may take a while) ..."
    $npmExit = Invoke-Npm -NpmArguments @("install", "--no-audit", "--no-fund") -WorkDir $PluginDir
    if ($npmExit -ne 0) {
        throw "npm install failed in the plugin directory (exit code $npmExit)."
    }
    $DshDeps = Join-Path $PluginDir "node_modules\@deepseek-ai"
    if (-not (Test-Path -LiteralPath $DshDeps)) {
        throw "@deepseek-ai dependencies are missing after npm install."
    }
    Write-Ok "Plugin dependencies installed"

    # 5) Make sure the build artifact exists.
    $BuildArtifact = Join-Path $PluginDir "dist\plugin.js"
    if (-not (Test-Path -LiteralPath $BuildArtifact)) {
        Write-Step "dist\plugin.js is missing, building the plugin ..."
        $buildExit = Invoke-Npm -NpmArguments @("run", "build") -WorkDir $PluginDir
        if ($buildExit -ne 0 -or -not (Test-Path -LiteralPath $BuildArtifact)) {
            throw "Plugin build failed: dist\plugin.js was not produced."
        }
        Write-Ok "Plugin rebuilt"
    } else {
        Write-Ok "dist\plugin.js found"
    }

    # 6) Register the plugin in the DSH profile (dependency + bundle list).
    Write-Step "Updating DSH profile package.json ..."
    Copy-Item -LiteralPath $ProfilePackage -Destination ($ProfilePackage + ".bak-dlqq-install") -Force
    $profile = Get-Content -LiteralPath $ProfilePackage -Raw | ConvertFrom-Json
    if (-not $profile.dependencies) {
        $profile | Add-Member -MemberType NoteProperty -Name dependencies -Value ([pscustomobject]@{}) -Force
    }
    $linkTarget = ($PluginDir -replace "\\", "/")
    $profile.dependencies | Add-Member -MemberType NoteProperty -Name "dlqq-mcp-client" -Value ("link:" + $linkTarget) -Force

    if (-not $profile.dsh) {
        $profile | Add-Member -MemberType NoteProperty -Name dsh -Value ([pscustomobject]@{}) -Force
    }
    if (-not $profile.dsh.profile) {
        $profile.dsh | Add-Member -MemberType NoteProperty -Name profile -Value ([pscustomobject]@{}) -Force
    }
    $bundles = @()
    if ($profile.dsh.profile.bundles) {
        $bundles = @($profile.dsh.profile.bundles)
    }
    if ($bundles -notcontains "dlqq-mcp-client") {
        $bundles += "dlqq-mcp-client"
    }
    $profile.dsh.profile | Add-Member -MemberType NoteProperty -Name bundles -Value $bundles -Force

    $profileJson = $profile | ConvertTo-Json -Depth 12
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($ProfilePackage, $profileJson, $utf8NoBom)
    Write-Ok "Profile package.json updated"

    # 7) Link the plugin package into node_modules (junction fallback).
    $nodeModules = Join-Path $ProfileDir "node_modules"
    New-Item -ItemType Directory -Force -Path $nodeModules | Out-Null
    $linkDir = Join-Path $nodeModules "dlqq-mcp-client"
    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    if ($pnpm) {
        Write-Step "Refreshing profile node_modules with pnpm ..."
        Push-Location -LiteralPath $ProfileDir
        try {
            & $pnpm.Source install --prefer-offline --ignore-scripts
            if ($LASTEXITCODE -ne 0) {
                Write-Warn "pnpm install failed, using a directory link instead"
            }
        } catch {
            Write-Warn "pnpm install failed, using a directory link instead"
        } finally {
            Pop-Location
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $linkDir "package.json"))) {
        Write-Step "Creating node_modules link ..."
        Remove-DirectorySafe -Path $linkDir
        cmd /c mklink /J "$linkDir" "$PluginDir" | Out-Null
        if (-not (Test-Path -LiteralPath (Join-Path $linkDir "package.json"))) {
            throw "Could not create the node_modules link for the plugin."
        }
    }
    Write-Ok "Plugin linked into the DSH profile"

    # 8) Enable the plugin in the profile patch (only when no entry exists yet).
    Write-Step "Configuring the DSH plugin patch ..."
    $patchFile = Join-Path $ProfileDir "cordis.patch.yml"
    $javaExe = Join-Path $DeployDir "java\bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExe)) {
        $javaExe = "java"
    }
    $javaLiteral = ($javaExe -replace "\\", "/")
    $jarLiteral = ($JarPath -replace "\\", "/")
    $patchLines = @(
        "- id: dlqq-mcp-client",
        "  disabled: false",
        "  config:",
        "    serverName: dlqq-mcp",
        "    transport: stdio",
        "    command: '" + $javaLiteral + "'",
        "    args:",
        "      - '-jar'",
        "      - '" + $jarLiteral + "'",
        "    sessionMetaKey: 'dsh/sessionId'",
        "    agentMetaKey: 'dsh/agentId'",
        "    workspaceMetaKey: 'dsh/workspace'"
    )
    $patchBlock = ($patchLines -join "`r`n")
    if (Test-Path -LiteralPath $patchFile) {
        $patchContent = Get-Content -LiteralPath $patchFile -Raw
        if ($patchContent -match "(?m)^\s*-\s*id:\s*dlqq-mcp-client\s*$") {
            Write-Warn "cordis.patch.yml already contains a dlqq-mcp-client entry; kept unchanged."
        } else {
            Copy-Item -LiteralPath $patchFile -Destination ($patchFile + ".bak-dlqq-install") -Force
            $newContent = $patchContent.TrimEnd() + "`r`n`r`n" + $patchBlock + "`r`n"
            [IO.File]::WriteAllText($patchFile, $newContent, $utf8NoBom)
            Write-Ok "dlqq-mcp-client entry appended to cordis.patch.yml"
        }
    } else {
        $header = "# dlqq-mcp-client entry generated by install_dlqq_mcp_dsh_client.bat`r`n`r`n"
        [IO.File]::WriteAllText($patchFile, $header + $patchBlock + "`r`n", $utf8NoBom)
        Write-Ok "cordis.patch.yml created with the dlqq-mcp-client entry"
    }

    Write-Host ""
    Write-Ok ("Installation completed (source: {0})" -f $UsedSource)
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "  1. Start DeepSeek Harness (or restart it if it was running)."
    Write-Host "  2. Open Plugins > dlqq-mcp-client and verify the MCP command/args."
    Write-Host "  3. The plugin injects session/agent/workspace _meta for the dlqq MCP server."
    exit 0
} catch {
    Write-Host ""
    Write-Fail $_.Exception.Message
    exit 1
} finally {
    if (Test-Path -LiteralPath $TempDir) {
        Remove-Item -LiteralPath $TempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
