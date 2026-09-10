package com.example.Ai_Service.service;


import com.example.Ai_Service.dto.IncidentResolvementSuggestionDTO;
import com.example.Ai_Service.dto.IncidentSuggestionNeededDTO;
import com.google.protobuf.Empty;
import com.project.events.IncidentResolutionReportEvent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.ai.vectorstore.filter.Filter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
@RequiredArgsConstructor
@Service
public class RagIntegrationService {
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(RagIntegrationService.class);
    PromptTemplate userPromptTemplate;
    SystemPromptTemplate systemPromptTemplate;

    @Value("classpath:/prompts/ragIntegrationPrompts/rag-userPrompt.st")
    private Resource userPromptResource;
    @Value("classpath:/prompts/ragIntegrationPrompts/rag-systemPrompt.st")
    private Resource systemPromptResource;

    public void storingResolvedIncidentsInVectorDb(IncidentResolutionReportEvent event){
        log.info("we are going to start the ETL Pipeline");
        // 1. EXTRACT
 String rawText= BuildTextForRag(event);
 log.info("Incident resolved data: extraction Done");
        // 2. TRANSFORM (cleaning / structuring)
 String cleaned= CleanTextForRag(rawText);
 log.info("Incident resolved data: transformation Done");

        // 3. LOAD into vector store

        // ---------------------------------------------------------------------------------
        // The document id is the INCIDENT id, on purpose. (Track B2 - 2026-09-08)
        //
        // It used to be `new Document(cleaned)`, which gives the document a brand new random id
        // every time. That was a quiet bug:
        //
        // Kafka promises "at least once" delivery, so the same event CAN arrive twice - after a
        // retry, a rebalance, or a restart. With a random id each time, the same incident got
        // stored as TWO separate documents. Then a search would find both, the same advice would
        // be counted twice, and the AI's suggestions would slowly get worse.
        //
        // Nothing would ever show an error. That is what made it nasty.
        //
        // Using the incident id fixes it, because of how the vector store writes rows:
        //
        //     INSERT INTO ... VALUES (...) ON CONFLICT (id) DO UPDATE SET ...
        //
        // Same id arriving again = overwrite the existing row, not add a second one.
        //
        // A NOTE ON WHY WE OVERWRITE RATHER THAN SKIP
        // We could check "does this incident already exist" and skip early. That would save the
        // Gemini embedding call on a repeat. We deliberately do not, because an incident can
        // genuinely be resolved more than once (reopened, then resolved differently). Skipping
        // would leave the old, wrong resolution in the search index forever. Overwriting always
        // keeps the latest version. Kafka redeliveries are rare, so the occasional extra
        // embedding call is a fair price for never serving stale advice.
//        also here:Document doesn't have a version of its constructor that takes just (id, content). If you want to give it a custom id, you're forced to also hand it a metadata box — even if that box starts empty.
//        as this is what we are doing: new Document(id, content, metadata)      // YOUR id, but metadata is required
        // ---------------------------------------------------------------------------------
        Document doc = new Document(event.getIncidentId().toString(), cleaned, new HashMap<>());
        //so in our HAshMap we are  actaully  adding  the metadata to the document that we are storing in vector db so that it can be used in filtering the relevant documents at the time of retrieval from vector db based on the user prompt query.
        doc.getMetadata().put("incidentId",event.getIncidentId());
        doc.getMetadata().put("category",event.getCategory());
        doc.getMetadata().put("service",event.getServiceName());
        doc.getMetadata().put("resolvedById", event.getResolvedBy());
        doc.getMetadata().put("resolutionTimeInSeconds", event.getResolutionTimeInSeconds());
        doc.getMetadata().put("assignmentSlaBreached",event.isAssignmentSlaBreached());
        doc.getMetadata().put( "resolutionSlaBreached",event.isResolutionSlaBreached());
        doc.getMetadata().put("resolvedAt",event.getResolvedAt().toString());

log.info("Incident resolved data: data loading Done,now  adding to vector store");
        vectorStore.add(List.of(doc));
log.info("Incident resolved data: added to vector store successfully "
        + "(document id = incident id {}, so a repeat delivery overwrites instead of duplicating)",
        event.getIncidentId());
    }
//Helper method used in storingResolvedIncidentsInVectorDb
    private String BuildTextForRag(IncidentResolutionReportEvent event)
    {
        return String.format("""
                Incident: %s
                Description: %s
                Category: %s
                Service: %s
                Root Cause: %s
                Resolution: %s""",

                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                event.getServiceName(),
                event.getRootCause(),
                event.getResolutionNotes()
      );

    }
    //Helper method used in storingResolvedIncidentsInVectorDb
    private String CleanTextForRag(String text)
    {
        // Placeholder for any cleaning logic (e.g., removing special characters, normalizing whitespace)
     return text
            .replaceAll("[ \\t]+", " ")   // clean spaces/tabs
            .replaceAll("\\n+", "\n")     // keep single newline
            .trim();
    }
//----------------------------------------
    @PostConstruct
    public void init(){
        userPromptTemplate= PromptTemplate.builder().resource(userPromptResource).build();
       systemPromptTemplate=SystemPromptTemplate.builder().resource(systemPromptResource).build();
    }


