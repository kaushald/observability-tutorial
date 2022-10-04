export OTEL_METRICS_EXPORTER="none"
export OTEL_EXPORTER_OTLP_ENDPOINT="https://api.honeycomb.io:443"
export OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}"
export OTEL_SERVICE_NAME="java-jokes"

cd java-jokes || exit
gradle bootJar
java -javaagent:../../lib/opentelemetry-javaagent.jar -jar build/libs/java-jokes.jar