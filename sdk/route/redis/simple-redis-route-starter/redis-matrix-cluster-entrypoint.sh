#!/bin/sh
# Redis 多版本矩阵 Cluster 启动脚本
# 参数：$1=Redis版本（3.2.12/5.0.14/7.2.6），$2=起始端口（17000/17010/17020）
set -eu

VERSION="${1:-7.2.6}"
BASE_PORT="${2:-17000}"
MAJOR=$(echo "$VERSION" | cut -d. -f1)

P0=$BASE_PORT
P1=$((BASE_PORT + 1))
P2=$((BASE_PORT + 2))
P3=$((BASE_PORT + 3))
P4=$((BASE_PORT + 4))
P5=$((BASE_PORT + 5))

# 启动 6 个节点
for port in $P0 $P1 $P2 $P3 $P4 $P5; do
  mkdir -p /tmp/redis-cluster/$port
  announce_args=""
  if [ "$MAJOR" -ge 5 ]; then
    announce_args="--cluster-announce-ip 127.0.0.1 --cluster-announce-port $port"
  fi
  redis-server \
    --port $port \
    --dir /tmp/redis-cluster/$port \
    --cluster-enabled yes \
    --cluster-config-file nodes.conf \
    --cluster-node-timeout 5000 \
    --appendonly no \
    --protected-mode no \
    --bind 0.0.0.0 \
    $announce_args \
    --daemonize yes
done

# 等待第一个节点就绪
until redis-cli -p $P0 ping >/dev/null 2>&1; do
  sleep 1
done

# 判断集群是否已初始化
if redis-cli -p $P0 cluster info 2>/dev/null | grep -q 'cluster_state:ok'; then
  echo "Cluster 已初始化，跳过创建"
  tail -f /dev/null
  exit 0
fi

# Redis 5.0+ 支持 redis-cli --cluster create
MAJOR=$(echo "$VERSION" | cut -d. -f1)
if [ "$MAJOR" -ge 5 ]; then
  redis-cli --cluster create \
    127.0.0.1:$P0 \
    127.0.0.1:$P1 \
    127.0.0.1:$P2 \
    127.0.0.1:$P3 \
    127.0.0.1:$P4 \
    127.0.0.1:$P5 \
    --cluster-replicas 1 \
    --cluster-yes
else
  # Redis 3.x 手动 CLUSTER MEET + ADDSLOTS（无 --cluster 子命令）
  redis-cli -p $P1 cluster meet 127.0.0.1 $P0
  redis-cli -p $P2 cluster meet 127.0.0.1 $P0
  redis-cli -p $P3 cluster meet 127.0.0.1 $P0
  redis-cli -p $P4 cluster meet 127.0.0.1 $P0
  redis-cli -p $P5 cluster meet 127.0.0.1 $P0

  sleep 2

  # 分配 16384 个 slot：前 3 个节点各约 5461 个
  redis-cli -p $P0 cluster addslots $(seq -s ' ' 0 5460)
  redis-cli -p $P1 cluster addslots $(seq -s ' ' 5461 10921)
  redis-cli -p $P2 cluster addslots $(seq -s ' ' 10922 16383)

  # 后 3 个节点设为副本
  NODE0_ID=$(redis-cli -p $P0 cluster myid)
  NODE1_ID=$(redis-cli -p $P1 cluster myid)
  NODE2_ID=$(redis-cli -p $P2 cluster myid)
  redis-cli -p $P3 cluster replicate $NODE0_ID
  redis-cli -p $P4 cluster replicate $NODE1_ID
  redis-cli -p $P5 cluster replicate $NODE2_ID
fi

echo "Cluster 初始化完成，version=$VERSION，base_port=$BASE_PORT"
tail -f /dev/null