    // actually performing RAG: as retrieving the similar data(CONTEXT) from vector db using the user-prompt,adding the context along with userprompt and system prompt , finally using the combined prompt [userprompt,systemprompt,context from vector db]to call the LLM and get the response.
    public IncidentResolvementSuggestionDTO generateSuggestionForNewIncident(IncidentSuggestionNeededDTO  request){
        log.info("Request data from User for Incident → title={}, category={}, service={}",
                request.getTitle(), request.getCategory(), request.getServiceName());

        Filter.Expression filter = buildFilter(request.getCategory(), request.getServiceName());


        // we are also applying the fallback mechanism here that is:
        // suppose while using the filtering expression for retrieving similar data ,the filter is too strict that we are not  to retrieve any relevant document from vector db.
        // in that case we will not have any context to give to the LLM and that may affect the quality of the response that we will get from LLM, so in that case  instead of giving
        // no context to LLM we can give some context to LLM by relaxing the filter condition and trying to retrieve the relevant documents again, so in this way we can increase the
        // chances of retrieving some relevant documents from vector db and giving that as context to LLM which may help in improving the quality of response from LLM.
//        User Query
//             ↓
//        Try filtered retrieval
//             ↓
//        IF results found → use filtered
//        ELSE → fallback to unfiltered
//             ↓
//        Send to LLM


//        Step 1: Create both retrievers
        //  VectorStoreDocumentRetriever with  Filter.Expression
        VectorStoreDocumentRetriever retriever_WithFilter= VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(4)
                .similarityThreshold(0.6)
                .filterExpression(filter)
                .build();
      // VectorStoreDocumentRetriever without  Filter.Expression
        VectorStoreDocumentRetriever retriever_fallback= VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(4)
                .similarityThreshold(0.7)
                .build();
        // Step 2: Manually  Try retrieving with the  retrieval (that has filter expression) BEFORE calling advisor
        // we also need to make the Query as they traditional way accept the Query object in retrieval method .
        String queryText = request.getTitle() + " " + request.getDescription();
        Query query = new Query(queryText);
        List<Document> DocRetrievedFromVectorDb=retriever_WithFilter.retrieve(query);
        log.info("Filtered retrieval returned {} docs", DocRetrievedFromVectorDb.size());
        VectorStoreDocumentRetriever finalRetriever;
        if( DocRetrievedFromVectorDb.isEmpty()){
            log.warn("Filtered Document retrieval  from Vector Db returned no results so we are → using fallback retrival where no filter is applied");

            List<Document> fallbackDocsFromVectorDb = retriever_fallback.retrieve(query);
            log.info("Filtered retrieval returned {} docs", DocRetrievedFromVectorDb.size());
            if ( fallbackDocsFromVectorDb.isEmpty()) {
                log.warn("Fallback Document retrival also empty → sono context available");
                log.warn("so Not enough similar incidents found to provide suggestion. so EXITING without calling LLM as it will not give good response without context");
                return IncidentResolvementSuggestionDTO.builder().anyError("Both Filtered and Fallback Document retrieval are not able to find the similar incidents from VectorDB so we are EXITING  without calling LLM as it will not give good response without context. ").build();
            }
            finalRetriever=retriever_fallback;
        }
        else{
            finalRetriever=retriever_WithFilter;
        }

        Message userMessage=userPromptTemplate.createMessage(Map.of("title",request.getTitle(),"description",request.getDescription()));
        Message systemMessage=systemPromptTemplate.createMessage();
        Prompt prompt=new Prompt(systemMessage,userMessage);
        return chatClient
                .prompt(prompt)
                .advisors(RetrievalAugmentationAdvisor.builder().documentRetriever(finalRetriever).build())
                .call()
                .entity(IncidentResolvementSuggestionDTO.class);
    }
//Filter.eq(field, value) :
//“Here we are making the Expression that it is used  in filtering the Data from vector db
private Filter.Expression  buildFilter(String category, String service) {
    FilterExpressionBuilder builder = new FilterExpressionBuilder();
    if (category != null && service != null) {
        return builder.and(
                builder.eq("category", category),
                builder.eq("service", service)
        ).build();
    } else if (category != null) {
        return builder.eq("category", category).build();
    } else if (service != null) {
        return builder.eq("service", service).build();
    }

    return null;
}

}
