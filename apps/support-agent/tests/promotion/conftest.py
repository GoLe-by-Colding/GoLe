"""홍보 테스트 공용 fixture."""

from pathlib import Path

import pytest

from .gitrepo import git


@pytest.fixture
def repo(tmp_path: Path) -> Path:
    root = tmp_path / "repo"
    root.mkdir()
    git(root, "init", "-q", "-b", "main")
    return root
