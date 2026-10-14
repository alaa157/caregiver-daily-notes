package com.caregiver.notes;
import com.caregiver.auth.Caregiver;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="note_addenda",indexes=@Index(name="idx_note_addenda_note_created",columnList="note_id,created_at"),uniqueConstraints=@UniqueConstraint(name="uk_note_addenda_owner_idempotency",columnNames={"caregiver_id","idempotency_key"}))
public class NoteAddendum {
 @Id @UuidGenerator @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="note_id",nullable=false,updatable=false) private DailyNote note;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="caregiver_id",nullable=false,updatable=false) private Caregiver caregiver;
 @Column(length=40,updatable=false) private String mood; @Column(length=40,updatable=false) private String appetite; @Column(length=40,updatable=false) private String sleep; @Column(length=40,updatable=false) private String mobility;
 @Column(name="medication_status",length=20,updatable=false) private String medicationStatus; @Column(name="medication_verified",updatable=false) private Boolean medicationVerified; @Column(name="pain_score",updatable=false) private Integer painScore; @Column(name="fall_reported",updatable=false) private Boolean fallReported; @Column(name="free_text",columnDefinition="TEXT",updatable=false) private String freeText; @Column(name="idempotency_key",length=100,updatable=false) private String idempotencyKey;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected NoteAddendum(){}
 public NoteAddendum(DailyNote n,Caregiver c,String m,String a,String s,String mob,String ms,Boolean mv,Integer p,Boolean f,String t,String k){note=n;caregiver=c;mood=m;appetite=a;sleep=s;mobility=mob;medicationStatus=ms;medicationVerified=mv;painScore=p;fallReported=f;freeText=t;idempotencyKey=k;}
 public UUID getId(){return id;} public DailyNote getNote(){return note;} public Caregiver getCaregiver(){return caregiver;} public String getMood(){return mood;} public String getAppetite(){return appetite;} public String getSleep(){return sleep;} public String getMobility(){return mobility;} public String getMedicationStatus(){return medicationStatus;} public Boolean getMedicationVerified(){return medicationVerified;} public Integer getPainScore(){return painScore;} public Boolean getFallReported(){return fallReported;} public String getFreeText(){return freeText;} public String getIdempotencyKey(){return idempotencyKey;} public Instant getCreatedAt(){return createdAt;}
}