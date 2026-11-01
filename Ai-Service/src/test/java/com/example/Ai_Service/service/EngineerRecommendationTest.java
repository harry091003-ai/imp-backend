package com.example.Ai_Service.service;

import com.example.Ai_Service.FakeEmbeddingModel;
import com.example.Ai_Service.dto.EngineerCandidateDTO;
import com.example.Ai_Service.dto.EngineerRecommendationRequestDTO;
import com.example.Ai_Service.dto.EngineerSummaryDTO;
import com.example.Ai_Service.dto.EngineerSummaryRequestDTO;
import com.example.Ai_Service.dto.UserValidationDTO;
import com.example.Ai_Service.exception.AiUnavailableException;
import com.example.Ai_Service.feignClients.ReportingServiceFeignClient;
import com.example.Ai_Service.feignClients.UserServiceFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI plan phase 1 (2026-11-01): POST /ai/EngineersSuggestion-ForIncidentAssignment on the REAL code.
 * A fake embedding model, an in-memory vector store and fake UserService / ReportingService.
 */
class EngineerRecommendationTest {

    private static final String SAME_WORDS = "database timeout payment";

    private final UUID supervisor = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID anna = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final UUID carl = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    private final FakeEmbeddingModel embedding = new FakeEmbeddingModel();
    private final ReportingServiceFeignClient reporting = mock(ReportingServiceFeignClient.class);
    private final UserServiceFeignClient users = mock(UserServiceFeignClient.class);

    private final Map<UUID, EngineerSummaryDTO> summaries = new HashMap<>();
    private final Map<UUID, UserValidationDTO> userAnswers = new HashMap<>();

    private SimpleVectorStore store;
    private EngineerAssignmentRecommendationService service;

