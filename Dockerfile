FROM registry.xiaojukeji.com/didionline/sre-didi-centos7-base-v2:stable

RUN mkdir /home/xiaoju/dop_tmp
COPY apache-pulsar-dop-*.tar.gz /home/xiaoju/dop_tmp
RUN chown -R xiaoju.xiaoju /home/xiaoju/dop_tmp