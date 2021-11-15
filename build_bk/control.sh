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

SERVICE="bookie"
PULSAR_HOME=$(cd $(dirname $0) && pwd -P)
PULSAR_PID_DIR=$PULSAR_HOME
PIDFILE="$PULSAR_PID_DIR/bin/pulsar-${SERVICE}.pid"
PULSAR_LOG_DIR="$PULSAR_HOME/logs"
PULSAR_LOG_CONF=$PULSAR_HOME/conf/log4j2.xml
CONTROL_LOG="$PULSAR_LOG_DIR/control.log"
CONSOLE_OUT_LOG="${PULSAR_LOG_DIR}/console_out.log"
PULSAR_LOG_LEVEL="INFO"
CGROUP_ENABLE="false"

function start() {
    check_pid
    if [ $? -ne 0 ]; then
        echo "pulsar-${SERVICE} is already running, pid=$pid. Stop it first!"
        exit 1
    fi

    backup_logs
    date >> ${CONSOLE_OUT_LOG}

    #define default configs here.
    PULSAR_BOOKKEEPER_CONF="${PULSAR_HOME}/conf/bookkeeper.conf"
    PULSAR_MEM="-Xms10G -Xmx10G -XX:MaxDirectMemorySize=5G"
    PULSAR_GC="-XX:+ParallelRefProcEnabled -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:InitiatingHeapOccupancyPercent=70 -XX:G1HeapRegionSize=32m"
    PULSAR_GC_LOG_FILE="${PULSAR_LOG_DIR}/gc.log"
    PULSAR_GC_LOG="-XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:${PULSAR_GC_LOG_FILE} -XX:+UseGCLogFileRotation
                   -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=200m"


    # SERVICE_NAME: e.g, cproxy-1.binlog.fd.rocketmq.fd.didi.com
    SERVICE_NAME="test"
    if [ -f "${PULSAR_HOME}/.deploy/service.service_name.txt" ]; then
        SERVICE_NAME=$(cat "${PULSAR_HOME}/.deploy/service.service_name.txt")
    elif [ $DIDIENV_ODIN_SERVICE_NAME ];then
        SERVICE_NAME=$DIDIENV_ODIN_SERVICE_NAME
    fi

    # CLUSTER_NAME: e.g, gz01
    CLUSTER_NAME="test"
     if [ -f "${PROXY_HOME}/.deploy/service.cluster.txt" ]; then
        CLUSTER_NAME=$(cat .deploy/service.cluster.txt)
    elif [ $DIDIENV_ODIN_CLUSTER ]; then
        CLUSTER_NAME=$DIDIENV_ODIN_CLUSTER
    fi

    # SERVICE_CLUSTER_NAME: e.g, gz01.cproxy-1.binlog.fd.rocketmq.fd.didi.com
    SERVICE_CLUSTER_NAME=${CLUSTER_NAME}"."${SERVICE_NAME}

    if [[ ${CLUSTER_NAME} == hna-pre* ]]; then
      PULSAR_BOOKKEEPER_CONF="${PULSAR_HOME}/conf/bk_conf/bookkeeper.preview.conf"
    elif [[ ${SERVICE_NAME} == perf.bookkeeper.dop.ddmq.didi.com ]]; then
      PULSAR_BOOKKEEPER_CONF="${PULSAR_HOME}/conf/bk_conf/bookkeeper.perf.conf"
    fi

    #docker machine flag,if it's a docker machine, get real memory and set the flag 1
    DOCKER_MACHINE=0
    #docker machine memory
    DOCKER_MEM=4096
    if [[ $HOSTNAME && $HOSTNAME =~ docker && $DIDIENV_ODIN_INSTANCE_QUOTA_MEM && $DIDIENV_ODIN_INSTANCE_TYPE && $DIDIENV_ODIN_INSTANCE_TYPE == "dd_docker" ]];then
	    DOCKER_MACHINE=1
	    DOCKER_MEM=$DIDIENV_ODIN_INSTANCE_QUOTA_MEM
	    echo "deploy machine is docker container,hostname=$HOSTNAME||memory=$DOCKER_MEM mb"
    fi

   #for docker machine, change default memory limit
    if ((DOCKER_MACHINE==1));then
  	    HEAP_SIZE=$((DOCKER_MEM*4/10))
        DIRECT_MEMORY_SIZE=$((DOCKER_MEM*4/10))
   	    PULSAR_MEM="-Xms${HEAP_SIZE}m -Xmx${HEAP_SIZE}m -XX:MaxDirectMemorySize=${DIRECT_MEMORY_SIZE}m"
    fi

    export PULSAR_LOG_DIR
    export PULSAR_LOG_CONF
    export PULSAR_LOG_LEVEL
    export PULSAR_BOOKKEEPER_CONF
    export PULSAR_MEM
    export PULSAR_GC
    export PULSAR_GC_LOG_FILE
    export PULSAR_GC_LOG

    echo "SERVICE_NAME:" $SERVICE_NAME",   configs:"
    echo "  PULSAR_BOOKKEEPER_CONF:" $PULSAR_BOOKKEEPER_CONF
    echo "  PULSAR_GC:" $PULSAR_GC
    echo "  PULSAR_MEM:" $PULSAR_MEM
    echo "  PULSAR_LOG_DIR:" $PULSAR_LOG_DIR
    echo "  PULSAR_LOG_CONF:" $PULSAR_LOG_CONF
    echo "  PULSAR_LOG_LEVEL:" $PULSAR_LOG_LEVEL


    #添加判读cgroup是否开启的逻辑
    if [ "$CGROUP_ENABLE" == "true" ];then
        CMD_PREFIX="cgexec -g cpu:pulsar-${SERVICE}"
    else
        CMD_PREFIX=""
    fi

    JSTAT_FILE=${PULSAR_HOME}/logs/jstat.log
    nohup ${CMD_PREFIX} bin/pulsar-daemon start ${SERVICE} >> ${CONSOLE_OUT_LOG} 2>&1 &

    #不能太小，否则判断状态有误
    sleep 5
    PID=$(echo $!)
    echo ${PID} > ${PIDFILE}
    date >> ${CONTROL_LOG}
    check_pid
    if [ $? -ne 0 ]; then
        echo "New pulsar-${SERVICE} is running, pid=$pid"
        echo "New pulsar-${SERVICE} is running, pid=$pid" >> ${CONTROL_LOG}
        jstat -gcutil -t $pid 30s >> ${JSTAT_FILE} 2>&1 &
	    #remove old deploy folder
    else
        echo "Start pulsar-${SERVICE} Failed"
        echo "Start pulsar-${SERVICE} Failed" >> ${CONTROL_LOG}
        exit 1
    fi
}

