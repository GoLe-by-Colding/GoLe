from __future__ import annotations

import hmac
import json
from concurrent import futures

import grpc

from gole.agent.v1 import agent_jobs_pb2 as pb, agent_jobs_pb2_grpc as rpc
from gole_agent_worker.contracts import Conflict, NotFound, Purged, Submission
from gole_agent_worker.runtime.jobs import JobService
from gole_agent_worker.runtime.store import Store


class AgentJobsService(rpc.AgentJobsServicer):
    def __init__(self, store: Store, *, caller: str, token: str, openai_enabled=False):
        if not caller or len(token) < 32:
            raise ValueError("INTERNAL_AUTH_CONFIGURATION_REQUIRED")
        self.store, self.caller, self.token = store, caller, token
        self.openai_enabled = openai_enabled
        self.jobs = JobService(store, openai_enabled=openai_enabled)

    def authenticate(self, context):
        metadata = list(context.invocation_metadata())
        callers = [v for k, v in metadata if k == "x-gole-caller"]
        tokens = [v for k, v in metadata if k == "authorization"]
        if len(callers) != 1 or len(tokens) != 1:
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "INTERNAL_AUTH_REQUIRED")
        if not (hmac.compare_digest(callers[0].encode(), self.caller.encode())
                and hmac.compare_digest(tokens[0].encode(), ("Bearer " + self.token).encode())):
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "INTERNAL_AUTH_REQUIRED")
        return self.caller

    def Submit(self, request, context):
        caller = self.authenticate(context)
        try:
            if len(request.payload_json.encode()) > 12_000:
                raise ValueError()
            submission = Submission(request.owner_id, request.idempotency_key,
                                    request.authorization_ref, request.kind,
                                    json.loads(request.payload_json), request.provider,
                                    request.allow_external)
            return response(self.jobs.submit(caller, submission))
        except (ValueError, TypeError, RecursionError):
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "INVALID_SUBMISSION")
        except Purged:
            context.abort(grpc.StatusCode.FAILED_PRECONDITION, "JOB_PURGED")
        except Conflict:
            context.abort(grpc.StatusCode.ALREADY_EXISTS, "IDEMPOTENCY_PAYLOAD_CONFLICT")

    def _job(self, request, context, cancel=False):
        caller = self.authenticate(context)
        try:
            action = self.jobs.cancel if cancel else self.jobs.get
            return response(action(caller, request.owner_id, request.job_id))
        except ValueError:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "INVALID_IDENTIFIER")
        except NotFound:
            context.abort(grpc.StatusCode.NOT_FOUND, "JOB_NOT_FOUND")

    def Purge(self, request, context):
        caller = self.authenticate(context)
        try:
            receipt = self.jobs.purge(caller, request.owner_id, request.idempotency_key)
            return pb.PurgeJobReceipt(receipt_id=receipt["receipt_id"],
                                      purged_at_ms=int(receipt["purged_at"] * 1000))
        except ValueError:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "INVALID_IDENTIFIER")
        except NotFound:
            context.abort(grpc.StatusCode.NOT_FOUND, "JOB_NOT_FOUND")

    def Get(self, request, context):
        return self._job(request, context)

    def Cancel(self, request, context):
        return self._job(request, context, cancel=True)


def response(job):
    return pb.Job(job_id=job["id"], state=pb.JobState.Value(job["state"]),
                  attempts=job["attempts"], result_json=job["result"], error_code=job["error"],
                  created_at_ms=int(job["created_at"] * 1000),
                  updated_at_ms=int(job["updated_at"] * 1000), human_review_required=True)


def create_server(service, port=50052):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4), options=(
        ("grpc.max_receive_message_length", 32 * 1024),
        ("grpc.max_send_message_length", 32 * 1024),
    ))
    rpc.add_AgentJobsServicer_to_server(service, server)
    bound = server.add_insecure_port(f"127.0.0.1:{port}")
    if not bound:
        raise RuntimeError("INTERNAL_BIND_FAILED")
    return server, bound


