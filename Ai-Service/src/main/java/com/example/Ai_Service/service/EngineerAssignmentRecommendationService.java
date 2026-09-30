package com.example.Ai_Service.service;

import com.example.Ai_Service.dto.*;
import com.example.Ai_Service.feignClients.ReportingServiceFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;


import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class EngineerAssignmentRecommendationService {
    private final VectorStore vectorStore;
private final ReportingServiceFeignClient reportingServiceFeignClient;
    public List<EngineerCandidateDTO> recommendedEngineers(EngineerRecommendationRequestDTO request) {
        // C1 step 1b: was log.info of the whole request object. Today that printed only a memory
        // address (no toString), but the request holds the incident title + description - one
        // @Data away from printing them. Now debug, with just the 2 values this method uses.
        log.debug("Recommending engineers for category {}, service {}", request.getCategory(), request.getServiceName());
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
        VectorStoreDocumentRetriever retriever_WithFilter = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(4)
                .similarityThreshold(0.6)
                .filterExpression(filter)
                .build();

        // VectorStoreDocumentRetriever without  Filter.Expression
        VectorStoreDocumentRetriever retriever_fallback = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(4)
                .similarityThreshold(0.7)
                .build();
        // Step 2: Manually  Try retrieving with the  retrieval (that has filter expression)

        // we also need to make the Query as they traditional way accept the Query object in retrieval method .
        String queryText = request.getTitle() + " " + request.getDescription();
        Query query = new Query(queryText);
        List<Document> DocRetrievedFromVectorDb = retriever_WithFilter.retrieve(query);
        log.info("Filtered retrieval returned {} docs", DocRetrievedFromVectorDb.size());
        List<Document> finalDoc=new ArrayList<>();
        if (DocRetrievedFromVectorDb.isEmpty()) {
            log.warn("Filtered Document retrieval  from Vector Db returned no results so we are → using fallback retrival where no filter is applied");

            List<Document> fallbackDocsFromVectorDb = retriever_fallback.retrieve(query);
            log.info("Filtered retrieval returned {} docs", DocRetrievedFromVectorDb.size());
            if (fallbackDocsFromVectorDb.isEmpty()) {
                log.warn("Fallback Document retrival also empty → sono context available");
                log.warn("so Not enough similar incidents found to provide suggestion. so EXITING without calling LLM as it will not give good response without context");
            }
            else
            { finalDoc=fallbackDocsFromVectorDb;
            }

        }
        else{finalDoc=DocRetrievedFromVectorDb;
        log.info("so we are using the DocRetrievedFromVectorDb with filter based search from metadata");}
        // for each retrieved document we are extracting the metadata and creating the EngineerCandidateDTO object and putting that in the map where key is engineerId and value is the EngineerCandidateDTO .
        Map<UUID, EngineerCandidateDTO>EngineerCandidates =  extractEngineerCandidates(finalDoc);

//        performance stats and Workload related data from Reporting service

GetEngineerStatsFromReportingService(EngineerCandidates,request);

//setting finalRankingScore of each candidate by using the calculateFinalRankingScore function.

        for (EngineerCandidateDTO candidate : EngineerCandidates.values()) {
      double finalRankingScore = calculateFinalRankingScore(candidate);
            candidate.setFinalRankingScore(finalRankingScore);


        }
// list of engineers sorted based on their finalRankingScore in descending order.
List<EngineerCandidateDTO>SortedRecommendedEngineers=EngineerCandidates.values()
        .stream().sorted(Comparator.comparingDouble(EngineerCandidateDTO::getFinalRankingScore).reversed()).toList();



  return SortedRecommendedEngineers;
    }

//------------------Helper functions----------
    //Calling the Reporting service for getting the performance stats and Workload related data from Reporting service...
    public void GetEngineerStatsFromReportingService(Map<UUID, EngineerCandidateDTO>Engineers, EngineerRecommendationRequestDTO requestDTO){
        for(EngineerCandidateDTO engineer:Engineers.values()){
            log.info("Calling the FeignClient for getting the performance stats and Workload related data");
            EngineerPerformanceStatsDTO engineerPerformanceStats= reportingServiceFeignClient.getEngineerPerformanceStats(engineer.getEngineerId(),requestDTO.getCategory(),requestDTO.getServiceName());
            EngineerWorkloadDTO engineerWorkload= reportingServiceFeignClient.getEngineerWorkload(engineer.getEngineerId());
log.info("so EngineerPerformanceStatsDTO is null → {} and EngineerWorkloadDTO is null → {}",engineerPerformanceStats==null,engineerWorkload==null);
            if(engineerPerformanceStats!=null)
            {engineer.setExpertiseScore(engineerPerformanceStats.getExpertiseScore());
                engineer.setSlaSuccessRate(engineerPerformanceStats.getSlaSuccessRate());
                log.info("so Expertise Score is {},Sla Success Rate is {}",engineerPerformanceStats.getExpertiseScore(),engineerPerformanceStats.getSlaSuccessRate());
            }
            if(engineerWorkload!=null){
                engineer.setActiveIncidents(engineerWorkload.getActiveIncidentCount());
                log.info("so Active Incidents count is {}",engineerWorkload.getActiveIncidentCount());
            }

        }
    }


    //Extract Metadata From Retrieved Documents from RAG
    private Map<UUID, EngineerCandidateDTO>
    extractEngineerCandidates(List<Document> docs) {
        log.info(" enter the extractEngineerCandidates function with retrieved docs from vector db ");
        Map<UUID, EngineerCandidateDTO> map = new HashMap<>();
        for (Document doc : docs) {
            UUID engineerId = UUID.fromString(
                    doc.getMetadata()
                            .get("resolvedById")
                            .toString()
            );
            EngineerCandidateDTO candidate =
                    map.getOrDefault(
                            engineerId,
                            EngineerCandidateDTO.builder()
                                    .engineerId(engineerId)
                                    .similarIncidentCount(0)
                                    .build()
                    );
            // increment similar incident count
            candidate.setSimilarIncidentCount(
                    candidate.getSimilarIncidentCount() + 1
            );
            map.put(engineerId, candidate);
        }
        return map;
    }

    private Filter.Expression buildFilter(String category, String service) {
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

    private double calculateFinalRankingScore(
            EngineerCandidateDTO candidate
    ) {

        double similarityFactor =
                Math.min(
                        Math.log10(
                                candidate.getSimilarIncidentCount() + 1
                        ) / 2.0,
                        1.0
                );

        double expertiseFactor =
                candidate.getExpertiseScore() / 100.0;

        double slaFactor =
                candidate.getSlaSuccessRate();

        double workloadFactor =
                1 - Math.min(
                        candidate.getActiveIncidents() / 10.0,
                        1.0
                );

        return (
                similarityFactor * 0.35 +
                        expertiseFactor * 0.35 +
                        slaFactor * 0.20 +
                        workloadFactor * 0.10
        ) * 100;
    }
}