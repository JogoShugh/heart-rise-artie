#!/bin/sh
# Builds the Spring HATEOAS clone report from the runs in this folder (README of the steps: "Run it again" on
# proof.html). Run from the repository root, with the example's container and the clone still up, after:
#   SPRING_CLONE_OUT=$PWD/docs/reports/spring-hateoas-clone ./gradlew -p heart :heart-sims:test --tests '*SpringCloneTrace*'
#   artie run wild/fresh-run/explore.kts --dialect hal-forms --trace …/artie-real.yaml --exchanges
#   artie run wild/fresh-run/explore.kts entry=http://localhost:8096/employees --dialect hal-forms --trace …/artie-clone.yaml --exchanges
set -eu
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
assets="$root/docs/reports/assets"
example="$root/wild/fresh-run/spring-hateoas-examples"
java="$example/affordances/src/main/java/org/springframework/hateoas/examples"
clone="$root/heart/heart-sims/src/testFixtures/kotlin/com/artie/heart/sims/employees/StockSpring.kt"
harness="$root/heart/heart-sims/src/test/kotlin/com/artie/heart/sims/employees/SpringCloneTrace.kt"

sh "$assets/build-run-report.sh" "$here/trace.yaml" "$here/side.html" "The Clone and Spring's Example, Side by Side" \
  "Each request sent to the clone and to Spring HATEOAS's own affordances example, run untouched; each comparison is the example's own answer."
sh "$assets/build-script-report.sh" "$here/artie-real.yaml" "$here/artie-real.html" "ARTIE on Spring's Example" \
  "wild/fresh-run/explore.kts, run by ARTIE in HAL-FORMS against Spring HATEOAS's affordances example at commit c100c56, untouched." \
  "$root/wild/fresh-run/explore.kts"
sh "$assets/build-script-report.sh" "$here/artie-clone.yaml" "$here/artie-clone.html" "ARTIE on the Clone" \
  "The same script, unchanged, against the clone: only its entry differs." "$root/wild/fresh-run/explore.kts"

# The facts, each read where it is, never typed in.
escape() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' "$1"; }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
escape "$java/EmployeeController.java" > "$tmp/CONTROLLER"
escape "$java/HypermediaConfiguration.java" > "$tmp/CONFIG"
escape "$java/DatabaseLoader.java" > "$tmp/LOADER"
escape "$clone" > "$tmp/CLONE"
escape "$harness" > "$tmp/HARNESS"
cat "$assets/kotlin-view.js" > "$tmp/KOTLIN_VIEW"
sed -n '/^# Run again/,$p' "$0" | sed '1d; s/^# \{0,1\}//' > "$tmp/RERUN"
printf '%s' "$(git -C "$example" log -1 --format='%H, %ad, "%s"' --date=short)" > "$tmp/COMMIT"
git -C "$example" status --short | wc -l | tr -d ' \n' > "$tmp/DIRTY"
sed -n '/spring-boot-starter-parent/{n;s/.*<version>\(.*\)<\/version>.*/\1/p;}' "$example/pom.xml" | tr -d '\n' > "$tmp/BOOT"
docker exec hal-forms-fresh sh -c 'ls /root/.m2/repository/org/springframework/hateoas/spring-hateoas/' | tr -d '\n' > "$tmp/HATEOAS"
docker exec hal-forms-fresh sh -c 'java -version 2>&1 | head -1' | tr -d '\n' > "$tmp/JAVA"
docker inspect hal-forms-fresh --format '{{.Config.Image}}' | tr -d '\n' > "$tmp/IMAGE"
docker inspect hal-forms-fresh --format '{{join .Config.Cmd " "}}' | sed 's/^sh -c //' | tr -d '\n' > "$tmp/CMD"
git -C "$root" log -1 --format=%h -- "$clone" | tr -d '\n' > "$tmp/OUR_COMMIT"
grep -c '^- at:' "$here/trace.yaml" | tr -d '\n' > "$tmp/SIDE_STEPS"
sed -n 's/^- at: "\([0-9-]*\)T.*/\1/p' "$here/trace.yaml" | head -1 | tr -d '\n' > "$tmp/RUN_DATE"

awk -v dir="$tmp" '{
  while (match($0, /@@[A-Z_]+@@/)) {
    key = substr($0, RSTART + 2, RLENGTH - 4)
    file = dir "/" key; text = ""; first = 1
    while ((getline line < file) > 0) { text = text (first ? "" : "\n") line; first = 0 }
    close(file)
    $0 = substr($0, 1, RSTART - 1) text substr($0, RSTART + RLENGTH)
  }
  print
}' "$here/proof.src.html" > "$here/proof.html"
echo "wrote proof.html"
exit 0

# Run again
# The example, untouched, in its container (first time: docker run as the 2026-11-02 log says):
#   docker restart hal-forms-fresh        # http://localhost:8097
# The clone:
#   ./gradlew -p heart :heart-sims:installSims
#   java -cp 'heart/heart-sims/build/sims/lib/*' com.artie.heart.sims.MainKt 8096 employees.test
# Side by side (it resets the clone and switches it to answer as Spring's example):
#   SPRING_CLONE_OUT=$PWD/docs/reports/spring-hateoas-clone ./gradlew -p heart :heart-sims:test --tests '*SpringCloneTrace*' --rerun
# ARTIE on each, both started fresh (restart the container; POST /_sim/reset and PUT /_sim/employees/mode stock-spring):
#   artie run wild/fresh-run/explore.kts --dialect hal-forms --trace docs/reports/spring-hateoas-clone/artie-real.yaml --exchanges
#   artie run wild/fresh-run/explore.kts entry=http://localhost:8096/employees --dialect hal-forms --trace docs/reports/spring-hateoas-clone/artie-clone.yaml --exchanges
# Then: sh docs/reports/spring-hateoas-clone/build.sh
