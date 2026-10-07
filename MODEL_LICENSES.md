# Bundled local OCR LLM

EmreShots bundles `SmolLM2-135M-Instruct-Q2_K.gguf` in release APKs for offline OCR cleanup, title generation, and tag generation.

- Model: SmolLM2-135M-Instruct, 135M parameters, Q2_K GGUF
- Source: https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF
- File size: 88.2 MB
- SHA-256: `741ad12b64088fedc17c33aacb22e48be1972ef36a39f03666dd68bd15614fb9`
- License: Apache License 2.0
- Runtime: `llamacpp-kotlin` / llama.cpp

The model is downloaded at build time from the pinned Hugging Face file URL and verified by SHA-256 before being placed under `app/src/main/assets/models/`. The runtime copies it once into app-private storage; OCR enrichment itself does not require network access.

See the upstream model card and Apache 2.0 license for full terms.
