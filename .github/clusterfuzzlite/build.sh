#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$SRC/rsql-jpa-search"
FUZZER_DIR="$PROJECT_DIR/integration/src/fuzz/java"

cd "$PROJECT_DIR"

./mvnw -B -ntp -nsu -DskipTests install
./mvnw -B -ntp -nsu -pl integration -DskipTests dependency:copy-dependencies \
  -DincludeScope=test \
  -DoutputDirectory="$OUT/lib"

mkdir -p "$OUT/classes" "$OUT/lib"

find api rsql-spi core jpa-validation perplexhub spring-boot-starter \
  -path "*/target/*.jar" \
  ! -name "*-tests.jar" \
  -exec cp {} "$OUT/lib/" \;

cp /usr/local/bin/jazzer_driver /usr/local/bin/jazzer_agent_deploy.jar "$OUT/"

BUILD_CLASSPATH="$OUT/lib/*"

for fuzzer in "$FUZZER_DIR"/*Fuzzer.java; do
  fuzzer_basename="$(basename "$fuzzer" .java)"
  javac -cp "$BUILD_CLASSPATH" -d "$OUT/classes" "$fuzzer"

  cat > "$OUT/$fuzzer_basename" <<EOF
#!/bin/sh
# LLVMFuzzerTestOneInput for ClusterFuzzLite fuzzer detection.
this_dir=\$(dirname "\$0")
runtime_cp="\$this_dir/classes"
for jar in "\$this_dir"/lib/*.jar; do
  runtime_cp="\$runtime_cp:\$jar"
done
LD_LIBRARY_PATH="\$JVM_LD_LIBRARY_PATH:\$this_dir" \\
  "\$this_dir/jazzer_driver" \\
  --agent_path="\$this_dir/jazzer_agent_deploy.jar" \\
  --cp="\$runtime_cp" \\
  --target_class="$fuzzer_basename" \\
  --jvm_args="-Xmx2048m:-Djava.awt.headless=true" \\
  "\$@"
EOF
  chmod +x "$OUT/$fuzzer_basename"
done
