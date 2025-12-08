#!/usr/bin/env sh
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
CLASSPATH=`dirname "$0"`/gradle/wrapper/gradle-wrapper.jar
JAVA_OPTS="-Xmx64m -Xms64m"
exec java $JAVA_OPTS -jar "$CLASSPATH" "$@"
