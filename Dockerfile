FROM eclipse-temurin:17-jre-alpine

COPY target/wiremock-persisted-*.jar /opt/wiremock/wiremock-persisted.jar

ENTRYPOINT java -Dmongodb.uri=${MONGO_DB_URI} -Dmongodb.name=${MONGO_DB_NAME} -jar /opt/wiremock/wiremock-persisted.jar --extensions com.wearenotch.wiremock.extension.FilePersister,com.wearenotch.wiremock.extension.StubPersister