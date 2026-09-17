#!/usr/bin/env bash
# Sobe backend + front-end (build PWA) do FalaCidade em um comando.
#   ./subir.sh            -> PC:      http://localhost:4173  (service worker ativo)
#   ./subir.sh --celular  -> celular: tuneis HTTPS, imprime a URL
# Ctrl+C derruba tudo e restaura o environment.ts original.
#
# Personalizacao (variaveis de ambiente, ou edite os defaults abaixo):
#   PORTA=8081 ./subir.sh                          -> troca a porta local do front
#   ./subir.sh --celular  -> ja usa o dominio fixo do ngrok reservado na conta
#                            (veja DOMINIO abaixo); o tunel do backend segue no
#                            cloudflared, com URL aleatoria injetada sozinha.
#   DOMINIO= ./subir.sh --celular   -> forca cloudflared tambem no front
#   DOMINIO=outro.ngrok-free.dev ./subir.sh --celular   -> troca o dominio
set -euo pipefail
cd "$(dirname "$0")"

MODO=${1:-}
PORTA=${PORTA:-4173}      # porta local do front
DOMINIO=${DOMINIO:-duration-dismiss-pacifist.ngrok-free.dev}   # dominio ngrok fixo do front
FRONT=front-end
ENV=$FRONT/src/environments/environment.ts
DIST=$FRONT/dist/fala-cidade/browser
LOG=/tmp/falacidade; mkdir -p "$LOG"
PIDS=()

cleanup() {
  [[ -f $ENV.bak ]] && mv -f "$ENV.bak" "$ENV"
  for p in "${PIDS[@]:-}"; do kill -- -"$p" 2>/dev/null || true; done
}
trap cleanup EXIT INT TERM

porta_ativa() { timeout 1 bash -c "</dev/tcp/127.0.0.1/$1" 2>/dev/null; }

bg() { # bg <nome> <cmd...>  -> roda em background, log em $LOG/<nome>.log
  local nome=$1; shift
  setsid "$@" >"$LOG/$nome.log" 2>&1 &
  PIDS+=($!)
}

espera() { # espera <nome> <porta> <segundos>
  local i
  for ((i = 0; i < $3; i++)); do porta_ativa "$2" && return 0; sleep 1; done
  echo "!! $1 nao subiu na porta $2 — veja $LOG/$1.log" >&2; exit 1
}

tunel() { # tunel <nome> <porta> -> ecoa a URL https
  local url i log=$LOG/tunel-$1.log
  if [[ -n $DOMINIO && $1 == front ]] && command -v ngrok >/dev/null; then
    bg "tunel-$1" ngrok http "$2" --url "https://${DOMINIO#https://}" --log stdout
  else
    [[ -n $DOMINIO && $1 == front ]] && echo "   (ngrok nao instalado — usando cloudflared, URL aleatoria)" >&2
    bg "tunel-$1" npx -y cloudflared tunnel --url "http://127.0.0.1:$2"
  fi
  for ((i = 0; i < 60; i++)); do
    url=$(grep -om1 'https://[a-z0-9.-]*\(trycloudflare\.com\|ngrok[a-z.-]*\)' "$log" 2>/dev/null) && [[ -n $url ]] && { echo "$url"; return 0; }
    sleep 1
  done
  echo "!! tunel de $1 nao gerou URL — veja $log" >&2; exit 1
}

api() { # api <base-url> -> reescreve environment.ts (restaurado no fim)
  [[ -f $ENV.bak ]] || cp "$ENV" "$ENV.bak"
  cat > "$ENV" <<EOF
export const environment = {
    env: 'production',
    api_endpoint: '$1/api',
    authentication_api_endpoint: '$1/api'
}
EOF
}

# 1. backend
if porta_ativa 8080; then
  echo "== backend ja esta no ar na 8080"
else
  echo "== subindo backend (mvnw spring-boot:run)"
  bg backend bash -c 'cd backend && ./mvnw -q spring-boot:run'
  espera backend 8080 180
fi

# 2. URL da API que o front vai usar
if [[ $MODO == --celular ]]; then
  echo "== abrindo tunel do backend"
  BACK_URL=$(tunel backend 8080)
  echo "   API: $BACK_URL/api"
else
  BACK_URL=http://localhost:8080
fi
api "$BACK_URL"

# 3. build do front (PWA) + servir
echo "== build do front-end (pode demorar ~1min)"
npm --prefix "$FRONT" run build >"$LOG/build.log" 2>&1 || { tail -30 "$LOG/build.log"; exit 1; }
echo "== servindo $DIST na $PORTA"
bg front npx -y serve -s "$DIST" -l "$PORTA"
espera front "$PORTA" 30

# 4. endereco final
if [[ $MODO == --celular ]]; then
  echo "== abrindo tunel do front"
  echo
  echo "   >>> link: $(tunel front "$PORTA")"
else
  echo
  echo "   >>> link: http://localhost:$PORTA"
fi
echo "   logs em $LOG/ — Ctrl+C para derrubar tudo"
wait
