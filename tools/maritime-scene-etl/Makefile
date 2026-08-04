PYTHON ?= $(shell if command -v python3.13 >/dev/null 2>&1; then command -v python3.13; elif test -x /opt/homebrew/opt/python@3.13/bin/python3.13; then echo /opt/homebrew/opt/python@3.13/bin/python3.13; else command -v python3; fi)
VENV ?= .venv

.PHONY: venv test doctor list-datasets

venv:
	$(PYTHON) -m venv $(VENV)
	$(VENV)/bin/python -m pip install --upgrade pip
	$(VENV)/bin/python -m pip install -r requirements.lock

test:
	$(VENV)/bin/python -m pytest

doctor:
	$(VENV)/bin/python -m scene_etl.cli doctor

list-datasets:
	$(VENV)/bin/python -m scene_etl.cli list-datasets
