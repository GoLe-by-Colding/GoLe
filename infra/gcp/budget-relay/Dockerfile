FROM python:3.13-alpine@sha256:7415fbc3c9e4979cc717d92377ab2bc7b2b4a2af1ac03cc52b5f3f88efedaf3a

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    BUDGET_STATE_DIR=/state

RUN addgroup -S -g 10001 gole && adduser -S -G gole -u 10001 gole \
    && mkdir -p /app /state \
    && chown -R gole:gole /app /state

COPY --chown=gole:gole budget_relay.py /app/budget_relay.py

USER gole
WORKDIR /app
VOLUME ["/state"]

CMD ["python", "/app/budget_relay.py"]
