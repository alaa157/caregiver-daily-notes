import React from 'react';
import {Text, View} from 'react-native';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import {ApiError} from '../../lib/errors';
import {Addendum, Note} from '../../types/api';

export interface NoteDetailProps {
  note: Note;
  addenda: Addendum[];
  state?: ApiViewState;
  error?: ApiError | null;
  onRetry?: () => void;
}

export default function NoteDetailScreen({
  note,
  addenda,
  state = 'content',
  error = null,
  onRetry,
}: NoteDetailProps): React.JSX.Element {
  const content = (
    <View testID="note-detail">
      <Text testID="note-text">{note.text}</Text>
      {addenda.map(a => (
        <View key={a.id} testID="note-addendum">
          <Text>{a.text}</Text>
        </View>
      ))}
    </View>
  );
  if (state === 'error') {
    return (
      <ApiStateView state="error" error={error} onRetry={onRetry}>
        {content}
      </ApiStateView>
    );
  }
  return (
    <ApiStateView state={state} onRetry={onRetry}>
      {content}
    </ApiStateView>
  );
}
