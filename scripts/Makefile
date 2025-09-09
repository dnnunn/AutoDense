# Makefile
.PHONY: infer-one batch api ui accept

infer-one:
\tpython -m scripts.infer_one --image $(IMG) --out $(OUT) --modality $(MOD)

batch:
\tpython -m scripts.batch_infer --dir $(DIR) --out $(OUT) --modality $(MOD) --retries 1

api:
\tuvicorn autodense.service.api_infer:router --reload

ui:
\tstreamlit run ui/streamlit_app.py

accept:
\tpython -m scripts.acceptance_check --reports-dir $(REPORTS) --out-csv $(OUT)
