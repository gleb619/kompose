FROM gleb619/kompose-base

COPY main.groovy /app/

RUN export JAVA_OPTS='-Divy.message.logger.level=2 -Dgroovy.grape.report.downloads=true'

ENTRYPOINT ["groovy", "/app/main.groovy"]