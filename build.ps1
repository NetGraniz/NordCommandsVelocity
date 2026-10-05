param([string]$ProxyPath = 'C:\Users\artyo\Documents\Codex\nordcommands-paper-test-20261004\proxy')
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$resolvedProxy = (Resolve-Path -LiteralPath $ProxyPath).Path
if (-not $resolvedProxy.StartsWith('C:\Users\artyo\Documents\Codex\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Build requires isolated LOCAL Velocity libraries' }
$velocityJar = Join-Path $resolvedProxy 'velocity.jar'
if (-not (Test-Path -LiteralPath $velocityJar)) { throw 'Local Velocity jar missing' }
$buildPath = Join-Path $projectPath 'build'
$workPath = Join-Path $buildPath ('work-' + [Guid]::NewGuid().ToString('N'))
$classesPath = Join-Path $workPath 'classes'
$testClasses = Join-Path $workPath 'test-classes'
$outputPath = Join-Path $buildPath 'NordCommands-Velocity-1.1.0.jar'
$javaPath = 'C:\Program Files\Java\jdk-25\bin'

New-Item -ItemType Directory -Force -Path $classesPath,$testClasses | Out-Null
$sources = Get-ChildItem -LiteralPath (Join-Path $projectPath 'src\main\java') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& (Join-Path $javaPath 'javac.exe') --release 25 -encoding UTF-8 -proc:none -classpath $velocityJar -d $classesPath $sources
if ($LASTEXITCODE -ne 0) { throw 'NordCommands Velocity compilation failed.' }
Copy-Item -Path (Join-Path $projectPath 'src\main\resources\*') -Destination $classesPath -Recurse -Force
$tests = @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'src\test\java') -Recurse -Filter '*.java' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
if ($tests.Count -gt 0) {
    & (Join-Path $javaPath 'javac.exe') --release 25 -encoding UTF-8 -proc:none -classpath ($classesPath+';'+$velocityJar) -d $testClasses $tests
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    & (Join-Path $javaPath 'java.exe') -ea -classpath ($testClasses+';'+$classesPath+';'+$velocityJar) com.nordfjell.nordcommandsvelocity.CommandRegressionTest (Join-Path $buildPath 'unit-results.json')
    if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed' }
}
& (Join-Path $javaPath 'jar.exe') --create --file $outputPath -C $classesPath .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
$entries = & (Join-Path $javaPath 'jar.exe') tf $outputPath
if ($entries -match 'Test|Probe') { throw 'Release contains test code' }
'VELOCITY_SHA256='+(Get-FileHash -LiteralPath $velocityJar -Algorithm SHA256).Hash
'SHA256='+(Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash
Write-Output $outputPath
