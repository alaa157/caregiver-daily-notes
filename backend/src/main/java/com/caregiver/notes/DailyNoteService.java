package com.caregiver.notes;
import com.caregiver.auth.AuthContext; import com.caregiver.auth.CaregiverRepository; import com.caregiver.recipient.CareRecipientRepository; import com.caregiver.recipient.RecipientNotFoundException; import org.springframework.data.domain.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.LocalDate; import java.util.UUID;
@Service public class DailyNoteService{
 private final DailyNoteRepository notes; private final NoteAddendumRepository addenda; private final CareRecipientRepository recipients; private final CaregiverRepository caregivers;
 public DailyNoteService(DailyNoteRepository n,NoteAddendumRepository a,CareRecipientRepository r,CaregiverRepository c){notes=n;addenda=a;recipients=r;caregivers=c;}
 @Transactional public NoteResponse create(NoteRequest r,String key){UUID owner=AuthContext.currentCaregiverId();if(key!=null&&!key.isBlank()){var e=notes.findByCaregiver_IdAndIdempotencyKey(owner,key);if(e.isPresent()){if(!sameRequest(e.get(),r))throw new IllegalArgumentException("Idempotency-Key was already used with a different request.");return response(e.get());}}
 var recipient=recipients.findByIdAndCaregiver_Id(r.recipientId(),owner).orElseThrow(RecipientNotFoundException::new);if(!recipient.isActive())throw new IllegalArgumentException("Recipient is inactive.");
 var caregiver=caregivers.findById(owner).orElseThrow(RecipientNotFoundException::new);return response(notes.saveAndFlush(new DailyNote(caregiver,recipient,r.date(),r.mood(),r.appetite(),r.sleep(),r.mobility(),r.medicationStatus(),r.medicationVerified(),r.painScore(),r.fallReported(),r.freeText(),blank(key))));}
 @Transactional(readOnly=true) public NoteResponse get(UUID id){UUID o=AuthContext.currentCaregiverId();return notes.findByIdAndCaregiver_Id(id,o).map(this::response).orElseThrow(NoteNotFoundException::new);}
 @Transactional(readOnly=true) public NoteResponse getByRecipientDate(UUID recipient,LocalDate date){UUID o=AuthContext.currentCaregiverId();return notes.findByRecipient_IdAndCaregiver_IdAndDate(recipient,o,date).map(this::response).orElseThrow(NoteNotFoundException::new);}
 @Transactional(readOnly=true) public Page<NoteResponse> history(UUID recipient,LocalDate from,LocalDate to,int page,int size){UUID o=AuthContext.currentCaregiverId();if(from.isAfter(to))throw new IllegalArgumentException("from must be on or before to.");if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be non-negative and size must be 1-100.");var p=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"date").and(Sort.by(Sort.Direction.DESC,"id")));Page<DailyNote> rows=recipient==null?notes.findAllByCaregiver_IdAndDateBetween(o,from,to,p):notes.findAllByCaregiver_IdAndRecipient_IdAndDateBetween(o,recipient,from,to,p);return rows.map(this::response);}
 @Transactional public NoteAddendumResponse append(UUID id,AddendumRequest r,String key){UUID o=AuthContext.currentCaregiverId();if(key!=null&&!key.isBlank()){var e=addenda.findByCaregiver_IdAndIdempotencyKey(o,key);if(e.isPresent()){NoteAddendum existing=e.get();if(!existing.getNote().getId().equals(id)||!sameRequest(existing,r))throw new IllegalArgumentException("Idempotency-Key was already used for a different addendum request.");return NoteAddendumResponse.from(existing);}}var n=notes.findByIdAndCaregiver_Id(id,o).orElseThrow(NoteNotFoundException::new);var c=caregivers.findById(o).orElseThrow(NoteNotFoundException::new);return NoteAddendumResponse.from(addenda.saveAndFlush(new NoteAddendum(n,c,r.mood(),r.appetite(),r.sleep(),r.mobility(),r.medicationStatus(),r.medicationVerified(),r.painScore(),r.fallReported(),r.freeText(),blank(key))));}
 @Transactional(readOnly=true) public java.util.List<NoteAddendumResponse> addenda(UUID id){UUID o=AuthContext.currentCaregiverId();if(!notes.existsByIdAndCaregiver_Id(id,o))throw new NoteNotFoundException();return addenda.findAllByNote_IdAndCaregiver_IdOrderByCreatedAtAsc(id,o).stream().map(NoteAddendumResponse::from).toList();}
 private boolean sameRequest(DailyNote n, NoteRequest r) {
  return Objects.equals(n.getRecipient().getId(), r.recipientId())
      && Objects.equals(n.getDate(), r.date())
      && Objects.equals(n.getMood(), r.mood())
      && Objects.equals(n.getAppetite(), r.appetite())
      && Objects.equals(n.getSleep(), r.sleep())
      && Objects.equals(n.getMobility(), r.mobility())
      && Objects.equals(n.getMedicationStatus(), r.medicationStatus())
      && Objects.equals(n.getMedicationVerified(), r.medicationVerified())
      && Objects.equals(n.getPainScore(), r.painScore())
      && Objects.equals(n.getFallReported(), r.fallReported())
      && Objects.equals(n.getFreeText(), r.freeText());
 }
 private boolean sameRequest(NoteAddendum a, AddendumRequest r) {
  return Objects.equals(a.getMood(), r.mood())
      && Objects.equals(a.getAppetite(), r.appetite())
      && Objects.equals(a.getSleep(), r.sleep())
      && Objects.equals(a.getMobility(), r.mobility())
      && Objects.equals(a.getMedicationStatus(), r.medicationStatus())
      && Objects.equals(a.getMedicationVerified(), r.medicationVerified())
      && Objects.equals(a.getPainScore(), r.painScore())
      && Objects.equals(a.getFallReported(), r.fallReported())
      && Objects.equals(a.getFreeText(), r.freeText());
 }
 private NoteResponse response(DailyNote n){return NoteResponse.from(n,addenda.countByNote_IdAndCaregiver_Id(n.getId(),n.getCaregiver().getId()));} private String blank(String s){return s==null||s.isBlank()?null:s;}
}