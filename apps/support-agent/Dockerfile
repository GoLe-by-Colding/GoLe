FROM python:3.13-slim@sha256:9d2e5553305c7c7b0097999bb17187c69b921ccd6bc9d40e4bb5ebe652c00285

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"

WORKDIR /app
RUN pip install --no-cache-dir uv==0.10.2
COPY apps/support-agent/pyproject.toml apps/support-agent/uv.lock ./
RUN uv sync --locked --no-dev

# 이 이미지는 상주 엔트리포인트(gole_support_agent.server)가 쓰는 것만 담는다.
# AgentJobs·BrickImages 계약은 각각 보류·미배포 패키지의 것이라 생성하지 않는다.
COPY apps/api/src/main/proto /contracts
RUN mkdir -p /app/generated && \
    uv run --no-sync python -m grpc_tools.protoc \
      -I/contracts \
      --python_out=/app/generated \
      --grpc_python_out=/app/generated \
      /contracts/gole/support/v1/support_agent.proto

# 패키지를 골라 복사한다. `src` 를 통째로 복사하면 돌지도 않는 워커·사진·홍보 코드가
# 운영 컨테이너에 실려 나간다. 의존 방향이 단방향이라 이 둘만으로 엔트리포인트가 선다 —
# gole_agent_worker 가 gole_support_agent 를 쓰지 그 반대가 아니다.
# 보류를 푸는 날 되돌릴 곳이 여기다(apps/support-agent/README.md "영속 작업자" 절).
COPY apps/support-agent/src/gole_support_agent ./src/gole_support_agent
COPY apps/support-agent/src/gole_agent_runtime ./src/gole_agent_runtime
ENV PYTHONPATH="/app/src:/app/generated"

RUN useradd --system --uid 10002 --create-home support-agent && \
    chown -R support-agent:support-agent /app
USER support-agent

EXPOSE 50051
HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=10s \
  CMD ["python", "-m", "gole_support_agent.healthcheck"]
ENTRYPOINT ["python", "-m", "gole_support_agent.server"]
