package com.example.Ai_Service.service;

import com.example.Ai_Service.dto.ClassificationResultDTO;
import com.project.events.IncidentCreatedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.Resource;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class ClassificationService {
 private final ChatClient chatClient;

    @Value("classpath:/prompts/classificationPrompts/classification-systemPrompt.st")
    private Resource systemPromptResource;

    @Value("classpath:/prompts/classificationPrompts/classification-userPrompt.st")
    private Resource userPromptResource;

    // we are doing postconstruct(){} here because
    // Field Initialization Happens Before Object Injection eg: any field like:systemPromptTemplate,userPromptTemplate etc
    //but at that moment  systemPromptResource and userPromptResource has the  null values  ..to avoid this we did field inject after the object injection is done.
    PromptTemplate systemPromptTemplate;
    PromptTemplate userPromptTemplate;
    @PostConstruct()
    public void init(){
         systemPromptTemplate= SystemPromptTemplate.builder().resource(systemPromptResource).build();
         userPromptTemplate= PromptTemplate.builder().resource(userPromptResource).build();

    }

    public ClassificationResultDTO classifyIncident(IncidentCreatedEvent event) {
        Message systemMessage=systemPromptTemplate.createMessage();
        Message userMessage=userPromptTemplate.createMessage(Map.of("title",event.getTitle(),"description",event.getDescription()));

       Prompt prompt=new Prompt(systemMessage,userMessage);

       return chatClient.prompt(prompt).call().entity(ClassificationResultDTO.class);


    }


}
