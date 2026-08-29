Root Project:
which represents the entire platform.
like :incident-management-platform is the root project that contains all the microservices and shared modules. 
It is the main entry point for the application and can be used to run all the services together using Docker Compose or Kubernetes.

 **Pom.xml of the root project**:
1.add the dependencies that are common for all the microservices and shared modules.(we will use this approch during optimistaion of project) 

2.used to build order so that Maven builds modules in correct order based on their dependencies need.
For example, if AuthService depends on shared-events, then shared-events will be built first before AuthService.



-----------------
inside artifactId: project name
so When Maven builds:

incident-management-platform-1.0-SNAPSHOT.jar
   project name is : incident-management-platform
   version is : 1.0-SNAPSHOT


<packaging>pom</packaging> :This is VERY IMPORTANT in your architecture
this tells This project is:
❌ NOT a runnable app
❌ NOT a JAR
❌ NOT a WAR
It is:
✅ A parent  project so i will not have any code or main class to run, but it will manage the dependencies and build order of all the child modules (microservices and shared modules) in my architecture.

incident-management-platform (root)
│
├── shared-events (JAR)
├── AuthService (Spring Boot)
├── UserService
├── APIGateway
└── Eureka