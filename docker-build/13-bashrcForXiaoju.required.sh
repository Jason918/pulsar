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

id xiaoju || exit 1
grep "stty" /home/xiaoju/.bashrc > /dev/null 2>&1 || echo "
/bin/stty -ctlecho >/dev/null 2>&1
export LC_ALL=en_US.UTF8
export PS1='\[\e]2;\u@\h\a\]\[\e[01;36m\]\u\[\e[01;35m\]@\[\e[01;32m\]\H\[\e[00m\]:\[\e[01;34m\]\w\$\[\e[00m\] '
export JAVA_HOME=/home/xiaoju/jdk-11.0.13
export PATH=\$JAVA_HOME/bin:\$PATH
alias  grep='grep --col'" >> /home/xiaoju/.bashrc
chown xiaoju.xiaoju /home/xiaoju/.bashrc