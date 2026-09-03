#!/bin/sh
CLASSPATH=$( cd "$(dirname "$0")" && pwd )/gradle/wrapper/gradle-wrapper.jar
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
