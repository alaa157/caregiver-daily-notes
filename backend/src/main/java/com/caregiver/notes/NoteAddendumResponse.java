package com.caregiver.notes;
import java.time.Instant; import java.util.UUID;
public record NoteAddendumResponse(UUID id,UUID noteId,String mood,String appetite,String sleep,String mobility,String medicationStatus,Boolean medicationVerified,Integer painScore,Boolean fallReported,String freeText,Instant createdAt){
 static NoteAddendumResponse from(NoteAddendum a){return new NoteAddendumResponse(a.getId(),a.getNote().getId(),a.getMood(),a.getAppetite(),a.getSleep(),a.getMobility(),a.getMedicationStatus(),a.getMedicationVerified(),a.getPainScore(),a.getFallReported(),a.getFreeText(),a.getCreatedAt());}
}