function stop() {
    date >> ${CONTROL_LOG}
    check_pid
    if [ $? -ne 0 ]; then
        echo "Killing pulsar-${SERVICE} =$pid" >> ${CONTROL_LOG}
        pkill -f "jstat -gcutil -t $pid 30s"
        pkill -f hangAlarm.sh
        kill ${pid}
    else
        echo "pulsar-${SERVICE} is already stopped"
    fi
    t=0
    check_pid
    while [[ $? -ne 0 && "$t" -lt 120 ]]; do
        echo "time=$t,killing $pid"
        t=$(($t+1))
        sleep 1
        check_pid
    done
    check_pid
    if [ $? -eq 0 ];then
        echo "" > ${PIDFILE}
        echo "pulsar-${SERVICE} is stopped"
        echo "KILLED" >> ${CONTROL_LOG}
    else
        echo "stop timeout"
        echo "Stop pulsar-${SERVICE} Failed" >> ${CONTROL_LOG}
        exit 1
    fi
}

function backup_logs() {
    #bakup log
    echo "backup log"
    if [ ! -x ${PULSAR_LOG_DIR}/old ]; then
      mkdir -p ${PULSAR_LOG_DIR}/old
    fi

    LOG_SUFFIX=$(date +%Y%m%d-%H%M%S)
    for var in $(ls ${PULSAR_LOG_DIR})
    do
        if [ -f "${PULSAR_LOG_DIR}/${var}" -a "${var}" != "control.log" ]; then
            mv "${PULSAR_LOG_DIR}/${var}" "${PULSAR_LOG_DIR}/old/${var}.${LOG_SUFFIX}"
        fi
    done
}

function get_pid() {
    pid=""
    if [ -f ${PIDFILE} ]; then
        pid=$(cat ${PIDFILE})
    fi
    real_pid=""
    if [ "x_" != "x_${pid}" ]; then
        real_pid=`(ls -l /proc/${pid}/cwd 2>/dev/null| grep "$PULSAR_HOME" &>/dev/null) && echo ${pid}`
    fi
    echo "${real_pid}"
}

function check_pid() {
    pid=$(get_pid)
    if [ "x_" != "x_${pid}" ]; then
        running=$(ps -p ${pid}|grep -v "PID TTY" |wc -l)
        return ${running}
    fi
    return 0
}

function status(){
    check_pid
    local running=$?
    if [ ${running} -ne 0 ];then
        local pid=$(get_pid)
        echo "this pulsar-${SERVICE} is started, pid=${pid}"
    else
        echo "this pulsar-${SERVICE} is stopped"
    fi
    exit 0
}

case "$1" in
    "start")
        start
        exit 0
        ;;
    "stop")
        stop
        exit 0
        ;;
    "reload")
        stop
        start
        exit 0
        ;;
    "status" )
        # 检查服务
        status
        ;;
    *)
        echo "supporting cmd: start/stop/reload/status"
        exit 1
        ;;
esac

