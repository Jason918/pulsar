#!/bin/bash
id xiaoju || exit 1
grep "stty" /home/xiaoju/.bashrc > /dev/null 2>&1 || echo "
/bin/stty -ctlecho >/dev/null 2>&1
export LC_ALL=en_US.UTF8
export PS1='\[\e]2;\u@\h\a\]\[\e[01;36m\]\u\[\e[01;35m\]@\[\e[01;32m\]\H\[\e[00m\]:\[\e[01;34m\]\w\$\[\e[00m\] '
export JAVA_HOME=/home/xiaoju/jdk-11.0.13
export PATH=\$JAVA_HOME/bin:\$PATH
alias  grep='grep --col'" >> /home/xiaoju/.bashrc
chown xiaoju.xiaoju /home/xiaoju/.bashrc