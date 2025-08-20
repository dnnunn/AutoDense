#!/usr/bin/env bash
set -euo pipefail
rm -rf build && mkdir -p build

echo "[AutoDense] Cloning llama.cpp..."
git clone https://github.com/ggerganov/llama.cpp.git build/llama.cpp
pushd build/llama.cpp >/dev/null
make clean
# Build arm64
make LLAMA_SERVER=1 LLAMA_OPENMP=1 -j10
mv bin/llama-server ../../llama-server-arm64
make clean
# Build x86_64
CFLAGS="-arch x86_64" CXXFLAGS="-arch x86_64" LDFLAGS="-arch x86_64" \
make LLAMA_SERVER=1 LLAMA_OPENMP=1 -j10
mv bin/llama-server ../../llama-server-x86_64
popd >/dev/null
lipo -create -output llama-server-universal llama-server-arm64 llama-server-x86_64
mkdir -p ../resources/Fiji.app/Contents/Resources/bin
mv llama-server-universal ../resources/Fiji.app/Contents/Resources/bin/llama-server
chmod +x ../resources/Fiji.app/Contents/Resources/bin/llama-server
