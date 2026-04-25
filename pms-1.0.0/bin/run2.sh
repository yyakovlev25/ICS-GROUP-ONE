#!/usr/bin/env sh
set -eu

# Set Java binary for Java 21
# JAVA_CMD="/path/to/java"
# JAVA_HOME="/path/to/jre"
# per default the script will use the java version included in the path

APP_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
APP_JAR="$APP_DIR/pms-1.0.0.jar"

# Build classpath: app jar + all libs
CP="$APP_JAR:$APP_DIR/lib/*"

# If you need memory settings:
# JAVA_OPTS="-Xms256m -Xmx512m"
JAVA_OPTS="${JAVA_OPTS:-}"


# 1) If JAVA_CMD is set, use it
if [ -n "${JAVA_CMD:-}" ]; then
  JAVA="$JAVA_CMD"

# 2) Else, if JAVA_HOME is set, use that
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"

# 3) Else, if you bundle a runtime under ./jre, use it (optional)
elif [ -x "$APP_DIR/jre/bin/java" ]; then
  JAVA="$APP_DIR/jre/bin/java"

# 4) Else fallback to java on PATH
else
  JAVA="java"
fi

# print java version 
"$JAVA" -version

# start application
exec "$JAVA" $JAVA_OPTS -cp "$CP" de.mbg.pms.ServerMain2 "$@"