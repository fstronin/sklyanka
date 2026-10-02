#!/usr/bin/env bash
# Утвердить прогон эталоном: PNG прогона заменяют tools/visual/baselines (SKL-92).
#
#   ./approve.sh <прогон>
#
# Эталон заменяется целиком: PNG экрана, исчезнувшего из прогона, не переживает
# утверждение. В git попадает только baselines/manifest.sha256: PNG эталонов
# остаются локальными.
set -euo pipefail

SKL_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=config.sh
source "$SKL_DIR/config.sh"
# shellcheck source=lib.sh
source "$SKL_DIR/lib.sh"

if (( $# != 1 )); then
    skl_die "использование: approve.sh <прогон>"
fi

run_dir="$(skl_resolve_run "$1")"
png_count="$(skl_count_png "$run_dir")"
if (( png_count == 0 )); then
    skl_die "в прогоне нет PNG: $run_dir"
fi

manifest_copy="$(mktemp "${TMPDIR:-/tmp}/skl-visual-approve.XXXXXX")"
cleanup() { rm -f "$manifest_copy"; }
trap cleanup EXIT
skl_write_manifest "$run_dir" "$manifest_copy"
if [[ -f "$run_dir/manifest.sha256" ]] && ! cmp -s "$manifest_copy" "$run_dir/manifest.sha256"; then
    skl_warn "манифест прогона расходится с файлами: прогон правился после снятия"
fi

rm -rf "$SKL_BASELINES_DIR"
mkdir -p "$SKL_BASELINES_DIR"
while read -r _hash path; do
    [[ -n "$path" ]] || continue
    source_file="$run_dir/$path"
    if [[ ! -f "$source_file" ]]; then
        skl_die "манифест ссылается на отсутствующий лист: $path"
    fi
    mkdir -p "$SKL_BASELINES_DIR/$(dirname "$path")"
    cp "$source_file" "$SKL_BASELINES_DIR/$path"
done < "$manifest_copy"

skl_write_manifest "$SKL_BASELINES_DIR" "$SKL_BASELINES_DIR/manifest.sha256"
baseline_count="$(skl_count_png "$SKL_BASELINES_DIR")"
if [[ "$baseline_count" != "$png_count" ]]; then
    skl_die "эталон не сходится с прогоном: PNG $baseline_count, в прогоне $png_count"
fi

skl_log "эталон утверждён: $baseline_count PNG из $run_dir"
skl_log "в git уходит только $SKL_BASELINES_DIR/manifest.sha256"
