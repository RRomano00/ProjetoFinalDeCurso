<#
  Sobe backend + front-end (build PWA) do FalaCidade no Windows, sem WSL.
    .\subir.ps1             -> PC:      http://localhost:4173
    .\subir.ps1 -Celular    -> celular: abre o tunel e imprime a URL fixa do ngrok
  Ctrl+C derruba tudo. Logs em %TEMP%\falacidade\.

  Equivalente ao subir.sh. O front e a API saem pelo mesmo endereco: servir.mjs
  entrega o build e repassa /api para a 8080, entao o environment.ts fica fixo em
  '/api' e nao e reescrito a cada execucao.
#>
param(
  [switch] $Celular,
  [int]    $Porta   = 4173,
  [string] $Dominio = 'duration-dismiss-pacifist.ngrok-free.dev'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$Front = 'front-end'
$Dist  = Join-Path $Front 'dist\fala-cidade\browser'
$Log   = Join-Path $env:TEMP 'falacidade'
New-Item -ItemType Directory -Force -Path $Log | Out-Null

$processos = @()

function Limpar {
  foreach ($p in $script:processos) {
    if ($p -and -not $p.HasExited) {
      # /T pega a arvore: npx e mvnw.cmd sao wrappers, o processo real e filho deles
      & taskkill /T /F /PID $p.Id 2>$null | Out-Null
    }
  }
}
[Console]::add_CancelKeyPress({ param($remetente, $evento) $evento.Cancel = $true; Limpar; [Environment]::Exit(130) })

function Sucesso($msg) { Write-Host '[SUCCESS] ' -ForegroundColor Green -NoNewline; Write-Host $msg }
function Erro($msg)    { Write-Host '[ERRO] '    -ForegroundColor Red   -NoNewline; Write-Host $msg }

function PortaAtiva([int] $p) {
  $c = New-Object Net.Sockets.TcpClient
  try { $c.ConnectAsync('127.0.0.1', $p).Wait(1000) -and $c.Connected } catch { $false } finally { $c.Dispose() }
}

function Bg([string] $nome, [string] $exe, [string[]] $argumentos, [string] $dir = $PSScriptRoot) {
  # saida e erro precisam de arquivos separados: o PowerShell recusa o mesmo para os dois
  $p = Start-Process -FilePath $exe -ArgumentList $argumentos -WorkingDirectory $dir `
         -NoNewWindow -PassThru `
         -RedirectStandardOutput "$Log\$nome.log" -RedirectStandardError "$Log\$nome.err.log"
  $script:processos += $p
  return $p
}

function MostraErros([string] $nome) {
  # ecoa na tela as linhas de erro que o processo escreveu, com prefixo [ERRO]
  foreach ($f in "$Log\$nome.log", "$Log\$nome.err.log") {
    if (Test-Path $f) {
      Select-String -Path $f -Pattern 'error|exception|caused by|failed|failure|refused|denied' `
        | Select-Object -Last 15 | ForEach-Object { Erro "[$nome] $($_.Line)" }
    }
  }
}

function Espera([string] $nome, [int] $porta, [int] $segundos, $proc) {
  for ($i = 0; $i -lt $segundos; $i++) {
    if (PortaAtiva $porta) { return }
    if ($proc -and $proc.HasExited) { break }      # morreu: nao espera o timeout
    Start-Sleep -Seconds 1
  }
  Erro "$nome nao subiu na porta $porta - veja $Log\$nome.log"
  MostraErros $nome
  exit 1
}

function Tunel([int] $porta) {
  if (Get-Command ngrok -ErrorAction SilentlyContinue) {
    $p = Bg 'tunel' 'ngrok' @('http', "$porta", '--url', "https://$($Dominio -replace '^https://','')", '--log', 'stdout')
  } else {
    Write-Host '   (ngrok nao instalado - usando cloudflared, URL aleatoria)'
    $p = Bg 'tunel' 'npx' @('-y', 'cloudflared', 'tunnel', '--url', "http://127.0.0.1:$porta")
  }
  for ($i = 0; $i -lt 60; $i++) {
    if ($p.HasExited) { Erro 'o tunel caiu'; MostraErros 'tunel'; exit 1 }
    foreach ($f in "$Log\tunel.log", "$Log\tunel.err.log") {
      if (Test-Path $f) {
        # linha de erro nao conta: o ngrok repete a URL dentro da mensagem de falha
        $m = Get-Content $f | Where-Object { $_ -notmatch 'lvl=eror|ERROR|err=' } |
             Select-String -Pattern 'https://[a-z0-9.-]+(trycloudflare\.com|ngrok[a-z.-]*)' |
             Select-Object -First 1
        if ($m) { return $m.Matches[0].Value }
      }
    }
    Start-Sleep -Seconds 1
  }
  Erro "o tunel nao gerou URL - veja $Log\tunel.log"
  MostraErros 'tunel'
  exit 1
}

try {
  # 1. backend
  if (PortaAtiva 8080) {
    Sucesso 'backend ja esta no ar na 8080'
  } else {
    Write-Host '== subindo backend (mvnw spring-boot:run)'
    $b = Bg 'backend' (Join-Path $PSScriptRoot 'backend\mvnw.cmd') @('-q', 'spring-boot:run') (Join-Path $PSScriptRoot 'backend')
    Espera 'backend' 8080 180 $b
    Sucesso 'backend no ar na 8080'
  }

  # 2. build do front (PWA) + servir (a API sai no mesmo endereco, em /api)
  Write-Host '== build do front-end (pode demorar ~1min)'
  & npm.cmd --prefix $Front run build *> "$Log\build.log"
  if ($LASTEXITCODE -ne 0) {
    Erro "build do front falhou - veja $Log\build.log"
    Get-Content "$Log\build.log" -Tail 30 | Write-Host
    exit 1
  }
  Sucesso 'build do front pronto'

  Write-Host "== servindo $Dist na $Porta (API no mesmo endereco, em /api)"
  $f = Bg 'front' 'node' @('servir.mjs', $Dist, "$Porta", '127.0.0.1:8080')
  Espera 'front' $Porta 30 $f
  Sucesso "front no ar na $Porta"

  # 3. endereco final
  if ($Celular) {
    Write-Host '== abrindo tunel'
    Sucesso "link: $(Tunel $Porta)"
  } else {
    Sucesso "link: http://localhost:$Porta"
  }
  Write-Host "   logs em $Log\ - Ctrl+C para derrubar tudo"

  while ($true) { Start-Sleep -Seconds 3600 }
}
finally { Limpar }
