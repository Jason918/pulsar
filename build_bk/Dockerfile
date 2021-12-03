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

FROM registry-v3.intra.xiaojukeji.com/didionline/dop-pulsar:stable

COPY 899-logsymbol.required.sh  /etc/container/init/
COPY 990-startservice.required.sh  /etc/container/init/

ENV DEPLOY_DISF_NOT_NEED_START 1
ENV DEPLOY_APOLLO_NOT_NEED_START 1

RUN mkdir /home/xiaoju/pulsar_bk/
RUN ls -alh
RUN tar -zxf /home/xiaoju/dop_tmp/apache-pulsar-*-bin.tar.gz -C /home/xiaoju/pulsar_bk/ --strip-components 1
RUN rm -rf /home/xiaoju/dop_tmp
COPY control.sh /home/xiaoju/pulsar_bk/control.sh
