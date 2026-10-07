import React, {useState} from 'react';
import {Text, View} from 'react-native';
import AppButton from '../../components/AppButton';
import AppTextInput from '../../components/AppTextInput';
import {t} from '../../i18n/i18n';
import {Note} from '../../types/api';

export default function AddendumScreen({
  note,
  onSubmit,
}: {
  note: Note;
  onSubmit: (text: string) => void;
}): React.JSX.Element {
  const [text, setText] = useState('');

  return (
    <View testID="addendum-screen">
      <Text testID="addendum-original">{note.text}</Text>
      <AppTextInput testID="addendum-text" value={text} onChangeText={setText} />
      <AppButton
        testID="addendum-submit"
        title={t('common.save')}
        onPress={() => onSubmit(text)}
      />
    </View>
  );
}
