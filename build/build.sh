#!/bin/bash

if [ -d "/usr/local/jdk1.8.0_65" ]; then 
    export JAVA_HOME=/usr/local/jdk1.8.0_65
    export PATH=$JAVA_HOME/bin:$PATH
fi
export MAVEN_HOME=/home/scmtools/thirdparty/maven-3.6.3
export PATH=$MAVEN_HOME/bin:$PATH

BASEDIR=$(dirname "$0")
CURDIR=`pwd`
cd ${BASEDIR}/..

echo "mvn clean package -U -DskipTests -Pcore-modules,-main -T2C"
mvn clean package -U -DskipTests -Pcore-modules,-main -T2C
ret=$?
if [ $ret -ne 0 ];then
    echo "===== maven build failure ====="
    exit $ret
else
    echo -n "===== maven build successfully! ====="
fi

OUTPUT_PATH=${CURDIR}/output
mkdir -p ${OUTPUT_PATH}
cp ${CURDIR}/control.sh ${OUTPUT_PATH}/control.sh
tar -zxf distribution/server/target/apache-pulsar-*-bin.tar.gz -C ${OUTPUT_PATH} --strip-components 1



