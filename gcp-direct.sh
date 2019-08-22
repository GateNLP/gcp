#!/usr/bin/env bash

## NOTE: this script includes the current directory in the Java classpath before
## any other location is included. This allows e.g. to override the default
## log4j.properties settings from GCP_HOME/conf with a log4j.properties file
## in the current directory.

if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME not set.  Please set it to point to your Java installation"
  exit 1
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Cannot find java executable at $JAVA_HOME/bin/java."
  echo "Please check your JAVA_HOME setting."
  exit 1
fi

PRG="$0"
CURDIR="`pwd`"
# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/$link"
  fi
done
SCRIPTDIR=`dirname "$PRG"`
SCRIPTDIR=`cd "$SCRIPTDIR"; pwd -P`

## Process the command line arguments so we can distinguish between those
## for the Java VM and those for the BatchRunner itself
gcpparams=()
jvmparams=()

# Build classpath
GCP_CLASSPATH=.:"${SCRIPTDIR}"/conf
if [ -f "${SCRIPTDIR}/gcp.jar" ]; then
  # Running from an SVN checkout, we need a GATE_HOME and to include GATE_HOME libs
  if [ "${GATE_HOME}" == "" ] 
  then
    echo environment variable GATE_HOME not set, cannot proceed
    exit 1
  fi
  GCP_CLASSPATH="${GCP_CLASSPATH}":"${SCRIPTDIR}"/gcp.jar:"${SCRIPTDIR}"/'lib/*':"$GATE_HOME"/bin/gate.jar:"$GATE_HOME"/'lib/*'
else
  # Running from a distro, so all the GATE libs are in our lib
  GCP_CLASSPATH="${GCP_CLASSPATH}":"${SCRIPTDIR}"/'lib/*'
fi

# Pass on GATE_HOME if set
if [ "${GATE_HOME}" != "" ]; then
  jvmparams=( -Dgate.home="${GATE_HOME}" )
fi

while test "$1" != "";
do
if [ "$1" == "-h" ]
then
    gcpparams=( "${gcpparams[@]}" $1 )
    cat <<EOF
Run GCP
All options starting with -X or -D will be passed on to the "java" command, for example:
  -Djava.io.tmpdir=<somedir>
  -Xmx<memorysize>
All other arguments will be passed to the program. The program can be invoked in two
ways:
1) giving it the number of threads and a config file (GCP-CLI mode)
2) giving it more flexible arguments:
EOF
else 
  if [[ "$1" =~ -D.* ]] || [[ "$1" =~ -X.* ]] 
  then 
    jvmparams=( "${jvmparams[@]}" $1 )
  else 
    gcpparams=( "${gcpparams[@]}" $1 )
  fi
fi
shift
done
echo JVM parameters used ${jvmparams[@]}
echo GCP parameters used ${gcpparams[@]}
"$JAVA_HOME/bin/java" -Dgcp.home="${SCRIPTDIR}" -Djava.protocol.handler.pkgs=gate.cloud.util.protocols -cp "${GCP_CLASSPATH}" "${jvmparams[@]}" gate.cloud.batch.BatchRunner "${gcpparams[@]}"
