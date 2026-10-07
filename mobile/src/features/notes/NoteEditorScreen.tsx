import React, {useState} from 'react';
import {Switch, Text, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import AppButton from '../../components/AppButton';
import AppTextInput from '../../components/AppTextInput';
import {Note} from '../../types/api';

export interface NoteDraft {
  mood: string;
  appetite: string;
  sleep: string;
  mobility: string;
  medicationTaken: string;
  pain: number;
  fall: boolean;
  text: string;
}

export type NoteSubmit = NoteDraft & {recipientId: string};

function parsePain(raw: string): number | null {
  if (!/^-?\d+$/.test(raw.trim())) {
    return null;
  }
  const value = Number(raw.trim());
  return Number.isInteger(value) && value >= 0 && value <= 10 ? value : null;
}

export default function NoteEditorScreen({
  recipientId,
  onSubmit,
}: {
  recipientId: string;
  onSubmit: (draft: NoteSubmit) => void;
}): React.JSX.Element {
  const {t} = useTranslation();
  const [mood, setMood] = useState('');
  const [appetite, setAppetite] = useState('');
  const [sleep, setSleep] = useState('');
  const [mobility, setMobility] = useState('');
  const [medicationTaken, setMedicationTaken] = useState('');
  const [text, setText] = useState('');
  const [painRaw, setPainRaw] = useState('');
  const [fall, setFall] = useState(false);
  const [painError, setPainError] = useState(false);
  const [moodError, setMoodError] = useState(false);

  return (
    <View testID="note-editor">
      <Text>{t('notes.mood')}</Text>
      <AppTextInput testID="note-mood" value={mood} onChangeText={setMood} />
      {moodError ? <Text testID="note-mood-error">{t('notes.moodRequired')}</Text> : null}
      <Text>{t('notes.appetite')}</Text>
      <AppTextInput testID="note-appetite" value={appetite} onChangeText={setAppetite} />
      <Text>{t('notes.sleep')}</Text>
      <AppTextInput testID="note-sleep" value={sleep} onChangeText={setSleep} />
      <Text>{t('notes.mobility')}</Text>
      <AppTextInput testID="note-mobility" value={mobility} onChangeText={setMobility} />
      <Text>{t('notes.medication')}</Text>
      <AppTextInput
        testID="note-medication"
        value={medicationTaken}
        onChangeText={setMedicationTaken}
      />
      <Text>{t('notes.text')}</Text>
      <AppTextInput testID="note-text" value={text} onChangeText={setText} />
      <Text>{t('notes.pain')}</Text>
      <AppTextInput
        testID="note-pain"
        value={painRaw}
        onChangeText={setPainRaw}
        keyboardType="numeric"
      />
      {painError ? <Text testID="note-pain-error">{t('notes.painInvalid')}</Text> : null}
      <Text>{t('notes.fall')}</Text>
      <Switch
        testID="note-fall"
        value={fall}
        onValueChange={setFall}
        accessibilityLabel={t('notes.fall')}
        accessibilityRole="switch"
      />
      <AppButton
        testID="note-submit"
        title={t('common.save')}
        onPress={() => {
          const pain = parsePain(painRaw);
          setPainError(pain === null);
          setMoodError(mood.trim().length === 0);
          if (pain === null || mood.trim().length === 0) {
            return;
          }
          onSubmit({
            recipientId,
            mood: mood.trim(),
            appetite: appetite.trim(),
            sleep: sleep.trim(),
            mobility: mobility.trim(),
            medicationTaken: medicationTaken.trim(),
            pain,
            fall,
            text,
          });
        }}
      />
    </View>
  );
}

export function noteToDraft(note: Note): NoteDraft {
  return {
    mood: note.mood,
    appetite: note.appetite,
    sleep: note.sleep,
    mobility: note.mobility,
    medicationTaken: note.medicationTaken,
    pain: note.pain,
    fall: note.fall,
    text: note.text,
  };
}
