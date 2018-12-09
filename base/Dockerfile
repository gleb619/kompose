FROM groovy:2.5.4-jdk8-alpine

#USER root

USER root

#http://dl-cdn.alpinelinux.org/alpine/
#http://nl.alpinelinux.org/alpine/
#http://dl-2.alpinelinux.org/alpine/
#http://dl-3.alpinelinux.org/alpine/
#http://dl-4.alpinelinux.org/alpine/
#http://dl-5.alpinelinux.org/alpine/
#http://dl-6.alpinelinux.org/alpine/
#http://dl-8.alpinelinux.org/alpine/
#http://distrib-coffee.ipsl.jussieu.fr/pub/linux/alpine/alpine/
#http://mirror.yandex.ru/mirrors/alpine/
#http://mirrors.gigenet.com/alpinelinux/
#http://repos.lax-noc.com/alpine/
#http://repos.dfw.lax-noc.com/alpine/
#http://repos.mia.lax-noc.com/alpine/
#http://mirror1.hs-esslingen.de/pub/Mirrors/alpine/
#http://liskamm.alpinelinux.uk/
#http://mirrors.2f30.org/alpine/
#http://mirror.leaseweb.com/alpine/
#http://repository.fit.cvut.cz/mirrors/alpine/
#http://alpine.mirror.far.fi/
#http://lasca.ic.unicamp.br/pub/alpine/
#http://alpinelinux.c3sl.ufpr.br/

RUN echo http://mirror.yandex.ru/mirrors/alpine/v3.5/main > /etc/apk/repositories && \
    echo http://mirror.yandex.ru/mirrors/alpine/v3.5/community >> /etc/apk/repositories && \
    apk add --no-cache curl && \
    rm -rf /var/cache/apk/* && \
    curl -L https://github.com/kubernetes/kompose/releases/download/v1.16.0/kompose-linux-amd64 -o kompose && \
    chmod +x kompose && \
    mv ./kompose /usr/local/bin/kompose

USER groovy

#EXPOSE 8080
#
#ENV GROOVY_VERSION=2.4.15
#
#RUN mkdir "/usr/lib/groovy" && \
#    cd "/tmp" && \
#    wget "http://repo1.maven.org/maven2/com/github/igor-suhorukov/groovy-grape-aether/$GROOVY_VERSION/groovy-grape-aether-$GROOVY_VERSION.jar" && \
#    mv "/tmp/groovy-grape-aether-$GROOVY_VERSION.jar" /usr/lib/groovy/groovy-grape-aether.jar
#
#ENTRYPOINT ["java","-jar","/usr/lib/groovy/groovy-grape-aether.jar"]
#
#CMD ["--help"]