-- AI plan phase 2 (2026-11-01). The owner's decision: the old rows are test data, so clear them.
-- They embed the problem AND its solution, and they can name whoever pressed Resolve (before issue 62) as the engineer.
-- New rows fill in as incidents are resolved. Until then a suggestion answers NOT_ENOUGH_SIMILAR_INCIDENTS.
DELETE FROM public.resolved_incidents_vectors;
