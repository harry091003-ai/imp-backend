kafka-docker contains the Docker configuration(inside .yml file) to run Kafka locally for development.
this configuration contains :
1.Zookeeper
2.Kafka Broker

creating Kafka using Docker:
1. In Terminal go in the kafka-docker directory and run the command : 
    **docker-compose up -d**

2. Making the topic for storing our events inside our kafka:
   **docker exec -it kafka kafka-topics --create --topic user.registered --bootstrap-server localhost:9092**

3. Create **shared-event Module** for the events (DTOs) that will be used for communication between microservices:
   that we already made.
4. add the configuration i the application.properties file of the microservices for connecting to the Kafka broker:
   like in AuthService and UserService we added

------------------------
Useful Kafka Commands (for development & debugging)
1. Create a topic:
   docker exec -it kafka kafka-topics --create --topic user.registered --bootstrap-server localhost:9092
2. List all topics
   docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
3. Describe a topic (details like partitions, replicas)
   docker exec kafka kafka-topics --describe --topic user.registered --bootstrap-server localhost:9092
4. See all events (messages) inside a topic
   docker exec kafka kafka-console-consumer --topic user.registered --bootstrap-server localhost:9092 --from-beginning
5. See only new incoming events (real-time)
   docker exec kafka kafka-console-consumer --topic user.registered --bootstrap-server localhost:9092
6. Delete a topic
   docker exec kafka kafka-topics --delete --topic user.registered --bootstrap-server localhost:9092
7. Recreate a topic (after deletion)
   docker exec -it kafka kafka-topics --create --topic user.registered --bootstrap-server localhost:9092
8. Produce (send) test messages manually
   docker exec -it kafka kafka-console-producer --topic user.registered --bootstrap-server localhost:9092