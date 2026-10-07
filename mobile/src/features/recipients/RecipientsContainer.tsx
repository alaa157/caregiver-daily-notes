import React, {useCallback, useEffect, useState} from 'react';
import {Button, View} from 'react-native';
import {useTranslation} from 'react-i18next';
import {ApiViewState} from '../../components/ApiStateView';
import AppText from '../../components/AppText';
import {ApiError} from '../../lib/errors';
import {useAuth} from '../auth/AuthContext';
import {Recipient} from '../../types/api';
import {createRecipient, listRecipients} from './api';
import RecipientForm from './RecipientForm';
import RecipientsScreen from './RecipientsScreen';

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Unexpected error');
}

// Part A container: drives the presentational list/form through the injected
// client (FakeTransport-backed in Part A, FetchTransport in Part B).
export default function RecipientsContainer({
  onSelect,
}: {
  onSelect: (recipientId: string) => void;
}): React.JSX.Element {
  const {authedFetch} = useAuth();
  const {t} = useTranslation();
  const [recipients, setRecipients] = useState<Recipient[]>([]);
  const [state, setState] = useState<ApiViewState>('loading');
  const [error, setError] = useState<ApiError | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    setError(null);
    try {
      const rows = await authedFetch(client => listRecipients(client));
      setRecipients(rows);
      setState(rows.length === 0 ? 'empty' : 'content');
    } catch (e) {
      setError(toApiError(e));
      setState('error');
    }
  }, [authedFetch]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleCreate(name: string): Promise<void> {
    setSubmitError(null);
    try {
      const created = await authedFetch(client => createRecipient(client, name));
      // The scripted Part A body carries a placeholder name; the submitted
      // name is the truthful one.
      setRecipients(rows => [...rows, {...created, name}]);
      setShowForm(false);
      if (state === 'empty') {
        setState('content');
      }
    } catch (e) {
      setSubmitError(toApiError(e));
    }
  }

  return (
    <View testID="recipients-container">
      <Button
        testID="recipients-add-toggle"
        title={t('recipients.add')}
        onPress={() => {
          setSubmitError(null);
          setShowForm(v => !v);
        }}
      />
      {showForm ? <RecipientForm onSubmit={name => void handleCreate(name)} /> : null}
      {submitError ? <AppText>{submitError.message}</AppText> : null}
      <RecipientsScreen recipients={recipients} state={state} error={error} onRetry={() => void load()} onSelect={r => onSelect(r.id)} />
    </View>
  );
}
