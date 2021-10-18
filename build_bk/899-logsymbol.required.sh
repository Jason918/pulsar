#!/bin/bash
mkdir -p /home/xiaoju/data1/pulsar_bk/logs
rm -rf /home/xiaoju/pulsar_bk/logs
ln -s /home/xiaoju/data1/pulsar_bk/logs /home/xiaoju/pulsar_bk/logs
chown -R xiaoju.xiaoju /home/xiaoju/data1