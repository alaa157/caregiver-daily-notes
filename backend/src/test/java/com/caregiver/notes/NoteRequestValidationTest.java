package com.caregiver.notes;
import jakarta.validation.*; import org.junit.jupiter.api.*; import java.time.LocalDate; import java.util.UUID; import static org.assertj.core.api.Assertions.assertThat;
import com.caregiver.common.ObservationState;
import com.caregiver.common.VerificationState;
class NoteRequestValidationTest{
 static ValidatorFactory factory;static Validator validator;@BeforeAll static void setup(){factory=Validation.buildDefaultValidatorFactory();validator=factory.getValidator();}@AfterAll static void close(){factory.close();}
 @Test void rejectsPainOutsideZeroToTen(){var r=new NoteRequest(UUID.randomUUID(),LocalDate.now(),"good","normal","good","normal","TAKEN",true,11,false,"text");assertThat(validator.validate(r)).anyMatch(v->v.getPropertyPath().toString().equals("painScore"));}
 @Test void rejectsUnknownMedicationEnum(){var r=new NoteRequest(UUID.randomUUID(),LocalDate.now(),"good","normal","good","normal","MAYBE",true,5,false,"text");assertThat(validator.validate(r)).anyMatch(v->v.getPropertyPath().toString().equals("medicationStatus"));}
 @Test void allowsMissingOptionalObservations(){var r=new NoteRequest(UUID.randomUUID(),LocalDate.now(),null,null,null,null,null,null,null,null,null);assertThat(validator.validate(r)).isEmpty();}
 @Test void acceptsEveryExplicitStateAndIndependentVerification(){
  var set=new ObservationSetRequest(
      input(ObservationState.RECORDED,"",null),
      input(ObservationState.UNKNOWN,null,null),
      input(ObservationState.NOT_RECORDED,null,null),
      input(ObservationState.NOT_APPLICABLE,null,null),
      input(ObservationState.LEGACY_AMBIGUOUS,null,"raw"),
      input(ObservationState.RECORDED,0,null),
      input(ObservationState.RECORDED,false,null),
      input(ObservationState.RECORDED,"",null));
  var request=new NoteRequest(UUID.randomUUID(),LocalDate.now(),null,null,null,null,null,null,null,null,null,set,VerificationState.VERIFIED);
  assertThat(validator.validate(request)).isEmpty();
 }
 @Test void rejectsRecordedWithoutValueAndValuesOnNonRecordedStates(){
  var set=new ObservationSetRequest(
      input(ObservationState.RECORDED,null,null),
      input(ObservationState.UNKNOWN,"unexpected",null),
      input(ObservationState.NOT_RECORDED,null,null),
      input(ObservationState.NOT_APPLICABLE,null,null),
      input(ObservationState.LEGACY_AMBIGUOUS,null,null),
      input(ObservationState.RECORDED,0,null),
      input(ObservationState.RECORDED,false,null),
      input(ObservationState.RECORDED,"text",null));
  var request=new NoteRequest(UUID.randomUUID(),LocalDate.now(),null,null,null,null,null,null,null,null,null,set,null);
  assertThat(validator.validate(request)).isNotEmpty();
 }
 private static <T> ObservationInput<T> input(ObservationState state,T value,T rawValue){
  return new ObservationInput<>(state,value,rawValue);
 }
}