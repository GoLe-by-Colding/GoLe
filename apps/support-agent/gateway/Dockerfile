# 홍보 LLM 게이트웨이(promotion-review D26). 요청마다 `docker run --rm -i` 로 하나 뜨고 끝나면 지워진다.
# 빌드 맥락은 apps/support-agent/gateway 다.
FROM node:22-bookworm-slim@sha256:c3de60bf2f9dd0ac6370e6117950ff62d6e339527e7472301c9c78a017978392

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g @openai/codex@0.162.1 \
    && npm cache clean --force

RUN useradd --create-home --uid 10010 gateway \
    && mkdir -p /home/gateway/.codex /home/gateway/state \
    && chown gateway:gateway /home/gateway/.codex /home/gateway/state \
    && chmod 700 /home/gateway/.codex /home/gateway/state

COPY gole_llm_gateway.py config.toml entrypoint.sh /opt/gateway/
RUN chmod 0755 /opt/gateway/entrypoint.sh

# 로그인(auth.json)은 지인 서버가 이 이미지 위에 굽는다(D28, server/Dockerfile.local).
# 상태 볼륨(gole-gateway-state)이 state 에 붙는다 — 잠금·서버 로그·계정 가드 기록만 두고 비밀은 두지 않는다.
# 볼륨을 이 경로에 처음 붙이면 위에서 만든 소유자를 이어받는다.
ENV CODEX_HOME=/home/gateway/.codex \
    GOLE_GATEWAY_LOCK=/home/gateway/state/gateway.lock \
    GOLE_GATEWAY_LOG=/home/gateway/state/gateway.log \
    GOLE_GATEWAY_USAGE=/home/gateway/state/gole-llm-gateway-usage.jsonl
USER gateway
WORKDIR /tmp
ENTRYPOINT ["/opt/gateway/entrypoint.sh"]
