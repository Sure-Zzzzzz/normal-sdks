#!/bin/sh
set -eu

for port in 7000 7001 7002 7003 7004 7005; do
  mkdir -p /tmp/redis-cluster/$port
  redis-server \
    --port $port \
    --dir /tmp/redis-cluster/$port \
    --cluster-enabled yes \
    --cluster-config-file nodes.conf \
    --cluster-node-timeout 5000 \
    --appendonly no \
    --protected-mode no \
    --bind 0.0.0.0 \
    --cluster-announce-ip 127.0.0.1 \
    --cluster-announce-port $port \
    --daemonize yes
done

until redis-cli -p 7000 ping >/dev/null 2>&1; do
  sleep 1
done

if ! redis-cli -p 7000 cluster info 2>/dev/null | grep -q 'cluster_state:ok'; then
  redis-cli --cluster create \
    127.0.0.1:7000 \
    127.0.0.1:7001 \
    127.0.0.1:7002 \
    127.0.0.1:7003 \
    127.0.0.1:7004 \
    127.0.0.1:7005 \
    --cluster-replicas 1 \
    --cluster-yes
fi

tail -f /dev/null
