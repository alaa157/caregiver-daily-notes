package com.caregiver.notes;
import jakarta.validation.*; import org.junit.jupiter.api.*; import java.time.LocalDate; import java.util.UUID; import static org.assertj.core.api.Assertions.assertThat;
class NoteRequestValidationTest{
 static ValidatorFactory factory;static Validator validator;@BeforeAll static void setup(){factory=Validation.buildDefaultValidatorFactory();validator=factory.getValidator();}@AfterAll static void close(){factory.close();}
 @Test void rejectsPainOutsideZeroToTen(){var r=new NoteRequest(UUID.randomUUID(),LocalDate.now(),"good","normal","good","normal","TAKEN",true,11,false,"text");assertThat(validator.validate(r)).anyMatch(v->v.getPropertyPath().toString().equals("painScore"));}
 @Test void rejectsUnknownMedicationEnum(){var r=new NoteRequest(UUID.randomUUID(),LocalDate.now(),"good","normal","good","normal","MAYBE",true,5,false,"text");assertThat(validator.validate(r)).anyMatch(v->v.getPropertyPath().toString().equals("medicationStatus"));}
 @Test void allowsMissingOptionalObservations(){var r=new NoteRequest(UUID.randomUUID(),LocalDate.now(),null,null,null,null,null,null,null,null,null);assertThat(validator.validate(r)).isEmpty();}
}