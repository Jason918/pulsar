#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

if [ -d "/usr/local/jdk1.8.0_65" ]; then 
    export JAVA_HOME=/usr/local/jdk1.8.0_65
    export PATH=$JAVA_HOME/bin:$PATH
fi
export MAVEN_HOME=/home/scmtools/thirdparty/maven-3.6.3
export PATH=$MAVEN_HOME/bin:$PATH

BASEDIR=$(dirname "$0")
CURDIR=`pwd`
echo "CURDIR: $CURDIR"
cd ${BASEDIR}/
echo "cd ${BASEDIR}/"

if [ "X$OE_PIPELINE_NAME" == "X" ]; then
    echo "OE_PIPELINE_NAME is not set"
    exit 1
fi
echo "OE_PIPELINE_NAME: $OE_PIPELINE_NAME"

echo "start build pulsar....."
echo "mvn clean package -DskipTests Pdop,-main -T2C"
mvn clean package -DskipTests -Pdop,-main -T2C
ret=$?
if [ $ret -ne 0 ];then
    echo "===== maven build failure ====="
    exit $ret
else
    echo -n "===== maven build successfully! ====="
fi

OUTPUT_PATH=${CURDIR}/output
mkdir -p ${OUTPUT_PATH}

if [[ $OE_PIPELINE_NAME =~ "broker" ]]; then
    cp ${CURDIR}/build_broker/control.sh ${OUTPUT_PATH}/control.sh
elif [[ $OE_PIPELINE_NAME =~ "bk" ]] || [[ $OE_PIPELINE_NAME =~ "bookeeper" ]] || [[ $OE_PIPELINE_NAME =~ "bookkeeper" ]]; then
    cp ${CURDIR}/build_bk/control.sh ${OUTPUT_PATH}/control.sh
else
    echo "PIPELINE_NAME name must contains string broker or bk(bookeeper/bookkeeper)"
    exit 1
fi

tar -zxf distribution/server/target/apache-pulsar-*-bin.tar.gz -C ${OUTPUT_PATH} --strip-components 1
