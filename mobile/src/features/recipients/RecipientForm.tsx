import React, {useState} from 'react';
import {Text, View} from 'react-native';
import AppButton from '../../components/AppButton';
import AppTextInput from '../../components/AppTextInput';
import {t} from '../../i18n/i18n';

export default function RecipientForm({
  onSubmit,
}: {
  onSubmit: (name: string) => void;
}): React.JSX.Element {
  const [name, setName] = useState('');
  const [error, setError] = useState(false);

  return (
    <View testID="recipient-form">
      <AppTextInput testID="recipient-name" value={name} onChangeText={setName} />
      {error ? <Text testID="recipient-name-error">{t('recipients.nameRequired')}</Text> : null}
      <AppButton
        testID="recipient-submit"
        title={t('common.save')}
        onPress={() => {
          if (name.trim().length === 0) {
            setError(true);
            return;
          }
          setError(false);
          onSubmit(name.trim());
        }}
      />
    </View>
  );
}
