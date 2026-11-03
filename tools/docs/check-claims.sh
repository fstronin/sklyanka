#!/usr/bin/env bash
# Проверка устаревших утверждений в документации «Склянки».
#
# Зачем: 01.11.2026 одна запись Р-92 разошлась копиями по пяти нормативным документам,
# и разворот Р-93 оставил в них ложь. Утверждение о состоянии продукта должно жить
# в одном месте и ссылаться на решение; этот скрипт ловит самые дорогие из копий.
#
# Правила:
# - проверяются только действующие документы: docs/**/*.md, AGENTS.md, CONTEXT.md, PLAN.md;
# - не проверяются история и артефакты задач: docs/archive/**, docs/journal/**, docs/tasks/**;
# - строку, которая цитирует историю намеренно, помечают хвостовым комментарием
#   `<!-- docs-check: allow -->` (Markdown) — скрипт её пропускает;
# - выход 0 — чисто, 1 — есть находки (печатаются с файлом и строкой).
#
# Запуск: tools/docs/check-claims.sh

set -uo pipefail

root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$root" || exit 2

# Фразы, которые были правдой и перестали ею быть. Дополнять при каждом развороте решения.
patterns=(
  'сети в продукте нет'
  'сети в поставляемой сборке нет'
  'сетевого контура в продукте нет'
  '`INTERNET` не объявлен'
  '`INTERNET` в сборке нет'
  'без разрешения `INTERNET`'
  'единственный канал'
  'клиентской аналитики нет'
  'клиентской аналитики в продукте нет'
  'профили сверх двух'
  'profiles beyond two'
  'серверной обработки .{0,20}нет'
)

files=$(find docs -name '*.md' \
  -not -path 'docs/archive/*' \
  -not -path 'docs/journal/*' \
  -not -path 'docs/tasks/*' \
  -not -path 'docs/research/*' \
  -print; printf '%s\n' AGENTS.md CONTEXT.md PLAN.md 2>/dev/null)

status=0
for pattern in "${patterns[@]}"; do
  while IFS= read -r hit; do
    [ -z "$hit" ] && continue
    case "$hit" in
      *'docs-check: allow'*) continue ;;
    esac
    printf '%s\n' "$hit"
    status=1
  done < <(grep -rnE --include='*.md' "$pattern" $files 2>/dev/null)
done

if [ "$status" -eq 0 ]; then
  echo "docs: устаревших утверждений не найдено"
else
  echo
  echo "docs: найдены утверждения, которые могли устареть — проверьте решение и добавьте «до SKL-NN» или ссылку на Р-N"
fi
exit "$status"
