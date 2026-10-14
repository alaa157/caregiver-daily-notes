package com.caregiver.notes;
import com.caregiver.auth.Caregiver;
import com.caregiver.recipient.CareRecipient;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity
@Table(name="daily_notes",uniqueConstraints={@UniqueConstraint(name="uk_daily_note_recipient_date",columnNames={"recipient_id","note_date"}),@UniqueConstraint(name="uk_daily_note_owner_idempotency",columnNames={"caregiver_id","idempotency_key"})},indexes={@Index(name="idx_daily_notes_owner_date",columnList="caregiver_id,note_date"),@Index(name="idx_daily_notes_recipient_date",columnList="recipient_id,note_date")})
public class DailyNote {
 @Id @UuidGenerator @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="caregiver_id",nullable=false,updatable=false) private Caregiver caregiver;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id",nullable=false,updatable=false) private CareRecipient recipient;
 @Column(name="note_date",nullable=false,updatable=false) private LocalDate date;
 @Column(length=40,updatable=false) private String mood;
 @Column(length=40,updatable=false) private String appetite;
 @Column(length=40,updatable=false) private String sleep;
 @Column(length=40,updatable=false) private String mobility;
 @Column(name="medication_status",length=20,updatable=false) private String medicationStatus;
 @Column(name="medication_verified",updatable=false) private Boolean medicationVerified;
 @Column(name="pain_score",updatable=false) private Integer painScore;
 @Column(name="fall_reported",updatable=false) private Boolean fallReported;
 @Column(name="free_text",columnDefinition="TEXT",updatable=false) private String freeText;
 @Column(name="idempotency_key",length=100,updatable=false) private String idempotencyKey;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected DailyNote(){}
 public DailyNote(Caregiver c,CareRecipient r,LocalDate d,String m,String a,String s,String mob,String ms,Boolean mv,Integer p,Boolean f,String text,String key){caregiver=c;recipient=r;date=d;mood=m;appetite=a;sleep=s;mobility=mob;medicationStatus=ms;medicationVerified=mv;painScore=p;fallReported=f;freeText=text;idempotencyKey=key;}
 public UUID getId(){return id;} public Caregiver getCaregiver(){return caregiver;} public CareRecipient getRecipient(){return recipient;} public LocalDate getDate(){return date;} public String getMood(){return mood;} public String getAppetite(){return appetite;} public String getSleep(){return sleep;} public String getMobility(){return mobility;} public String getMedicationStatus(){return medicationStatus;} public Boolean getMedicationVerified(){return medicationVerified;} public Integer getPainScore(){return painScore;} public Boolean getFallReported(){return fallReported;} public String getFreeText(){return freeText;} public String getIdempotencyKey(){return idempotencyKey;} public Instant getCreatedAt(){return createdAt;}
}