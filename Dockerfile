FROM openjdk:17

COPY gateway/target/scala-*/*.sh.bat ./

USER 1000:1000

CMD exec ./*.sh.bat
