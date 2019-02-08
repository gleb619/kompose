FROM groovy:2.5.4-jdk8-alpine

USER root

RUN echo http://mirror.yandex.ru/mirrors/alpine/v3.5/main > /etc/apk/repositories && \
    echo http://mirror.yandex.ru/mirrors/alpine/v3.5/community >> /etc/apk/repositories && \
    apk add --no-cache curl && \
    rm -rf /var/cache/apk/* && \
    curl -L https://github.com/kubernetes/kompose/releases/download/v1.16.0/kompose-linux-amd64 -o kompose && \
    chmod +x kompose && \
    mv ./kompose /usr/local/bin/kompose

RUN apk add libc6-compat && \
    mkdir /lib64 && ln -s /lib/ld-linux-x86-64.so.2 /lib64/ld-linux-x86-64.so.2 && \
    wget -q -O /etc/apk/keys/sgerrand.rsa.pub https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub && \
    wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.28-r0/glibc-2.28-r0.apk && \
    apk add glibc-2.28-r0.apk

USER groovy
