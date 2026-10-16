# 홍보 LLM 게이트웨이(promotion-review D26·D29). 주문마다 하나 뜨고 끝나면 지워진다.
# 빌드 맥락은 apps/support-agent 다(gateway/ + src 의 core·deliver). 무엇이 들어가는지는 Dockerfile.dockerignore 가 정한다.
FROM node:22-bookworm-slim@sha256:c3de60bf2f9dd0ac6370e6117950ff62d6e339527e7472301c9c78a017978392

# deliver(D29)가 쓰는 공용 계약(core)은 pydantic 하나에만 기댄다. 러너와 같은 버전으로 고정한다(uv.lock).
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir pydantic==2.13.5 \
    && npm install -g @openai/codex@0.162.1 \
    && npm cache clean --force

RUN useradd --create-home --uid 10010 gateway \
    && mkdir -p /home/gateway/.codex /home/gateway/state \
    && chown gateway:gateway /home/gateway/.codex /home/gateway/state \
    && chmod 700 /home/gateway/.codex /home/gateway/state

COPY gateway/gole_llm_gateway.py gateway/config.toml gateway/entrypoint.sh /opt/gateway/
# 러너 패키지(gole_promotion_agent)는 넣지 않는다 — deliver 는 core 만 쓴다.
COPY src/gole_promotion_core /opt/promotion/gole_promotion_core
COPY src/gole_promotion_deliver /opt/promotion/gole_promotion_deliver
RUN chmod 0755 /opt/gateway/entrypoint.sh

# 로그인(auth.json)은 지인 서버가 이 이미지 위에 굽는다(D28, server/Dockerfile.local).
# 상태 볼륨(gole-gateway-state)이 state 에 붙는다 — 잠금·서버 로그·계정 가드 기록만 두고 비밀은 두지 않는다.
# 볼륨을 이 경로에 처음 붙이면 위에서 만든 소유자를 이어받는다.
ENV PATH=/opt/venv/bin:$PATH \
    PYTHONPATH=/opt/promotion \
    CODEX_HOME=/home/gateway/.codex \
    GOLE_GATEWAY_LOCK=/home/gateway/state/gateway.lock \
    GOLE_GATEWAY_LOG=/home/gateway/state/gateway.log \
    GOLE_GATEWAY_USAGE=/home/gateway/state/gole-llm-gateway-usage.jsonl
USER gateway
WORKDIR /tmp
ENTRYPOINT ["/opt/gateway/entrypoint.sh"]
