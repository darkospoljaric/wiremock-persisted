package com.wearenotch.wiremock.extension;

import static com.wearenotch.wiremock.extension.Constants.STUBS;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.extension.MappingsLoaderExtension;
import com.github.tomakehurst.wiremock.extension.StubLifecycleListener;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.Document;

public class StubPersister implements StubLifecycleListener, MappingsLoaderExtension {

  MongoDatabase database;

  private final List<UUID> persistedStubsAtStartup = new ArrayList<>();

  public StubPersister() {
    String connectionString = System.getProperty("mongodb.uri");
    String dbName = System.getProperty("mongodb.name");
    MongoClient mongoClient = MongoClients.create(connectionString);
    database = mongoClient.getDatabase(dbName);
  }

  @Override
  public void loadMappingsInto(StubMappings stubMappings) {
    MongoCollection<Document> stubs = database.getCollection(STUBS);
    stubs.find().forEach(document -> {
      document.remove("_id");
      StubMapping stubMapping = Json.read(document.toJson(), StubMapping.class);
      persistedStubsAtStartup.add(stubMapping.getId());
      stubMappings.addMapping(stubMapping);
    });
  }

  @Override
  public void afterStubCreated(StubMapping stubMapping) {
    if (!persistedStubsAtStartup.contains(stubMapping.getUuid())) {
      String json = Json.write(stubMapping);
      database.getCollection(STUBS).insertOne(Document.parse(json));
      System.out.println("[afterStubCreated] inserted stub maping");
    }
  }

  @Override
  public void afterStubEdited(StubMapping oldStub, StubMapping newStub) {
    String json = Json.write(newStub);
    Document filter = new Document();
    filter.put("id", oldStub.getId().toString());
    database.getCollection(STUBS).findOneAndReplace(filter, Document.parse(json));
  }

  @Override
  public void afterStubRemoved(StubMapping stubMapping) {
    Document filter = new Document();
    filter.put("id", stubMapping.getId().toString());
    database.getCollection(STUBS).deleteOne(filter);
  }

  @Override
  public void afterStubsReset() {
    Document filter = new Document();
    filter.put("id", "*");
    database.getCollection(STUBS).deleteMany(filter); // all
  }

  @Override
  public String getName() {
    return this.getClass().getName();
  }

}
