FROM openjdk:8-jre

COPY gcp /opt/gcp/
COPY m2-settings.xml /config/

RUN ["install", "-d", "-m", "1777", "/config/m2-repo"]

WORKDIR /opt/gcp

ENTRYPOINT ["/usr/bin/java", "-jar", "/opt/gcp/gcp-cli.jar", "-DM2_SETTINGS_XML=/config/m2-settings.xml"]

CMD ["-t", "6", "-m", "4G"]
