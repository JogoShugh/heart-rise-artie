# Sourced by kit/bootstrap.sh and kit/try.sh: each NAME=value in kit/.env, for each NAME not set already, so what you
# export wins, and a line left blank changes nothing. Quotes around a value are dropped. kit/env.example says what
# goes in it.
if [ -f "$here/.env" ]; then
  while IFS='=' read -r key value || [ -n "$key" ]; do
    case $key in '' | \#* | *[!A-Za-z0-9_]*) continue ;; esac
    value=${value%"${value##*[![:space:]]}"}
    value=${value#\"}; value=${value%\"}; value=${value#\'}; value=${value%\'}
    eval "current=\${$key:-}"
    if [ -z "$current" ] && [ -n "$value" ]; then export "$key=$value"; fi
  done < "$here/.env"
fi
