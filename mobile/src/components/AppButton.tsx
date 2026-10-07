import React from 'react';
import {Button, View} from 'react-native';

export default function AppButton({
  testID,
  title,
  onPress,
}: {
  testID: string;
  title: string;
  onPress: () => void;
}): React.JSX.Element {
  return (
    <View>
      <Button testID={testID} title={title} onPress={onPress} />
    </View>
  );
}
