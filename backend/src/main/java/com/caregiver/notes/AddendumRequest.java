package com.caregiver.notes;
import jakarta.validation.constraints.*;
public record AddendumRequest(@Size(max=40) String mood,@Size(max=40) String appetite,@Size(max=40) String sleep,@Size(max=40) String mobility,@Pattern(regexp="TAKEN|MISSED|UNKNOWN") String medicationStatus,Boolean medicationVerified,@Min(0) @Max(10) Integer painScore,Boolean fallReported,@Size(max=20000) String freeText){}