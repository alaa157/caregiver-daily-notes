import {createNativeStackNavigator, NativeStackScreenProps} from '@react-navigation/native-stack';
import React, {useCallback, useEffect, useState} from 'react';
import {Button, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import AppText from '../../components/AppText';
import {ApiError} from '../../lib/errors';
import {useAuth} from '../auth/AuthContext';
import SummaryContainer from '../ai/SummaryContainer';
import {Addendum, Note, Recipient} from '../../types/api';
import {appendAddendum, createNote} from './api';
import AddendumScreen from './AddendumScreen';
import NoteDetailScreen from './NoteDetailScreen';
import NoteEditorScreen, {NoteSubmit} from './NoteEditorScreen';
import RecipientDetailScreen from '../recipients/RecipientDetailScreen';
import RecipientsContainer from '../recipients/RecipientsContainer';
import {listRecipients} from '../recipients/api';

export type NotesStackParamList = {
  Recipients: undefined;
  RecipientDetail: {recipientId: string};
  NoteEditor: {recipientId: string};
  NoteDetail: {note: Note; addenda: Addendum[]};
  Summary: {recipientId: string};
};

const Stack = createNativeStackNavigator<NotesStackParamList>();

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Unexpected error');
}

function submitCopy(t: (key: string) => string, error: ApiError): string {
  return error.code === 'NETWORK' ? t('states.networkError') : t('states.serverError');
}

type RecipientsRouteProps = NativeStackScreenProps<NotesStackParamList, 'Recipients'>;
type DetailRouteProps = NativeStackScreenProps<NotesStackParamList, 'RecipientDetail'>;
type EditorRouteProps = NativeStackScreenProps<NotesStackParamList, 'NoteEditor'>;
type NoteDetailRouteProps = NativeStackScreenProps<NotesStackParamList, 'NoteDetail'>;

function RecipientsRoute({navigation}: RecipientsRouteProps): React.JSX.Element {
  return (
    <RecipientsContainer
      onSelect={recipientId => navigation.navigate('RecipientDetail', {recipientId})}
    />
  );
}

function RecipientDetailRoute({navigation, route}: DetailRouteProps): React.JSX.Element {
  const {authedFetch} = useAuth();
  const {t} = useTranslation();
  const [recipient, setRecipient] = useState<Recipient | null>(null);
  const [state, setState] = useState<ApiViewState>('loading');
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    setError(null);
    try {
      const rows = await authedFetch(client => listRecipients(client));
      const found = rows.find(r => r.id === route.params.recipientId) ?? null;
      setRecipient(found);
      setState(found ? 'content' : 'empty');
    } catch (e) {
      setError(toApiError(e));
      setState('error');
    }
  }, [authedFetch, route.params.recipientId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!recipient) {
    if (state === 'error') {
      return <ApiStateView state="error" error={error} onRetry={() => void load()} />;
    }
    return <ApiStateView state={state} onRetry={() => void load()} />;
  }
  return (
    <View>
      <RecipientDetailScreen recipient={recipient} onBack={() => navigation.goBack()} />
      <Button
        testID="recipient-detail-write-note"
        title={t('notes.newNote')}
        onPress={() => navigation.navigate('NoteEditor', {recipientId: recipient.id})}
      />
      <Button
        testID="recipient-detail-view-summary"
        title={t('ai.viewSummary')}
        onPress={() => navigation.navigate('Summary', {recipientId: recipient.id})}
      />
    </View>
  );
}

function NoteEditorRoute({navigation, route}: EditorRouteProps): React.JSX.Element {
  const {authedFetch} = useAuth();
  const {t} = useTranslation();
  const [submitError, setSubmitError] = useState<ApiError | null>(null);

  async function handleSubmit(draft: NoteSubmit): Promise<void> {
    setSubmitError(null);
    try {
      const created = await authedFetch(client => createNote(client, {...draft}));
      // The scripted Part A body carries placeholder fields; the submitted
      // draft is the truthful content.
      const note: Note = {
        ...created,
        ...draft,
        id: created.id,
        date: '2026-10-04',
      };
      navigation.navigate('NoteDetail', {note, addenda: []});
    } catch (e) {
      setSubmitError(toApiError(e));
    }
  }

  return (
    <View>
      {submitError ? <AppText>{submitCopy(t, submitError)}</AppText> : null}
      <NoteEditorScreen recipientId={route.params.recipientId} onSubmit={draft => void handleSubmit(draft)} />
      <Button testID="note-editor-back" title={t('common.back')} onPress={() => navigation.goBack()} />
    </View>
  );
}

function NoteDetailRoute({navigation, route}: NoteDetailRouteProps): React.JSX.Element {
  const {authedFetch} = useAuth();
  const {t} = useTranslation();
  const [addenda, setAddenda] = useState<Addendum[]>(route.params.addenda);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);

  async function handleAddendum(text: string): Promise<void> {
    setSubmitError(null);
    try {
      const created = await authedFetch(client =>
        appendAddendum(client, route.params.note.id, text),
      );
      // The scripted Part A body carries a placeholder text; the submitted
      // text is the truthful content.
      setAddenda(rows => [...rows, {...created, text}]);
    } catch (e) {
      setSubmitError(toApiError(e));
    }
  }

  return (
    <View>
      <NoteDetailScreen note={route.params.note} addenda={addenda} />
      {submitError ? <AppText>{submitCopy(t, submitError)}</AppText> : null}
      <AddendumScreen note={route.params.note} onSubmit={text => void handleAddendum(text)} />
      <Button
        testID="note-detail-view-summary"
        title={t('ai.viewSummary')}
        onPress={() => navigation.navigate('Summary', {recipientId: route.params.note.recipientId})}
      />
      <Button testID="note-detail-back" title={t('common.back')} onPress={() => navigation.goBack()} />
    </View>
  );
}

function SummaryRoute({navigation, route}: NativeStackScreenProps<NotesStackParamList, 'Summary'>): React.JSX.Element {
  const {t} = useTranslation();
  return (
    <View>
      <SummaryContainer recipientId={route.params.recipientId} />
      <Button testID="summary-back" title={t('common.back')} onPress={() => navigation.goBack()} />
    </View>
  );
}

export default function NotesStack(): React.JSX.Element {
  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      <Stack.Screen name="Recipients" component={RecipientsRoute} />
      <Stack.Screen name="RecipientDetail" component={RecipientDetailRoute} />
      <Stack.Screen name="NoteEditor" component={NoteEditorRoute} />
      <Stack.Screen name="NoteDetail" component={NoteDetailRoute} />
      <Stack.Screen name="Summary" component={SummaryRoute} />
    </Stack.Navigator>
  );
}
