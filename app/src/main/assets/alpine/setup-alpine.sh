#!/bin/sh

set -e

echo "[1/5] Setting DNS..."
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf

echo "[2/5] Trusting CrestCode Repository Key..."
mkdir -p /etc/apk/keys
if [ -f "$PREFIX/crest@crestcode.rsa.pub" ]; then
    cp -f "$PREFIX/crest@crestcode.rsa.pub" /etc/apk/keys/
else
    cat <<'EOF' > /etc/apk/keys/crest@crestcode.rsa.pub
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv8SdTBe/lPIyzbiCmGpy
KoFQztASc+3ZGI2mXTLueAybsl8gJQ6+n7MoFcUlkeG+UuTvuFof8lned9HSKRbq
9XxYgdZbpDGrCF32EoYlXdX9KzRG2yU8NeNKYHq94YNh3SOhmGjUJ9lrv182ekSi
t9Im4CUE5VeMVmov4vx6Yd/2coGGxLB1jaXjnxyhR6JOxzvhYZA0gsmQNPiXXnSj
vx+PCwP48HojSZjESjjioqhCC47DdGw9xTfgS1tPMShc12hjEJ0ne6F8KMcLSnDU
g4ma9/JeSTDUPCnU2bRMkHahQz/IaCOPmjtHqXMApO/zwTQPUClAgxouf44E+0pE
2QIDAQAB
-----END PUBLIC KEY-----
EOF
fi

echo "[3/5] Adding Alpine & CrestCode Repositories..."
if ! grep -q "edge/main" /etc/apk/repositories 2>/dev/null; then
    cat >> /etc/apk/repositories <<'EOF'
https://dl-cdn.alpinelinux.org/alpine/edge/main
https://dl-cdn.alpinelinux.org/alpine/edge/community
EOF
fi

if ! grep -q "Crest-Packages" /etc/apk/repositories 2>/dev/null; then
    cat >> /etc/apk/repositories <<'EOF'
https://heysaish.github.io/Crest-Packages
EOF
fi

echo "[4/5] Setting up CPM (Crest Package Manager)..."
mkdir -p /usr/local/bin
if [ -f "$PREFIX/cpm" ]; then
    cp -f "$PREFIX/cpm" /usr/local/bin/cpm
    chmod +x /usr/local/bin/cpm
fi

echo "[5/5] Updating APK indexes & installing core packages..."
apk update || true
apk add bash || true
apk add -u cpm || true

echo
echo "=========================================="
echo " Alpine & CPM setup completed! 🐧🚀"
echo "=========================================="
echo
if command -v bash >/dev/null 2>&1; then
    echo "Bash: $(bash --version | head -n 1)"
else
    echo "Fallback: Using /bin/sh"
fi

if command -v cpm >/dev/null 2>&1; then
    echo "CPM:  $(cpm --version)"
fi