    @BeforeEach
    void setUp() {
        store = SimpleVectorStore.builder(embedding).build();
        AiCallGuard guard = new AiCallGuard(5);
        service = new EngineerAssignmentRecommendationService(new SimilarIncidentSearch(store, guard, 0.6), reporting, users,
                new EngineerRanking(0.35, 0.35, 0.20, 0.10), 10, 3, 5);

        for (UUID engineer : List.of(anna, ben, carl, outsider)) {
            userAnswers.put(engineer, user(engineer, "ACTIVE", "ROLE_ENGINEER"));
        }
        when(users.getEngineersUnderSupervisor(supervisor)).thenReturn(List.of(anna, ben, carl));
        when(users.validateUsers(any())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return ids.stream().filter(userAnswers::containsKey).map(userAnswers::get).toList();
        });
        when(reporting.getEngineerSummaries(any())).thenAnswer(call -> {
            EngineerSummaryRequestDTO request = call.getArgument(0);
            return request.getEngineerIds().stream()
                    .map(id -> summaries.getOrDefault(id, EngineerSummaryDTO.builder().engineerId(id).build())).toList();
        });
    }

    // ------------------------------------------------------------------ helpers

    private static UserValidationDTO user(UUID id, String status, String role) {
        return UserValidationDTO.builder().userid(id).status(status).roles(Set.of(role)).build();
    }

    private void solved(UUID engineer, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "DATABASE");
        metadata.put("service", "PAYMENT_SERVICE");
        metadata.put("resolvedById", engineer);
        store.add(List.of(new Document(UUID.randomUUID().toString(), text, metadata)));
    }

    private void stats(UUID engineer, double expertise, double sla, int active) {
        summaries.put(engineer, EngineerSummaryDTO.builder().engineerId(engineer).totalIncidentsResolved(5)
                .expertiseScore(expertise).slaSuccessRate(sla).activeIncidentCount(active).build());
    }

    private static EngineerRecommendationRequestDTO request() {
        return EngineerRecommendationRequestDTO.builder().title("database timeout").description("payment")
                .category("DATABASE").serviceName("PAYMENT_SERVICE").build();
    }

    private Authentication loggedIn(UUID id, String role) {
        return new UsernamePasswordAuthenticationToken(id.toString(), null, List.of(new SimpleGrantedAuthority(role)));
    }

    private List<EngineerCandidateDTO> askAsSupervisor() {
        return service.recommendedEngineers(request(), loggedIn(supervisor, "ROLE_SUPERVISOR"));
    }

    private List<EngineerCandidateDTO> askAsAdmin() {
        return service.recommendedEngineers(request(), loggedIn(admin, "ROLE_ADMIN"));
    }

    private static List<UUID> ids(List<EngineerCandidateDTO> candidates) {
        return candidates.stream().map(EngineerCandidateDTO::getEngineerId).toList();
    }

    private static EngineerCandidateDTO of(List<EngineerCandidateDTO> candidates, UUID engineer) {
        return candidates.stream().filter(c -> c.getEngineerId().equals(engineer)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ who can be recommended

    @Test
    void aSupervisorIsNeverShownSomeoneOutsideTheirTeam() {
        solved(outsider, SAME_WORDS);   // the best match of all, but they cannot assign this person
        solved(anna, SAME_WORDS);

        assertFalse(ids(askAsSupervisor()).contains(outsider));
    }

    @Test
    void anAdminSeesAnyoneWhoSolvedSomethingSimilar() {
        solved(outsider, SAME_WORDS);
        solved(anna, SAME_WORDS);

        assertEquals(Set.of(outsider, anna), Set.copyOf(ids(askAsAdmin())));
    }

    @Test
    void theRestOfTheTeamJoinsWhenTooFewHaveASimilarCase() {
        solved(anna, SAME_WORDS);

        List<EngineerCandidateDTO> answer = askAsSupervisor();

        assertEquals(Set.of(anna, ben, carl), Set.copyOf(ids(answer)));
        assertEquals(1, of(answer, anna).getSimilarIncidentCount());
        assertEquals(0, of(answer, ben).getSimilarIncidentCount());
        assertEquals(anna, answer.get(0).getEngineerId(), "the one with a similar case comes first");
    }

    @Test
    void noSimilarIncidentsAtAllStillGivesTheTeam() {
        assertEquals(Set.of(anna, ben, carl), Set.copyOf(ids(askAsSupervisor())));
    }

    @Test
    void anAdminWithNoSimilarIncidentsGetsAnEmptyList() {
        assertEquals(List.of(), askAsAdmin());
    }

    @Test
    void aSupervisorWithNoTeamGetsAnEmptyListAndNoPaidCall() {
        when(users.getEngineersUnderSupervisor(supervisor)).thenReturn(List.of());
        solved(anna, SAME_WORDS);
        embedding.calls.set(0);

        assertEquals(List.of(), askAsSupervisor());
        assertEquals(0, embedding.calls.get());
    }

    @Test
    void someoneSuspendedIsNotRecommended() {
        solved(anna, SAME_WORDS);
        solved(ben, SAME_WORDS);
        userAnswers.put(ben, user(ben, "SUSPENDED", "ROLE_ENGINEER"));

        assertFalse(ids(askAsSupervisor()).contains(ben));
    }

    @Test
    void aSupervisorWhoPressedResolveInOldDataIsNotRecommended() {
        // Before issue 62 the stored resolvedById was whoever pressed the button.
        solved(supervisor, SAME_WORDS);
        userAnswers.put(supervisor, user(supervisor, "ACTIVE", "ROLE_SUPERVISOR"));

        assertEquals(List.of(), askAsAdmin());
    }

    @Test
    void someoneWhoNoLongerExistsIsNotRecommended() {
        UUID gone = UUID.randomUUID();
        solved(gone, SAME_WORDS);

        assertEquals(List.of(), askAsAdmin());
    }

    @Test
    void aDocumentWithoutAnEngineerIsSkipped() {
        store.add(List.of(new Document(UUID.randomUUID().toString(), SAME_WORDS, new HashMap<>())));
        solved(anna, SAME_WORDS);

        assertEquals(List.of(anna), ids(askAsAdmin()));
    }

    @Test
    void theListIsLimitedToTheBestFive() {
        for (int i = 0; i < 7; i++) {
            UUID engineer = UUID.randomUUID();
            userAnswers.put(engineer, user(engineer, "ACTIVE", "ROLE_ENGINEER"));
            solved(engineer, SAME_WORDS);
        }

        assertEquals(5, askAsAdmin().size());
    }

    // ------------------------------------------------------------------ the order

    @Test
    void theCloserMatchRanksHigherWhenAllElseIsEqual() {
        solved(anna, SAME_WORDS);
        solved(ben, "database timeout payment refund invoice");

        List<EngineerCandidateDTO> answer = askAsAdmin();

        assertEquals(anna, answer.get(0).getEngineerId());
        assertTrue(of(answer, anna).getBestSimilarity() > of(answer, ben).getBestSimilarity());
    }

    @Test
    void theBusyEngineerRanksLowerWhenAllElseIsEqual() {
        solved(anna, SAME_WORDS);
        solved(ben, SAME_WORDS);
        stats(anna, 50, 0.5, 9);
        stats(ben, 50, 0.5, 0);

        assertEquals(ben, askAsAdmin().get(0).getEngineerId());
    }

    @Test
    void aNewEngineerIsNotPunishedForAnUnknownSlaRate() {
        solved(anna, SAME_WORDS);
        solved(ben, SAME_WORDS);
        // Anna: expertise 0 like a newcomer, and a really bad SLA rate. Ben: no history, so his SLA rate is unknown.
        summaries.put(anna, EngineerSummaryDTO.builder().engineerId(anna).totalIncidentsResolved(5).slaSuccessRate(0.1).build());

        assertEquals(ben, askAsAdmin().get(0).getEngineerId(), "unknown is better than bad");
    }

    // ------------------------------------------------------------------ when another service fails

    @Test
    void reportingDownStillGivesARecommendationByCloseness() {
        solved(anna, SAME_WORDS);
        solved(ben, "database timeout payment refund invoice");
        doThrow(new RuntimeException("connection refused")).when(reporting).getEngineerSummaries(any());

        List<EngineerCandidateDTO> answer = askAsAdmin();

        assertEquals(anna, answer.get(0).getEngineerId());
        assertFalse(answer.get(0).isStatsAvailable(), "the answer says its numbers are missing");
        assertTrue(answer.get(0).getFinalRankingScore() > 0);
    }

    @Test
    void whenReportingAnswersTheStatsAreMarkedAvailable() {
        solved(anna, SAME_WORDS);
        stats(anna, 70, 0.9, 2);

        EngineerCandidateDTO candidate = askAsAdmin().get(0);

        assertTrue(candidate.isStatsAvailable());
        assertEquals(70, candidate.getExpertiseScore());
        assertEquals(0.9, candidate.getSlaSuccessRate());
        assertEquals(2, candidate.getActiveIncidents());
    }

    @Test
    void userServiceDownIsA503BecauseWeCannotTellWhoIsAllowed() {
        solved(anna, SAME_WORDS);
        doThrow(new RuntimeException("connection refused")).when(users).validateUsers(any());

        AiUnavailableException error = assertThrows(AiUnavailableException.class, this::askAsAdmin);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("USER_SERVICE_UNAVAILABLE", error.getErrorCode());
    }

    @Test
    void reportingIsAskedOnceForEveryone() {
        solved(anna, SAME_WORDS);
        solved(ben, SAME_WORDS);

        askAsAdmin();

        verify(reporting, times(1)).getEngineerSummaries(any());
    }
}
