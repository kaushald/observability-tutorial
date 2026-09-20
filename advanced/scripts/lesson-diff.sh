#!/usr/bin/env bash
# The order service is copied into every lesson, and the diff between two consecutive
# lessons is the lesson. This script shows that diff and guards it.
#
#   scripts/lesson-diff.sh 002-spans    what lesson 002 changed compared with 001
#   scripts/lesson-diff.sh --check      fail if any lesson changed a file it should not
#
# --check exists because a fix made in one copy is easy to forget in the others.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="src/main/java/com/kaushaldalvi/o11y/orders"
TEST="src/test/java/com/kaushaldalvi/o11y/orders"

# Files each lesson may change or add, relative to its order-service directory
allowed_for() {
  case "$1" in
    002-spans) echo "build.gradle $SRC/OrderService.java $SRC/PaymentService.java $TEST/TracingTests.java" ;;
    003-async) echo "$SRC/ConfirmationSender.java $TEST/TracingTests.java" ;;
    004-events) echo "$SRC/OrderService.java $TEST/TracingTests.java" ;;
    005-links) echo "$SRC/Api.java $SRC/Controllers.java $SRC/CustomerOrder.java $SRC/Exceptions.java $SRC/OrderService.java $SRC/PaymentService.java src/main/resources/static/index.html $TEST/OrderServiceApplicationTests.java $TEST/TracingTests.java" ;;
    *) echo "" ;;
  esac
}

lessons() {
  (cd "$ROOT" && ls -d [0-9][0-9][0-9]-*/order-service 2>/dev/null | cut -d/ -f1 | sort)
}

previous_of() {
  local previous=""
  for lesson in $(lessons); do
    if [ "$lesson" = "$1" ]; then
      echo "$previous"
      return
    fi
    previous="$lesson"
  done
}

changed_files() {
  # Prints paths relative to the order-service directory that differ between two lessons
  diff -rq -x build -x .gradle "$ROOT/$1/order-service" "$ROOT/$2/order-service" 2>/dev/null |
    sed -E "s#^Files $ROOT/$1/order-service/(.*) and .* differ\$#\1#; s#^Only in $ROOT/$2/order-service/?([^:]*): (.*)\$#\1/\2#; s#^Only in $ROOT/$1/order-service/?([^:]*): (.*)\$#REMOVED \1/\2#; s#^/##" || true
}

if [ "${1:-}" = "--check" ]; then
  status=0
  for lesson in $(lessons); do
    previous="$(previous_of "$lesson")"
    [ -z "$previous" ] && continue
    allowed="$(allowed_for "$lesson")"
    changed="$(changed_files "$previous" "$lesson")"
    if [ -z "$changed" ]; then
      echo "FAIL: $lesson is identical to $previous"
      status=1
      continue
    fi
    while IFS= read -r file; do
      case " $allowed " in
        *" $file "*) ;;
        *)
          echo "FAIL: $lesson changes $file, which is not in its allowed list"
          status=1
          ;;
      esac
    done <<< "$changed"
    [ "$status" = "0" ] && echo "ok: $lesson changes only: $(echo "$changed" | tr '\n' ' ')"
  done
  exit "$status"
fi

LESSON="${1:?usage: lesson-diff.sh <lesson-dir> | --check}"
PREVIOUS="$(previous_of "$LESSON")"
if [ -z "$PREVIOUS" ]; then
  echo "$LESSON has no previous lesson with an order service." >&2
  exit 1
fi
echo "Changes from $PREVIOUS to $LESSON:"
diff -ru -x build -x .gradle "$ROOT/$PREVIOUS/order-service" "$ROOT/$LESSON/order-service" || true
