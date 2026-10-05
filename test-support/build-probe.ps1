param([string]$ProxyPath='C:\Users\artyo\Documents\Codex\nordcommands-paper-test-20261004\proxy')
$ErrorActionPreference='Stop'
$resolved=(Resolve-Path -LiteralPath $ProxyPath).Path
if(-not $resolved.StartsWith('C:\Users\artyo\Documents\Codex\',[StringComparison]::OrdinalIgnoreCase)){throw 'LOCAL Velocity required'}
$project=Split-Path -Parent $MyInvocation.MyCommand.Path
$classes=Join-Path $project ('build\probe-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& 'C:\Program Files\Java\jdk-25\bin\javac.exe' --release 25 -encoding UTF-8 -proc:none -classpath (Join-Path $resolved 'velocity.jar') -d $classes (Join-Path $project 'probe\VelocityCommandsTestProbe.java')
if($LASTEXITCODE-ne0){throw 'Probe compile failed'}
Copy-Item -LiteralPath (Join-Path $project 'probe\velocity-plugin.json') -Destination $classes
& 'C:\Program Files\Java\jdk-25\bin\jar.exe' --create --file (Join-Path $project 'build\VelocityCommandsTestProbe.jar') -C $classes .
if($LASTEXITCODE-ne0){throw 'Probe packaging failed'}
