FROM registry.xiaojukeji.com/didionline/sre-didi-centos7-base-v2:stable

COPY 13-bashrcForXiaoju.required.sh /etc/container/init/

RUN mkdir /home/xiaoju/dop_tmp
COPY apache-pulsar-dop-*.tar.gz /home/xiaoju/dop_tmp
ADD jdk-*.tar.gz /home/xiaoju/
RUN chown -R xiaoju.xiaoju /home/xiaoju/*