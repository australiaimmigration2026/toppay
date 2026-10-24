$ErrorActionPreference = 'Stop'

if (!$env:JAVA_HOME) {
    $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
}
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw 'JDK 17 was not found. Set the Windows user JAVA_HOME variable to your JDK 17 folder.'
}

$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if (!$env:TOPPAY_STORE_PASSWORD) {
    $secureStorePassword = Read-Host 'Release keystore store password' -AsSecureString
    $env:TOPPAY_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $secureStorePassword).Password
    $secureStorePassword.Dispose()
}
if (!$env:TOPPAY_KEY_PASSWORD) {
    $secureKeyPassword = Read-Host 'Release keystore key password' -AsSecureString
    $env:TOPPAY_KEY_PASSWORD = [System.Net.NetworkCredential]::new('', $secureKeyPassword).Password
    $secureKeyPassword.Dispose()
}

if (!$env:TOPPAY_STORE_PASSWORD -or !$env:TOPPAY_KEY_PASSWORD) {
    throw 'Both Android signing passwords are required to build a release APK.'
}

try {
    Set-Location -LiteralPath (Join-Path $PSScriptRoot '..\android')
    & .\gradlew.bat assembleRelease @args
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Remove-Item Env:TOPPAY_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:TOPPAY_KEY_PASSWORD -ErrorAction SilentlyContinue
}
