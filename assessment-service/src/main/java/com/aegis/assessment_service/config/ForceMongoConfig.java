package com.aegis.assessment_service.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForceMongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        // Never print credentials from the connection string into logs.
        String safeUri = mongoUri.replaceAll("//[^@]*@", "//***@");
        System.out.println("====== FORCE INJECTING MONGO CLIENT: " + safeUri + " ======");
        return MongoClients.create(mongoUri);
    }
}
