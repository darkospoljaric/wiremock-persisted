package com.wearenotch.wiremock.extension;

import static com.wearenotch.wiremock.extension.Constants.FILES;
import static com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction.continueWith;

import com.github.tomakehurst.wiremock.core.WireMockApp;
import com.github.tomakehurst.wiremock.extension.MappingsLoaderExtension;
import com.github.tomakehurst.wiremock.extension.requestfilter.AdminRequestFilterV2;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.Document;
import org.bson.types.Binary;

public class FilePersister implements AdminRequestFilterV2, MappingsLoaderExtension {

  public static final String FILES_PREFIX = "/files";
  public static final String FILENAME = "filename";
  public static final String BYTES = "bytes";
  private final MongoDatabase database;

  public FilePersister() {
    String connectionString = System.getProperty("mongodb.uri");
    String databaseName = System.getProperty("mongodb.name");
    System.out.println("mongodb.uri: " + connectionString);
    System.out.println("mongodb.name: " + databaseName);
    MongoClient mongoClient = MongoClients.create(connectionString);
    database = mongoClient.getDatabase(databaseName);
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    if (!request.getUrl().startsWith(FILES_PREFIX)) {
      return continueWith(request);
    }

    if (request.getMethod().isOneOf(RequestMethod.PUT)) {
      persistFile(request);
    }

    if (request.getMethod().isOneOf(RequestMethod.DELETE)) {
      deleteFile(request);
    }

    return continueWith(request);
  }

  private void persistFile(Request request) {
    String filename = getFilename(request);
    byte[] bytes = request.getBodyAsString().getBytes(StandardCharsets.UTF_8);

    Document fileDoc = new Document().append(FILENAME, filename).append(BYTES, new Binary(bytes));
    database.getCollection(FILES).insertOne(fileDoc);
  }

  private void deleteFile(Request request) {
    String filename = getFilename(request);
    Document filter = new Document().append(FILENAME, filename);
    database.getCollection(FILES).deleteOne(filter);
  }

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void loadMappingsInto(StubMappings stubMappings) {
    MongoCollection<Document> files = database.getCollection(FILES);
    files.find().forEach(file -> {
      String filename = file.getString(FILENAME);
      Binary bytesBinary = file.get(BYTES, Binary.class);
      byte[] bytes = bytesBinary.getData();
      try {
        Path path = Files.createFile(Path.of(WireMockApp.FILES_ROOT, filename));
        Files.write(path, bytes);
      } catch (FileAlreadyExistsException ex) {
        // process restarted, do nothing
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private static String getFilename(Request request) {
    return request.getUrl().substring((FILES_PREFIX + "/").length());
  }
}
