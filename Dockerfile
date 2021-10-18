FROM registry.xiaojukeji.com/didionline/sre-didi-centos7-base-v2:stable

ENV DEPLOY_DISF_NOT_NEED_START 1
ENV DEPLOY_APOLLO_NOT_NEED_START 1

COPY ./899-logsymbol.required.sh  /etc/container/init/
COPY ./990-startservice.required.sh  /etc/container/init/

# make dir for OE
RUN mkdir -p /home/xiaoju/data1/pulsar_bk/logs
RUN rm -rf /home/xiaoju/pulsar_bk/logs
RUN if [ ! -d "/home/xiaoju/pulsar_bk/" ];then \
    mkdir -p "/home/xiaoju/pulsar_bk/"; \
fi

RUN ln -s /home/xiaoju/data1/pulsar_bk/logs /home/xiaoju/pulsar_bk/logs

RUN chown -R xiaoju.xiaoju /home/xiaoju/data1

RUN chown -R xiaoju.xiaoju /home/xiaoju/pulsar_bk
RUN rsync -av --exclude ./Dockerfile ./* /home/xiaoju/pulsar_bk/