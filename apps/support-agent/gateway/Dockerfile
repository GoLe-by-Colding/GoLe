# 홍보 LLM 게이트웨이(promotion-review D26). 요청마다 `docker run --rm -i` 로 하나 뜨고 끝나면 지워진다.
# 빌드 맥락은 apps/support-agent/gateway 다.
FROM node:22-bookworm-slim@sha256:c3de60bf2f9dd0ac6370e6117950ff62d6e339527e7472301c9c78a017978392

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g @openai/codex@0.162.1 \
    && npm cache clean --force

RUN useradd --create-home --uid 10010 gateway \
    && mkdir -p /home/gateway/.codex \
    && chown gateway:gateway /home/gateway/.codex \
    && chmod 700 /home/gateway/.codex

COPY gole_llm_gateway.py config.toml entrypoint.sh /opt/gateway/
RUN chmod 0755 /opt/gateway/entrypoint.sh

# 볼륨이 이 자리에 붙는다. 로그인·잠금·서버 로그만 여기 남는다.
ENV CODEX_HOME=/home/gateway/.codex \
    GOLE_GATEWAY_LOCK=/home/gateway/.codex/gateway.lock \
    GOLE_GATEWAY_LOG=/home/gateway/.codex/gateway.log
USER gateway
WORKDIR /tmp
ENTRYPOINT ["/opt/gateway/entrypoint.sh"]
