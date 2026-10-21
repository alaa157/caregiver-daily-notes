package com.caregiver.notes;
import java.time.Instant; import java.time.LocalDate; import java.util.UUID;
public record NoteResponse(UUID id,UUID recipientId,LocalDate date,String mood,String appetite,String sleep,String mobility,String medicationStatus,Boolean medicationVerified,Integer painScore,Boolean fallReported,String freeText,long addendumCount,Instant createdAt){
 static NoteResponse from(DailyNote n,long count){return new NoteResponse(n.getId(),n.getRecipient().getId(),n.getDate(),n.getMood(),n.getAppetite(),n.getSleep(),n.getMobility(),n.getMedicationStatus(),n.getMedicationVerified(),n.getPainScore(),n.getFallReported(),n.getFreeText(),count,n.getCreatedAt());}
}