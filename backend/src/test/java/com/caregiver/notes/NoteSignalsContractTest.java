package com.caregiver.notes;
import com.caregiver.common.NoteSignals; import org.junit.jupiter.api.Test; import java.time.LocalDate; import static org.assertj.core.api.Assertions.*;
class NoteSignalsContractTest{
 @Test void preservesMixedLanguageTextAndAbsentPain(){String s="المريض قال: I feel pain — لا تغيّر النص.";var n=new NoteSignals("r1",LocalDate.of(2026,10,1),false,null,null,true,null,s);assertThat(n.painScore()).isNull();assertThat(n.freeText()).isEqualTo(s);assertThat(n.medicationUnverified()).isTrue();}
 @Test void rejectsPainOutsideRange(){assertThatThrownBy(()->new NoteSignals("r1",LocalDate.now(),false,11,null,false,null,"")).isInstanceOf(IllegalArgumentException.class);}
}