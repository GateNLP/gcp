FROM openjdk:8-jre

COPY gcp /opt/gcp/

WORKDIR /opt/gcp

ENTRYPOINT ["/usr/bin/java", "-jar", "/opt/gcp/gcp-cli.jar"]

CMD ["-t", "6", "-m", "4G"]
