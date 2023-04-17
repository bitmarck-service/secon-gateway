FROM openjdk:17

COPY dist/*.sh.bat ./

USER 1000:1000

CMD exec ./*.sh.bat
