import React from 'react';
import {TextInput} from 'react-native';

export default function AppTextInput({
  testID,
  value,
  onChangeText,
  keyboardType,
  secureTextEntry,
}: {
  testID: string;
  value: string;
  onChangeText: (text: string) => void;
  keyboardType?: 'default' | 'email-address' | 'numeric' | 'url';
  secureTextEntry?: boolean;
}): React.JSX.Element {
  return (
    <TextInput
      testID={testID}
      value={value}
      onChangeText={onChangeText}
      keyboardType={keyboardType}
      secureTextEntry={secureTextEntry}
      autoCapitalize="none"
      autoCorrect={false}
    />
  );
}
