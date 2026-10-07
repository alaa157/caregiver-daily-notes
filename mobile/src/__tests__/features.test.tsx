import {fireEvent, render} from '@testing-library/react-native';
import {setLanguage} from '../i18n/i18n';
import {ApiClient} from '../lib/ApiClient';
import {FakeTransport} from '../lib/FakeTransport';
import AddendumScreen from '../features/notes/AddendumScreen';
import HistoryScreen from '../features/history/HistoryScreen';
import NoteDetailScreen from '../features/notes/NoteDetailScreen';
import NoteEditorScreen from '../features/notes/NoteEditorScreen';
import PlanDetailScreen, {PlansScreen} from '../features/plans/PlansScreen';
import {PlanAction} from '../types/api';
import RecipientForm from '../features/recipients/RecipientForm';
import RecipientsScreen from '../features/recipients/RecipientsScreen';
import SummaryCard from '../features/ai/SummaryCard';
import {listRecipients} from '../features/recipients/api';
import {createNote} from '../features/notes/api';
import {queryHistory} from '../features/history/api';
import {requestSummary} from '../features/ai/api';
import {listPlans, transitionPlan} from '../features/plans/api';
import {
  ARABIC_NOTE,
  EG_NOTE,
  ENGLISH_NOTE,
  PLAN,
  RECIPIENT,
  SUMMARY_WITH_FLAGS,
} from '../test-utils/fixtures';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

describe('features', () => {
  beforeEach(async () => {
    await setLanguage('en');
  });

  it('recipients list renders fixture content and form validates', async () => {
    const list = await render(<RecipientsScreen recipients={[RECIPIENT]} />);
    expect(list.getByText('Fatma Hassan')).toBeTruthy();

    const form = await render(<RecipientForm onSubmit={() => {}} />);
    await fireEvent.press(form.getByTestId('recipient-submit'));
    expect(await form.findByTestId('recipient-name-error')).toBeTruthy();
  });

  it('note editor rejects out-of-range pain with a localized error', async () => {
    const screen = await render(<NoteEditorScreen recipientId="r1" onSubmit={() => {}} />);

    await fireEvent.changeText(screen.getByTestId('note-pain'), '11');
    await fireEvent.press(screen.getByTestId('note-submit'));

    expect(await screen.findByTestId('note-pain-error')).toBeTruthy();
  });

  it('Arabic note text renders byte-identical', async () => {
    const screen = await render(
      <NoteDetailScreen note={ARABIC_NOTE} addenda={[]} />,
    );
    expect(screen.getByTestId('note-text').props.children).toBe(ARABIC_NOTE.text);
  });

  it('Egyptian dialect note renders verbatim', async () => {
    const screen = await render(
      <NoteDetailScreen note={EG_NOTE} addenda={[]} />,
    );
    expect(screen.getByTestId('note-text').props.children).toBe(EG_NOTE.text);
  });

  it('addendum screen shows the original read-only', async () => {
    const screen = await render(<AddendumScreen note={ENGLISH_NOTE} onSubmit={() => {}} />);

    expect(screen.getByTestId('addendum-original').props.children).toBe(ENGLISH_NOTE.text);
    expect(screen.queryByTestId('addendum-original-input')).toBeNull();
    expect(screen.getByTestId('addendum-text')).toBeTruthy();
  });

  it('history filters narrow the fixture list', async () => {
    const screen = await render(
      <HistoryScreen notes={[ENGLISH_NOTE, ARABIC_NOTE]} recipientIds={['r1', 'r2']} />,
    );
    expect(screen.getAllByTestId('history-note').length).toBe(2);

    await fireEvent.changeText(screen.getByTestId('history-recipient-filter'), 'r2');

    expect(screen.getAllByTestId('history-note').length).toBe(1);
  });

  it('safety banner is present with flags and has no dismiss control', async () => {
    const screen = await render(<SummaryCard summary={SUMMARY_WITH_FLAGS} />);

    expect(screen.getByTestId('safety-banner')).toBeTruthy();
    expect(screen.getByTestId('safety-banner').props.children).toBeTruthy();
    expect(screen.queryByTestId('safety-banner-dismiss')).toBeNull();
    expect(screen.queryByTestId('safety-banner-close')).toBeNull();
    expect(screen.getByTestId('evidence-quote-0')).toBeTruthy();
    expect(screen.getByText('سقطت في الحمام')).toBeTruthy();
  });

  it('plans bar shows all four actions and version history', async () => {
    const seen: string[] = [];
    const screen = await render(
      <PlanDetailScreen
        plan={PLAN}
        onAction={(action: PlanAction) => {
          seen.push(action);
        }}
      />,
    );

    for (const testID of [
      'plan-action-accept',
      'plan-action-edit-accept',
      'plan-action-dismiss',
      'plan-action-archive',
    ]) {
      expect(screen.getByTestId(testID)).toBeTruthy();
    }
    await fireEvent.press(screen.getByTestId('plan-action-accept'));
    expect(seen).toEqual(['accept']);
    expect(screen.getByTestId('plan-version-0')).toBeTruthy();
  });

  it('plans list renders fixture plans', async () => {
    const screen = await render(<PlansScreen plans={[PLAN]} />);
    expect(screen.getAllByTestId('plan-row').length).toBe(1);
  });

  it('feature api wrappers call the provisional endpoints', async () => {
    const transport = new FakeTransport({
      'GET /api/recipients': {status: 200, body: [RECIPIENT]},
      'POST /api/notes': {status: 200, body: ENGLISH_NOTE},
      'GET /api/notes?recipientId=r1': {status: 200, body: [ENGLISH_NOTE]},
      'POST /api/summaries': {status: 200, body: SUMMARY_WITH_FLAGS},
      'GET /api/plans': {status: 200, body: [PLAN]},
      'POST /api/plans/p1/accept': {status: 200, body: PLAN},
    });
    const client = new ApiClient(transport);

    expect(await listRecipients(client)).toEqual([RECIPIENT]);
    expect(await createNote(client, {recipientId: 'r1'})).toEqual(ENGLISH_NOTE);
    expect(await queryHistory(client, 'r1')).toEqual([ENGLISH_NOTE]);
    expect(await requestSummary(client, 'r1', 7)).toEqual(SUMMARY_WITH_FLAGS);
    expect(await listPlans(client)).toEqual([PLAN]);
    expect(await transitionPlan(client, 'p1', 'accept')).toEqual(PLAN);
  });
});
