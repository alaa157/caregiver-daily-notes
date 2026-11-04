package com.caregiver.notes;

import com.caregiver.common.NoteSignals;
import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag; import jakarta.validation.Valid; import org.springframework.data.domain.Page; import org.springframework.format.annotation.DateTimeFormat; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import java.time.LocalDate; import java.util.List; import java.util.UUID;
@RestController @RequestMapping("/api/notes") @Tag(name="Daily notes",description="Caregiver-owned daily notes, append-only addenda, history and structured signals.")
public class NoteController{
 private final DailyNoteService service; private final NoteSignalService signals; private final NoteCorrectionService corrections; public NoteController(DailyNoteService s,NoteSignalService n,NoteCorrectionService c){service=s;signals=n;corrections=c;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Create a note; optional Idempotency-Key makes retries safe") public NoteResponse create(@Valid @RequestBody NoteRequest r,@RequestHeader(value="Idempotency-Key",required=false) String key){return service.create(r,key);}
 @GetMapping("/{id}") public NoteDetailResponse get(@PathVariable UUID id){return service.getDetail(id);}
 @GetMapping("/by-recipient/{recipientId}") public NoteDetailResponse byDate(@PathVariable UUID recipientId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return service.getByRecipientDateDetail(recipientId,date);}
 @GetMapping public Page<NoteResponse> history(@RequestParam(required=false) UUID recipientId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.history(recipientId,from,to,page,size);}
 @PostMapping("/{id}/addenda") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Append a correction without modifying the original note") public NoteAddendumResponse append(@PathVariable UUID id,@Valid @RequestBody AddendumRequest r,@RequestHeader(value="Idempotency-Key",required=false) String key){return service.append(id,r,key);}
 @GetMapping("/{id}/addenda") public List<NoteAddendumResponse> addenda(@PathVariable UUID id){return service.addenda(id);}
 @PostMapping("/{id}/corrections") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="Append typed observation corrections without modifying the original note") public CorrectionHistoryResponse correct(@PathVariable UUID id,@Valid @RequestBody CorrectionRequest r,@RequestHeader(value="Idempotency-Key",required=false) String key){return corrections.append(id,r,key);}
 @GetMapping("/{id}/corrections") @Operation(summary="Retrieve original note, typed correction events and replayed effective observations") public CorrectionHistoryResponse corrections(@PathVariable UUID id){return corrections.history(id);}
 @GetMapping("/signals") public List<NoteSignals> signals(@RequestParam UUID recipientId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return signals.forRange(recipientId,from,to);}
}
