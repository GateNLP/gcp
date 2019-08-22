#!/usr/bin/env bash
#
# Shell script to run the GATE Cloud Paralleliser
#

if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME not set.  Please set it to point to your Java installation"
  exit 1
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Cannot find java executable at $JAVA_HOME/bin/java."
  echo "Please check your JAVA_HOME setting."
  exit 1
fi

# classpath
BASEDIR="`dirname $0`"

# Command line processing - -J options pass through to the gcp-cli JVM, other
# options are parameters to the CLI.  So -J-Dfoo=bar sets a system property in
# the CLI JVM, whereas -Dfoo=bar sets one for the target BatchRunner
while [ "${1:0:2}" = "-J" ]; do
  JAVA_OPTS="$JAVA_OPTS ${1#-J}"
  shift
done

exec "$JAVA_HOME/bin/java" $JAVA_OPTS -jar "$BASEDIR/gcp-cli.jar" "$@"
