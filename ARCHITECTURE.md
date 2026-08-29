# Shared Events Architecture - Setup Complete ✅

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│         Incident Management Platform (Parent POM)              │
│                   Root: pom.xml                                │
└────────────┬──────────────┬──────────────┬──────────────┬──────┘
             │              │              │              │
    ┌────────▼────┐  ┌──────▼────┐  ┌─────▼──────┐  ┌───▼──────┐
    │  shared-    │  │    Auth   │  │    User    │  │   API    │
    │   events    │  │  Service  │  │  Service   │  │ Gateway  │
    │ (JAR - DTO) │  │           │  │            │  │          │
    └─────┬──────┘  └──────┬─────┘  └─────┬──────┘  └───┬──────┘
          │                │              │             │
          │         Imports from:         │             │
          │         com.example:          │             │
          │         shared-events:        │             │
          │         1.0-SNAPSHOT          │             │
          │                │              │             │
          └────────────────┴──────────────┴─────────────┘
                          ▼
                      KAFKA BROKER
                          ▼
              (Shared Event: UserRegisteredEvent)
```

## File Structure

```
incident-management-platform/
├── pom.xml (NEW - Parent POM)
├── shared-events/
│   ├── pom.xml (Standalone JAR module)
│   ├── src/main/java/com/project/events/
│   │   └── UserRegisteredEvent.java (Shared DTO)
│   └── target/ (Builds to JAR - publishable)
│
├── AuthService/
│   ├── pom.xml (Depends on shared-events:1.0-SNAPSHOT)
│   └── src/main/java/com/example/AuthService/kafka/producer/
│       └── UserEventProducer.java (Uses UserRegisteredEvent)
│
├── UserService/
│   ├── pom.xml (Can depend on shared-events)
│   └── src/.../kafka/consumer/ (Can consume UserRegisteredEvent)
│
└── ... other services
```

## How It Works

### 1. **shared-events Module**
   - **Purpose**: Provides shared DTOs/Event classes for Kafka messages
   - **Packaging**: JAR (not a Spring Boot application)
   - **Dependencies**: 
     - `jackson-annotations` (for JSON serialization)
     - `lombok:provided` (compile-time code generation)
   - **Built as**: Maven artifact `com.example:shared-events:1.0-SNAPSHOT`

### 2. **AuthService (Producer Example)**
   ```xml
   <dependency>
       <groupId>com.example</groupId>
       <artifactId>shared-events</artifactId>
       <version>1.0-SNAPSHOT</version>
   </dependency>
   ```
   - Imports `UserRegisteredEvent` from shared-events
   - `UserEventProducer.java` publishes events to Kafka
   - Uses Jackson to serialize events to JSON

### 3. **UserService (Consumer Example)**
   ```xml
   <dependency>
       <groupId>com.example</groupId>
       <artifactId>shared-events</artifactId>
       <version>1.0-SNAPSHOT</version>
   </dependency>
   ```
   - Same dependency can consume the events
   - Deserializes `UserRegisteredEvent` from Kafka messages

## Key Advantages of This Architecture

✅ **DRY Principle** - One source of truth for event definitions  
✅ **Type Safety** - Compile-time checking of event structures  
✅ **Scalability** - Easy to add new event classes  
✅ **Loose Coupling** - Services depend on contracts, not implementations  
✅ **Version Control** - Events versioned with the platform  

## Dependency Injection Flow

```
Maven Local Repository
    ↓
shared-events:1.0-SNAPSHOT (JAR)
    ├─→ AuthService pom.xml (imports)
    │       ↓
    │   UserEventProducer.java
    │       ↓
    │   Uses: UserRegisteredEvent (from shared-events)
    │
    └─→ UserService pom.xml (imports)
            ↓
        UserEventConsumer.java
            ↓
        Consumes: UserRegisteredEvent (from shared-events)
```

## Build Order

When building from root:

```bash
cd incident-management-platform
mvn clean install
```

Maven will automatically:
1. Build `shared-events` first (listed first in modules)
2. Install it to local Maven repository
3. Build `AuthService`, `UserService`, etc. (which depend on shared-events)

## Kafka Message Format

When `UserEventProducer` publishes:

```java
UserRegisteredEvent event = UserRegisteredEvent.builder()
    .userid(UUID.randomUUID())
    .username("john_doe")
    .email("john@example.com")
    .createdAt(LocalDateTime.now())
    .build();

kafkaTemplate.send("user.registered", event);
```

Serialized to JSON (via Jackson):
```json
{
  "userid": "550e8400-e29b-41d4-a716-446655440000",
  "username": "john_doe",
  "email": "john@example.com",
  "createdAt": "2026-03-16T10:30:45",
  "updatedAt": null,
  "lastLoginAt": null,
  "passwordUpdatedAt": null
}
```

## Summary

✅ **shared-events** is now properly configured as a shared JAR module  
✅ **UserEventProducer.java** correctly imports from `com.project.events.UserRegisteredEvent`  
✅ **AuthService pom.xml** has the correct dependency with version  
✅ **Parent pom.xml** manages all modules in one build  
✅ Architecture follows microservices best practices

