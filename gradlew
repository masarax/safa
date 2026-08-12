#!/bin/sh

# Minimal POSIX Gradle wrapper launcher. The wrapper JAR and distribution
# configuration are committed under gradle/wrapper.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
