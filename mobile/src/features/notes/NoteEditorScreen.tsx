import React, {useState} from 'react';
import {Text, View} from 'react-native';
import AppButton from '../../components/AppButton';
import AppTextInput from '../../components/AppTextInput';
import {t} from '../../i18n/i18n';
import {Note} from '../../types/api';

export interface NoteDraft {
  mood: string;
  text: string;
  pain: number;
}

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
  onSubmit: (draft: NoteDraft & {recipientId: string}) => void;
}): React.JSX.Element {
  const [mood, setMood] = useState('');
  const [text, setText] = useState('');
  const [painRaw, setPainRaw] = useState('');
  const [painError, setPainError] = useState(false);
  const [moodError, setMoodError] = useState(false);

  return (
    <View testID="note-editor">
      <AppTextInput testID="note-mood" value={mood} onChangeText={setMood} />
      {moodError ? <Text testID="note-mood-error">{t('notes.moodRequired')}</Text> : null}
      <AppTextInput testID="note-text" value={text} onChangeText={setText} />
      <AppTextInput
        testID="note-pain"
        value={painRaw}
        onChangeText={setPainRaw}
        keyboardType="numeric"
      />
      {painError ? <Text testID="note-pain-error">{t('notes.painInvalid')}</Text> : null}
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
          onSubmit({recipientId, mood: mood.trim(), text, pain});
        }}
      />
    </View>
  );
}

export function noteToDraft(note: Note): NoteDraft {
  return {mood: note.mood, text: note.text, pain: note.pain};
}
