$ErrorActionPreference = 'Stop'

# Load the saved user settings even if VS Code was open during SDK installation.
foreach ($variable in @('JAVA_HOME', 'ANDROID_HOME')) {
    $configured = [Environment]::GetEnvironmentVariable($variable, 'User')
    if ($configured) {
        [Environment]::SetEnvironmentVariable($variable, $configured, 'Process')
    }
}

if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw 'JDK 17 is missing. Configure JAVA_HOME before running the app.'
}
if (!$env:ANDROID_HOME -or !(Test-Path -LiteralPath "$env:ANDROID_HOME\platform-tools\adb.exe")) {
    throw 'Android platform-tools are missing. Configure ANDROID_HOME before running the app.'
}

$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$deviceLines = @(& $adbPath devices -l)
if ($LASTEXITCODE -ne 0) { throw 'ADB could not check connected phones.' }
$deviceIds = @($deviceLines | ForEach-Object {
    if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
})
if (!$deviceIds.Count) {
    if ($deviceLines -match '\sunauthorized(?:\s|$)') {
        throw 'Unlock your phone and tap Allow on the Allow USB debugging prompt, then run this command again.'
    }
    throw 'Connect your Android phone using a data-capable USB cable and enable Developer options > USB debugging.'
}

Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if ($deviceIds.Count -eq 1) {
    & $adbPath -s $deviceIds[0] reverse tcp:8081 tcp:8081
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect the phone to the Expo development server over USB.' }
    $selectedLine = $deviceLines | Where-Object { $_ -match ('^' + [regex]::Escape($deviceIds[0]) + '\s') } | Select-Object -First 1
    if ($selectedLine -match '\bmodel:(\S+)') {
        # Expo SDK 54 resolves --device by model name, rather than ADB serial.
        & npm.cmd run android -- --device $Matches[1]
    } else {
        & npm.cmd run android -- --device
    }
} else {
    # Let Expo ask which device to use rather than selecting one silently.
    & npm.cmd run android -- --device
}
exit $LASTEXITCODE
