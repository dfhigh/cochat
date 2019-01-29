#!/usr/bin/env bash
cur=$(cd `dirname $0`/..; pwd)
cd $cur

mkdir -p tmp
mkdir -p data

JAVA_ARGS="-Dlight-4j-config-dir=$cur/conf -Dlog4j.configurationFile=$cur/conf/log4j2.xml -Dconfig.location=$cur/conf/cochat.yml"
JAVA_CMD="java $JAVA_ARGS -cp lib/cochat*.jar:lib/* org.mib.cochat.rest.CochatApp"

echo "starting service:"
echo $JAVA_CMD
$JAVA_CMD