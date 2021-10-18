FROM registry.xiaojukeji.com/didionline/dop-pulsar:stable

COPY ./899-logsymbol.required.sh  /etc/container/init/
COPY ./990-startservice.required.sh  /etc/container/init/

ENV DEPLOY_DISF_NOT_NEED_START 1
ENV DEPLOY_APOLLO_NOT_NEED_START 1

WORKDIR /home/xiaoju
RUN mkdir ./pulsar_bk/
RUN ls -alh
RUN tar -zxf ./dop_tmp/apache-pulsar-*-bin.tar.gz -C ./pulsar_bk/ --strip-components 1
COPY ./control.sh ./pulsar_bk/control.sh