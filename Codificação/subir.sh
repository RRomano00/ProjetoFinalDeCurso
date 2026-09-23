#!/usr/bin/env bash
# Sobe backend + front-end (build PWA) do FalaCidade em um comando.
#   ./subir.sh            -> PC:      http://localhost:4173  (service worker ativo)
#   ./subir.sh --celular  -> celular: um tunel HTTPS, imprime a URL
# Ctrl+C derruba tudo.
#
# O front e a API saem pelo mesmo endereco: servir.mjs entrega o build e repassa
# /api para a 8080. Um tunel so, sem CORS, e o environment.ts fica fixo em '/api' —
# antes a URL do tunel ia compilada no bundle, o service worker guardava esse bundle
# e no run seguinte o PWA chamava um tunel morto (a tela de login chamava isso de
# "senha incorreta").
#
# Personalizacao (variaveis de ambiente, ou edite os defaults abaixo):
#   PORTA=8081 ./subir.sh                          -> troca a porta local do front
#   ./subir.sh --celular  -> ja usa o dominio fixo do ngrok reservado na conta
#   DOMINIO= ./subir.sh --celular   -> forca cloudflared, com URL aleatoria
#   DOMINIO=outro.ngrok-free.dev ./subir.sh --celular   -> troca o dominio
set -euo pipefail
cd "$(dirname "$0")"

MODO=${1:-}
PORTA=${PORTA:-4173}      # porta local do front
DOMINIO=${DOMINIO-duration-dismiss-pacifist.ngrok-free.dev}   # dominio ngrok fixo do front
FRONT=front-end
DIST=$FRONT/dist/fala-cidade/browser
LOG=/tmp/falacidade; mkdir -p "$LOG"
PIDS=()

if [[ -t 2 ]]; then VERM=$'\033[1;31m'; VERD=$'\033[1;32m'; ZERO=$'\033[0m'
else                VERM=''; VERD=''; ZERO=''; fi
erro()    { echo "${VERM}[ERRO]${ZERO} $*" >&2; }
sucesso() { echo "${VERD}[SUCCESS]${ZERO} $*"; }
ULTIMO_PID=
URL=

cleanup() {
  for p in "${PIDS[@]:-}"; do kill -- -"$p" 2>/dev/null || true; done
}
trap cleanup EXIT INT TERM HUP

porta_ativa() { timeout 1 bash -c "</dev/tcp/127.0.0.1/$1" 2>/dev/null; }

segue() { # segue <nome> -> ecoa na tela as linhas de erro do log enquanto roda
  setsid bash -c "tail -n0 -F '$LOG/$1.log' \
    | grep -a --line-buffered -iE 'error|exception|caused by|failed|failure|refused|denied|timeout' \
    | sed -u 's/^/   ${VERM}[ERRO]${ZERO} [$1] /'" >&2 &
  PIDS+=($!)
}

bg() { # bg <nome> <cmd...>  -> roda em background, log em $LOG/<nome>.log
  local nome=$1; shift
  rm -f "$LOG/$nome.log"                       # inode novo: processo velho nao fura este log
  setsid "$@" >"$LOG/$nome.log" 2>&1 &
  ULTIMO_PID=$!; PIDS+=($ULTIMO_PID)
  segue "$nome"
}

espera() { # espera <nome> <porta> <segundos>
  local i pid=${ULTIMO_PID:-}
  for ((i = 0; i < $3; i++)); do
    porta_ativa "$2" && return 0
    if [[ -n $pid ]] && ! kill -0 "$pid" 2>/dev/null; then break; fi   # morreu: nao espera o timeout
    sleep 1
  done
  erro "$1 nao subiu na porta $2 — veja $LOG/$1.log"
  tail -20 "$LOG/$1.log" >&2; exit 1
}

tunel() { # tunel <nome> <porta> -> devolve a URL https em $URL (sem subshell: o
         #                                cleanup precisa enxergar o PID do tunel)
  local url i log=$LOG/tunel-$1.log
  if [[ -n $DOMINIO && $1 == front ]] && command -v ngrok >/dev/null; then
    bg "tunel-$1" ngrok http "$2" --url "https://${DOMINIO#https://}" --log stdout
  else
    [[ -n $DOMINIO && $1 == front ]] && echo "   (ngrok nao instalado — usando cloudflared, URL aleatoria)" >&2
    bg "tunel-$1" npx -y cloudflared tunnel --url "http://127.0.0.1:$2"
  fi
  local pid=$ULTIMO_PID
  for ((i = 0; i < 60; i++)); do
    if ! kill -0 "$pid" 2>/dev/null; then
      erro "tunel de $1 caiu — veja $log"; tail -20 "$log" >&2; exit 1
    fi
    url=$(grep -av 'lvl=eror\|ERROR\|err=' "$log" 2>/dev/null \
          | grep -aom1 'https://[a-z0-9.-]*\(trycloudflare\.com\|ngrok[a-z.-]*\)') \
      && [[ -n $url ]] && { URL=$url; return 0; }
    sleep 1
  done
  erro "tunel de $1 nao gerou URL — veja $log"; tail -20 "$log" >&2; exit 1
}

# 1. backend
if porta_ativa 8080; then
  sucesso "backend ja esta no ar na 8080"
else
  echo "== subindo backend (mvnw spring-boot:run)"
  bg backend bash -c 'cd backend && ./mvnw -q spring-boot:run'
  espera backend 8080 180
  sucesso "backend no ar na 8080"
fi

# 2. build do front (PWA) + servir (a API sai no mesmo endereco, em /api)
echo "== build do front-end (pode demorar ~1min)"
npm --prefix "$FRONT" run build >"$LOG/build.log" 2>&1 \
  || { erro "build do front falhou — veja $LOG/build.log"; tail -30 "$LOG/build.log" >&2; exit 1; }
sucesso "build do front pronto"
echo "== servindo $DIST na $PORTA (API no mesmo endereco, em /api)"
bg front node servir.mjs "$DIST" "$PORTA" 127.0.0.1:8080
espera front "$PORTA" 30
sucesso "front no ar na $PORTA"

# 3. endereco final
if [[ $MODO == --celular ]]; then
  echo "== abrindo tunel do front"
  echo
  tunel front "$PORTA"
  sucesso "link: $URL"
else
  echo
  sucesso "link: http://localhost:$PORTA"
fi
echo "   logs em $LOG/ — Ctrl+C para derrubar tudo"
wait
