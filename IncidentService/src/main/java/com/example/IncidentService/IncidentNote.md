in Incient Service
Apply kafka event on the following actions:
 we are making  the Events for:
CREATE INCIDENT        → EVENT ✔
ASSIGN INCIDENT        → EVENT ✔
UPDATE STATUS          → EVENT ✔
UPDATE METADATA        → NO EVENT ❌ (for now because we are not sure if we need it or not)

but the Update MetaData as an Event will be used if we are doing adavance level:
like if Incident is servity is updated from low to critical
if priority is updated from low to high 
like this in that cases we will add the if conditions to apply the kafka event for updating the metadata of the incident.

