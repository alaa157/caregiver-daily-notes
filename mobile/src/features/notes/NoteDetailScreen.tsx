import React from 'react';
import {Text, View} from 'react-native';
import {Addendum, Note} from '../../types/api';

export default function NoteDetailScreen({
  note,
  addenda,
}: {
  note: Note;
  addenda: Addendum[];
}): React.JSX.Element {
  return (
    <View testID="note-detail">
      <Text testID="note-text">{note.text}</Text>
      {addenda.map(a => (
        <View key={a.id} testID="note-addendum">
          <Text>{a.text}</Text>
        </View>
      ))}
    </View>
  );
}
