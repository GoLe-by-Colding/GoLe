"""기존 import 경로를 유지하는 호환 facade. 실행 구현은 역할별 모듈에 둔다."""
from gole_brick_filter.brain import BrickState, build_graph as _build_graph
from gole_brick_filter.hands import OpenAIEditor, sanitize_image
from gole_brick_filter.policy import MAX_INPUT, MAX_OUTPUT, MAX_PIXELS, PROMPTS
from gole_brick_filter.ports import Editor


def build_graph(editor: Editor, check_active=lambda: None):
    return _build_graph(editor, check_active, sanitize_image)
